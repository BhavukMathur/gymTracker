from __future__ import annotations

import calendar
import json
import logging
from typing import Any

import httpx
from langchain.agents import create_agent
from langchain_core.messages import AIMessage, HumanMessage
from langchain_core.tools import tool

from app.config import Settings
from app.llm_factory import LlmConfigurationError, create_chat_model

logger = logging.getLogger(__name__)

COACH_SYSTEM = """You are a supportive coach for the Gym Tracker app.
The user's monthly attendance goal is {monthly_goal} days marked present.
You have read-only tools: (1) attendance for a **calendar month**, (2) attendance in a **rolling day window** up to today, and (3) a **random general health tip** from a static list (not computed from the user's data). You cannot log or change days.

## Which attendance tool to call (pick one per question unless the user clearly asks to compare two windows)
- A **named calendar month** (e.g. "April 2025", "this month" with calendar open): use **fetch_month_attendance** with year and month (1–12; April = 4).
- **Recent** activity without a named month (e.g. "lately", "past week", "last 30 days" in a rolling sense): use **fetch_rolling_attendance** with **days** (e.g. 7, 14, 30). The window is the last N **calendar** days through **today** (server time), not the calendar month.
- Do not call both month and rolling in one answer unless the user explicitly asks to compare a month to a recent window.

## Stitched flow (data → optional tip)
1. If the user needs their **own numbers** for the question, call the **right attendance tool first** and answer from that JSON (counts, entries, empty = no logs in range — that is normal).
2. If it fits the same reply (e.g. they want encouragement, ideas, or a rounded-out answer), you **may** call **fetch_health_tip** **after** the attendance result to add a **short** add-on. Say clearly the tip is a **general** wellness habit, not derived from their calendar.
3. If they only want a **quick idea** or a tip with no attendance context, you may use **only fetch_health_tip** (one call).
4. If attendance returns **"error": "unauthorized"**, say to log in again; do not imply tips fix auth. You may still offer fetch_health_tip only if a generic tip is appropriate after explaining the data issue.
5. If a tool returns **"error": "http_error"**, say the app backend may be unreachable (BACKEND_URL / coach); only call other tools if they are likely to succeed (e.g. health tips use the same server).

**fetch_health_tip** returns random static text. Never claim it is personalized, predicted from their streak, or based on their logs.

## Tool payloads
- **Month/rolling** responses include "entries" when relevant (date → true present / false missed), plus count fields. Rolling also has startDate, endDate, days, presentDays/markedDays (or present_days in augmented form).
- Empty entries for a range means nothing logged in that range — not a system failure.
- For **fetch_health_tip**, a normal response includes a "tip" string.

Be concise, practical, and encouraging."""

CALENDAR_HINT = """The user currently has the app calendar open on {month_name} {year}.
Use fetch_month_attendance(year={year}, month={month}) when they mean "this month" or "{month_name}" without naming another year."""


def _content_to_str(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and "text" in block:
                parts.append(str(block["text"]))
        return "".join(parts)
    return str(content)


def _final_ai_text(result: dict[str, Any]) -> str:
    msgs = result.get("messages") or []
    for m in reversed(msgs):
        if getattr(m, "type", None) == "ai":
            return _content_to_str(m.content).strip()
    return ""


def _build_tools(jwt_token: str, backend_url: str):
    base = backend_url.rstrip("/")

    @tool
    def fetch_month_attendance(year: int, month: int) -> str:
        """Load this user's gym attendance for one calendar month (read-only).

        Args:
            year: Four-digit year (e.g. 2026).
            month: Month number from 1 to 12.

        Returns:
            JSON mapping each date string (YYYY-MM-DD) to true if present, false if marked missed/rest.
            Dates with no entry may be omitted.
        """
        try:
            r = httpx.get(
                f"{base}/api/attendance/month",
                params={"year": year, "month": month},
                headers={"Authorization": f"Bearer {jwt_token}"},
                timeout=20.0,
            )
            if r.status_code == 401:
                logger.info(
                    "fetch_month_attendance unauthorized year=%s month=%s backend=%s",
                    year,
                    month,
                    base,
                )
                return json.dumps({"error": "unauthorized", "detail": "JWT rejected by API"})
            if r.status_code != 200:
                logger.warning(
                    "fetch_month_attendance bad status year=%s month=%s status=%s",
                    year,
                    month,
                    r.status_code,
                )
            r.raise_for_status()
            raw = r.json()
            if not isinstance(raw, dict):
                raw = {}
            present_days = sum(1 for v in raw.values() if v is True)
            marked_days = len(raw)
            payload = {
                "year": year,
                "month": month,
                "entries": raw,
                "present_days": present_days,
                "marked_days": marked_days,
            }
            logger.info(
                "fetch_month_attendance ok year=%s month=%s present_days=%s marked_days=%s",
                year,
                month,
                present_days,
                marked_days,
            )
            return json.dumps(payload)
        except httpx.HTTPError as e:
            logger.warning("fetch_month_attendance HTTP error: %s", e)
            return json.dumps({"error": "http_error", "detail": str(e)})

    @tool
    def fetch_rolling_attendance(days: int) -> str:
        """Load this user's gym attendance for the last N calendar days through today (read-only).

        Use for questions about recent habits, the past week, or a rolling "last 30 days" (not a named
        month). Prefer fetch_month_attendance for a specific calendar year/month.

        Args:
            days: Window length, 1–366 (e.g. 7 for last week, 30 for last 30 days).

        Returns:
            JSON with startDate, endDate, days, presentDays, markedDays, and entries (date string →
            true if present, false if missed). Omitted dates have no log.
        """
        d = int(days) if days is not None else 30
        d = max(1, min(366, d))
        try:
            r = httpx.get(
                f"{base}/api/attendance/rolling",
                params={"days": d},
                headers={"Authorization": f"Bearer {jwt_token}"},
                timeout=20.0,
            )
            if r.status_code == 401:
                logger.info("fetch_rolling_attendance unauthorized days=%s backend=%s", d, base)
                return json.dumps({"error": "unauthorized", "detail": "JWT rejected by API"})
            if r.status_code != 200:
                logger.warning("fetch_rolling_attendance bad status days=%s status=%s", d, r.status_code)
            r.raise_for_status()
            raw = r.json()
            if not isinstance(raw, dict):
                raw = {}
            entries = raw.get("entries")
            if not isinstance(entries, dict):
                entries = {}
            present_days = sum(1 for v in entries.values() if v is True)
            marked_days = len(entries)
            try:
                present_days = int(raw.get("presentDays", present_days))
                marked_days = int(raw.get("markedDays", marked_days))
            except (TypeError, ValueError):
                pass
            payload = {
                **raw,
                "entries": entries,
                "present_days": present_days,
                "marked_days": marked_days,
            }
            logger.info(
                "fetch_rolling_attendance ok days=%s present_days=%s marked_days=%s",
                raw.get("days", d),
                present_days,
                marked_days,
            )
            return json.dumps(payload)
        except httpx.HTTPError as e:
            logger.warning("fetch_rolling_attendance HTTP error: %s", e)
            return json.dumps({"error": "http_error", "detail": str(e)})

    @tool
    def fetch_health_tip() -> str:
        """Get one random general wellness tip from a static list (read-only, not derived from the user)."""
        try:
            r = httpx.get(f"{base}/api/health-tips", timeout=15.0)
            if r.status_code != 200:
                logger.warning("fetch_health_tip bad status status=%s", r.status_code)
            r.raise_for_status()
            data = r.json()
            if not isinstance(data, dict) or "tip" not in data:
                return json.dumps({"error": "http_error", "detail": "Invalid health tips response"})
            logger.info("fetch_health_tip ok")
            return json.dumps(data)
        except httpx.HTTPError as e:
            logger.warning("fetch_health_tip HTTP error: %s", e)
            return json.dumps({"error": "http_error", "detail": str(e)})

    return [fetch_month_attendance, fetch_rolling_attendance, fetch_health_tip]


def run_coach_turn(
    *,
    settings: Settings,
    jwt_token: str,
    user_message: str,
    history: list[tuple[str, str]],
    calendar_year: int | None = None,
    calendar_month: int | None = None,
) -> str:
    llm, _provider = create_chat_model(settings)
    tools = _build_tools(jwt_token, settings.backend_url)
    system_prompt = COACH_SYSTEM.format(monthly_goal=settings.monthly_goal_days)
    if calendar_year is not None and calendar_month is not None:
        month_name = calendar.month_name[calendar_month]
        system_prompt = (
            system_prompt
            + "\n\n"
            + CALENDAR_HINT.format(
                month_name=month_name,
                year=calendar_year,
                month=calendar_month,
            )
        )
    agent = create_agent(llm, tools, system_prompt=system_prompt)

    messages: list[Any] = []
    for role, text in history:
        if role == "user":
            messages.append(HumanMessage(content=text))
        elif role == "assistant":
            messages.append(AIMessage(content=text))
    messages.append(HumanMessage(content=user_message))

    result = agent.invoke(
        {"messages": messages},
        config={"recursion_limit": 45},
    )
    return _final_ai_text(result)


__all__ = ["run_coach_turn", "LlmConfigurationError"]

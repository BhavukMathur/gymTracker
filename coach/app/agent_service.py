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
You have tools to read their logged attendance only (read-only). You cannot log or change days.

Rules for tools:
- When discussing a specific month, you MUST call fetch_month_attendance with integer year and month (1–12; April = 4).
- A successful tool response includes "entries" (date → present/missed). An empty entries object means no days were logged yet — that is normal, not a system failure.
- If the tool JSON contains "error": "unauthorized", tell them to log out and log in again (JWT expired or invalid).
- If the tool JSON contains "error": "http_error", the API could not be reached; mention checking that the backend is running and BACKEND_URL in coach settings.
- Do not claim a retrieval failure if you received a normal tool payload with entries (even empty).

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

    return [fetch_month_attendance]


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
        config={"recursion_limit": 35},
    )
    return _final_ai_text(result)


__all__ = ["run_coach_turn", "LlmConfigurationError"]

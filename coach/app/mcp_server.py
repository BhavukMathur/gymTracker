"""stdio MCP server: read-only month attendance for Gym Tracker (Cursor MCP).

Run from the ``coach/`` directory with the coach venv activated::

    python -m app.mcp_server

Configure Cursor to spawn this process; set env in ``mcp.json``:

- ``BACKEND_URL`` — Spring API base (default ``http://127.0.0.1:8080``)
- ``GYMTRACKER_MCP_JWT`` — same Bearer JWT the app uses for ``/api/attendance/month``
"""

from __future__ import annotations

import json
import logging
import os

import httpx
from mcp.server.fastmcp import FastMCP

logger = logging.getLogger(__name__)

mcp = FastMCP(
    "gym-tracker",
    instructions=(
        "Gym Tracker read-only tool: calendar month attendance for the user "
        "implied by GYMTRACKER_MCP_JWT. Backend base URL from BACKEND_URL."
    ),
)


def _backend_base() -> str:
    return os.environ.get("BACKEND_URL", "http://127.0.0.1:8080").rstrip("/")


def _jwt() -> str | None:
    raw = os.environ.get("GYMTRACKER_MCP_JWT")
    if raw is None or not str(raw).strip():
        return None
    return str(raw).strip()


@mcp.tool()
def fetch_month_attendance(year: int, month: int) -> str:
    """Load this user's gym attendance for one calendar month (read-only).

    Args:
        year: Four-digit year (e.g. 2026).
        month: Month number from 1 to 12.

    Returns:
        JSON string: entries (date -> present/missed), present_days, marked_days,
        or error object (configuration / unauthorized / http_error).
    """
    token = _jwt()
    if not token:
        return json.dumps(
            {
                "error": "configuration",
                "detail": (
                    "Missing GYMTRACKER_MCP_JWT. Add it to the MCP server env in "
                    "Cursor (same JWT as the Gym Tracker web app)."
                ),
            }
        )

    y = int(year)
    m = int(month)
    if m < 1 or m > 12:
        return json.dumps({"error": "validation", "detail": "month must be 1–12"})
    if y < 1900 or y > 2100:
        return json.dumps({"error": "validation", "detail": "year out of supported range"})

    base = _backend_base()
    try:
        r = httpx.get(
            f"{base}/api/attendance/month",
            params={"year": y, "month": m},
            headers={"Authorization": f"Bearer {token}"},
            timeout=20.0,
        )
        if r.status_code == 401:
            logger.info("fetch_month_attendance unauthorized year=%s month=%s backend=%s", y, m, base)
            return json.dumps({"error": "unauthorized", "detail": "JWT rejected by API"})
        if r.status_code != 200:
            logger.warning(
                "fetch_month_attendance bad status year=%s month=%s status=%s",
                y,
                m,
                r.status_code,
            )
        r.raise_for_status()
        raw = r.json()
        if not isinstance(raw, dict):
            raw = {}
        present_days = sum(1 for v in raw.values() if v is True)
        marked_days = len(raw)
        payload = {
            "year": y,
            "month": m,
            "entries": raw,
            "present_days": present_days,
            "marked_days": marked_days,
        }
        logger.info(
            "fetch_month_attendance ok year=%s month=%s present_days=%s marked_days=%s",
            y,
            m,
            present_days,
            marked_days,
        )
        return json.dumps(payload)
    except httpx.HTTPError as e:
        logger.warning("fetch_month_attendance HTTP error: %s", e)
        return json.dumps({"error": "http_error", "detail": str(e)})


def main() -> None:
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()

# Coach agent: agentic workflow summary

The Gym Tracker coach ([`coach/app/agent_service.py`](coach/app/agent_service.py)) exposes three read-only tools:

| Tool | Role |
|------|------|
| `fetch_month_attendance` | Calendar month attendance (year + month 1–12) |
| `fetch_rolling_attendance` | Last *N* calendar days through today (rolling window) |
| `fetch_health_tip` | One random line from a static wellness list (not from user data) |

**Default flow:** use **one** attendance tool (month *or* rolling) unless the user clearly wants **two windows compared**. Prefer **attendance first**, then **optionally** `fetch_health_tip` as a short add-on, labeled as **general** advice, not derived from their log.

**Do not** call both month and rolling in one answer unless the user **explicitly** asks to compare a named month vs a rolling period.

---

## 1. All three tools in sequence (month + rolling + health tip)

The user should ask to **compare two windows** and **also** want a **separate** generic tip.

**Example inputs**

- For **May 2026**, summarize my month from the calendar, then also show how I’ve been over the **last 30 rolling days** through today, and **end with a general wellness tip** (not from my data).
- **Compare** my **this calendar month** to my **last 2 weeks (rolling)**, and after that add **one random health tip** for motivation.
- Give me **two views**: full **April 2026** month and **last 7 days rolling**; then **suggest a generic healthy habit** from your tips, separate from my numbers.

**Expected tool order:** `fetch_month_attendance` → `fetch_rolling_attendance` → `fetch_health_tip`.

---

## 2. Only month and rolling (no health tip)

The user wants **data only** for two different windows—**no** extra idea or tip.

**Example inputs**

- How is my **this month** going **vs the last 14 days** (rolling day window)? I only want the numbers, **no extra advice**.
- **March 2026** calendar month and **last 30 rolling days**—compare **present** counts, **no tips**.
- Show **February 2026** month and **this week (7-day rolling)** side by side; I’m not looking for ideas.

**Expected tools:** `fetch_month_attendance` + `fetch_rolling_attendance` (no `fetch_health_tip`).

---

## 3. Only the health tip

The user wants a **static tip** only, **no** attendance.

**Example inputs**

- **One random health tip**—don’t use my log.
- Give me a **generic wellness idea**; I don’t need my stats.
- I’m in a rush—**just a quick habit tip**, not about my month.

**Expected tool:** `fetch_health_tip` only.

---

## Notes

- Tighter phrasing in user messages nudges the model: **“compare this month to rolling *N* days”** (two attendance tools) vs **“tip only, not my data”** (one tool).
- The model may still choose tools unless instructions align with the system prompt in `coach/app/agent_service.py` (`COACH_SYSTEM`); these examples are written to match that behavior.

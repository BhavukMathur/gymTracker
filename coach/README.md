# AI coach (LangChain + OpenAI or Gemini)

FastAPI service that runs a LangChain `create_agent` graph with **OpenAI** or **Google Gemini** (factory in `app/llm_factory.py`) and a tool that reads attendance from the Spring API using the caller’s JWT.

## LLM configuration

| Env | Meaning |
|-----|--------|
| `LLM_PROVIDER` | `auto` (default), `openai`, or `gemini` |
| `OPENAI_API_KEY` | OpenAI API key |
| `OPENAI_MODEL` | Default `gpt-4o-mini` |
| `GEMINI_API_KEY` or `GOOGLE_API_KEY` | Gemini key |
| `GEMINI_MODEL` | Default `gemini-2.0-flash` |

With **`LLM_PROVIDER=auto`**, OpenAI is used when `OPENAI_API_KEY` is set; otherwise Gemini is used if a Gemini/Google key is set.

## Setup

```bash
cd coach
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
# Edit .env — set at least OPENAI_API_KEY or GEMINI_API_KEY
```

If `pip` is not found on your system, always use **`python3 -m pip`** (or `python -m pip` inside the venv).

## Run

With the Spring backend on port 8080:

```bash
. .venv/bin/activate
uvicorn app.main:app --host 127.0.0.1 --port 8090
```

`./run-local.sh` from the repo root starts this automatically after the API is up (default port **8090**). The Vite dev server proxies `/coach` to that port.

## Endpoints

- `GET /coach/health` — status, resolved `llm_provider`, and which keys are present
- `POST /coach/chat` — body: `{ "message": string, "history": [{ "role": "user"|"assistant", "content": string }] }`, header: `Authorization: Bearer <JWT>`

## MCP (Cursor): month attendance

A small **stdio** MCP server exposes `fetch_month_attendance` so Cursor’s agent can call the same Spring endpoint as the coach tools.

1. Install deps (includes `mcp`): `pip install -r requirements.txt` inside `coach/` (venv).
2. Add **`GYMTRACKER_MCP_JWT`** (same Bearer token as the web app after login) and **`BACKEND_URL`** if needed to `coach/.env` (see `.env.example`).
3. **Project MCP:** this repo includes `.cursor/mcp.json`, which runs `python -m app.mcp_server` with `cwd` = `coach/`, sets **`PYTHONPATH`** to `coach/` (some Cursor builds do not honor `cwd` for stdio MCP, which caused `ModuleNotFoundError: No module named 'app'`), and loads `coach/.env` via `envFile`. Reload MCP in Cursor after changing env.
4. Start the Spring backend on the URL in `BACKEND_URL`.

For a portable snippet without `envFile`, see `coach/cursor-mcp.example.json` and set `env` explicitly.

Run locally without Cursor: `python -m app.mcp_server` (stdio MCP; use an MCP client to drive it).

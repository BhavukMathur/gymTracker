#!/usr/bin/env bash
# Run Gym Tracker locally without Docker: Spring Boot (8080) + Vite (5173).
# Requires: Java 17+, Maven, Node 18+, npm, and MySQL per readMe.md / application.properties.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
COACH_DIR="$ROOT/coach"
COACH_PORT="${COACH_PORT:-8090}"
PROFILE="${SPRING_PROFILE:-dev}"

usage() {
  echo "Usage: $0 [backend|frontend|all]" >&2
  echo "  backend   — Maven Spring Boot only (http://localhost:8080)" >&2
  echo "  frontend  — Vite dev server only (http://localhost:5173, proxies /api to 8080)" >&2
  echo "  all       — backend, LangChain coach (${COACH_PORT}), then frontend (default)" >&2
  echo "Set SPRING_PROFILE to dev or prod (default: dev)." >&2
  echo "AI coach: set OPENAI_API_KEY and/or GEMINI_API_KEY (or GOOGLE_API_KEY); see coach/.env.example." >&2
}

wait_for_port() {
  local port="$1"
  local max_attempts="${2:-90}"
  local i=0
  echo "Waiting for port ${port}..."
  while [ "$i" -lt "$max_attempts" ]; do
    if bash -c "echo >/dev/tcp/127.0.0.1/${port}" 2>/dev/null; then
      echo "Port ${port} is open."
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "Timed out waiting for port ${port}. Is MySQL running?" >&2
  return 1
}

BACKEND_PID=""
COACH_PID=""

cleanup() {
  if [ -n "$COACH_PID" ] && kill -0 "$COACH_PID" 2>/dev/null; then
    echo "Stopping coach (PID ${COACH_PID})..."
    kill "$COACH_PID" 2>/dev/null || true
    wait "$COACH_PID" 2>/dev/null || true
  fi
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Stopping backend (PID ${BACKEND_PID})..."
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
}

run_backend() {
  cd "$BACKEND_DIR"
  mvn spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
}

run_frontend() {
  cd "$FRONTEND_DIR"
  if [ ! -d node_modules ]; then
    npm install
  fi
  npm run dev
}

run_coach() {
  if [ ! -d "$COACH_DIR" ] || [ ! -f "$COACH_DIR/requirements.txt" ]; then
    echo "Coach directory missing; skipping AI coach service." >&2
    return 0
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 not found; skipping AI coach service." >&2
    return 0
  fi
  if [ ! -f "$COACH_DIR/.venv/bin/python" ]; then
    echo "Creating coach virtualenv and installing dependencies..."
    python3 -m venv "$COACH_DIR/.venv"
  fi
  # Prefer python -m pip so installs work when `pip` is not on PATH (common on macOS).
  "$COACH_DIR/.venv/bin/python" -m pip install -q -r "$COACH_DIR/requirements.txt"
  (
    cd "$COACH_DIR"
    BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
    export BACKEND_URL
    export COACH_PORT
    exec "$COACH_DIR/.venv/bin/python" -m uvicorn app.main:app --host 127.0.0.1 --port "$COACH_PORT"
  ) &
  COACH_PID=$!
  if ! wait_for_port "$COACH_PORT" 45; then
    echo "Coach did not open port ${COACH_PORT}; AI panel may fail until it is running." >&2
  fi
}

run_all() {
  trap cleanup EXIT INT TERM

  cd "$BACKEND_DIR"
  mvn spring-boot:run -Dspring-boot.run.profiles="$PROFILE" &
  BACKEND_PID=$!

  wait_for_port 8080

  run_coach

  cd "$FRONTEND_DIR"
  if [ ! -d node_modules ]; then
    npm install
  fi
  npm run dev
}

MODE="${1:-all}"
case "$MODE" in
  -h|--help)
    usage
    exit 0
    ;;
  backend)
    run_backend
    ;;
  frontend)
    run_frontend
    ;;
  all)
    run_all
    ;;
  *)
    echo "Unknown argument: $MODE" >&2
    usage
    exit 1
    ;;
esac

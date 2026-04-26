#!/usr/bin/env bash
# Run Gym Tracker locally: Kafka (Docker) + Spring Boot (8080) + Vite (5173), optional MySQL and coach.
# See kafka.md for Kafka. Requires: Java 17+, Maven, Node 18+, npm, MySQL on 3306, Docker for bundled Kafka.
#
# One-shot full stack: ./run-local.sh   (or: ./run-local.sh all)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
COACH_DIR="$ROOT/coach"
COACH_PORT="${COACH_PORT:-8090}"
# Host port the Docker Compose file maps to Kafka in the container (PLAINTEXT_HOST; default 9092 on host)
KAFKA_PORT="${KAFKA_PORT:-${KAFKA_HOST_PORT:-9092}}"
PROFILE="${SPRING_PROFILE:-dev}"

usage() {
  echo "Usage: $0 [backend|frontend|all]" >&2
  echo "  backend   — ZooKeeper+Kafka (Docker) when needed, then Maven Spring Boot (http://localhost:8080)" >&2
  echo "  frontend  — Vite dev server only (http://localhost:5173, proxies /api to 8080)" >&2
  echo "  all       — default: Kafka, MySQL check, backend, LangChain coach (${COACH_PORT}), Vite" >&2
  echo "Environment: SPRING_PROFILE, COACH_PORT, KAFKA_PORT (or KAFKA_HOST_PORT for the host listener to Kafka)." >&2
  echo "  SKIP_KAFKA=1     — do not start or require Kafka (announcement pipeline will fail if broker is down)" >&2
  echo "  SKIP_MYSQL_CHECK=1 — do not wait for MySQL on 127.0.0.1:3306" >&2
  echo "AI coach: set OPENAI_API_KEY and/or GEMINI_API_KEY (or GOOGLE_API_KEY); see coach/.env.example." >&2
  echo "Kafka details: $ROOT/kafka.md" >&2
}

# Prefer Docker Compose v2, fall back to v1
compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    return 1
  fi
}

wait_for_port() {
  local port="$1"
  local max_attempts="${2:-90}"
  local err_msg="${3:-}"
  if [ -z "$err_msg" ]; then
    err_msg="Timed out waiting for port ${port}."
  fi
  local i=0
  echo "Waiting for 127.0.0.1:${port}..."
  while [ "$i" -lt "$max_attempts" ]; do
    if bash -c "echo >/dev/tcp/127.0.0.1/${port}" 2>/dev/null; then
      echo "Port ${port} is open."
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "$err_msg" >&2
  return 1
}

wait_for_mysql_or_exit() {
  if [ "${SKIP_MYSQL_CHECK:-0}" = "1" ]; then
    echo "SKIP_MYSQL_CHECK=1: not verifying MySQL on 3306"
    return 0
  fi
  if wait_for_port 3306 60 "MySQL is not on 127.0.0.1:3306. Start it (e.g. local service or: docker compose up -d db from repo root) or set SKIP_MYSQL_CHECK=1."
  then
    return 0
  fi
  return 1
}

ensure_local_kafka() {
  if [ "${SKIP_KAFKA:-0}" = "1" ]; then
    echo "SKIP_KAFKA=1: not starting or checking Kafka (set broker yourself on spring.kafka.bootstrap-servers if needed; see ${ROOT}/kafka.md)" >&2
    return 0
  fi
  if bash -c "echo >/dev/tcp/127.0.0.1/${KAFKA_PORT}" 2>/dev/null; then
    echo "Using existing broker on 127.0.0.1:${KAFKA_PORT}."
    return 0
  fi
  if ! command -v docker >/dev/null 2>&1; then
    echo "Error: no listener on 127.0.0.1:${KAFKA_PORT} and docker is not installed." >&2
    echo "Start a broker on that port (see ${ROOT}/kafka.md) or set SKIP_KAFKA=1" >&2
    return 1
  fi
  if ! compose -f "$ROOT/docker-compose.yml" -p gymtracker-local up -d zookeeper kafka; then
    echo "Error: 'docker compose up' failed for zookeeper and kafka. See ${ROOT}/kafka.md" >&2
    return 1
  fi
  if ! wait_for_port "$KAFKA_PORT" 90 "Kafka did not open on 127.0.0.1:${KAFKA_PORT}. Check: docker compose -p gymtracker-local -f ${ROOT}/docker-compose.yml ps"; then
    return 1
  fi
  # Spring default in application.properties is 127.0.0.1:9092 — align when using a custom host port
  if [ "$KAFKA_PORT" != "9092" ]; then
    export SPRING_KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${KAFKA_PORT}"
  fi
  echo "ZooKeeper + Kafka are up (Docker, project name gymtracker-local)."
  return 0
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
  # Intentionally do not stop Docker Kafka: keep broker for the next run; see kafka.md
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
  if ! wait_for_port "$COACH_PORT" 45 "Coach did not open port ${COACH_PORT}; the AI panel may be unavailable until it is running."
  then
    :
  fi
}

run_all() {
  trap cleanup EXIT INT TERM

  ensure_local_kafka || exit 1
  wait_for_mysql_or_exit || exit 1

  cd "$BACKEND_DIR"
  if [ -n "${SPRING_KAFKA_BOOTSTRAP_SERVERS:-}" ]; then
    export SPRING_KAFKA_BOOTSTRAP_SERVERS
  fi
  mvn spring-boot:run -Dspring-boot.run.profiles="$PROFILE" &
  BACKEND_PID=$!

  if ! wait_for_port 8080 120 "Spring Boot did not open port 8080. Check the Maven log for errors (MySQL, Kafka, etc.)."
  then
    exit 1
  fi

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
    ensure_local_kafka || exit 1
    wait_for_mysql_or_exit || exit 1
    if [ -n "${SPRING_KAFKA_BOOTSTRAP_SERVERS:-}" ]; then
      export SPRING_KAFKA_BOOTSTRAP_SERVERS
    fi
    cd "$BACKEND_DIR" && mvn spring-boot:run -Dspring-boot.run.profiles="$PROFILE"
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

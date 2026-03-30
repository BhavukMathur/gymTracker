# Gym Attendance Tracker

Simple working project with:
- Spring Boot backend
- JWT login
- MySQL persistence
- React (Vite) frontend; vanilla backup in `frontend/legacy/index.vanilla.html`

## Requirements
- Java 17+ installed
- Maven installed globally (`mvn --version`)
- Node.js 18+ and npm (for the React frontend)
- Python 3 (optional; only for serving the vanilla backup)
- MySQL running locally

## Default login
- username: `demo`
- password: `demo123`

## MySQL config
Configured in `backend/src/main/resources/application.properties`:
- username: `root`
- password: `rootroot`
- db: `gymtracker` (auto-created if not exists)

## Run frontend (Node.js + npm)
```bash
cd frontend
npm install
npm run dev
```

Dev URL: `http://localhost:5173` (proxies `/api` to the backend on port 8080).

Production build:
```bash
cd frontend
npm run build
npm run preview   # optional; serves dist/
```

Set `VITE_API_BASE` when the UI is not served with a dev proxy (e.g. `VITE_API_BASE=http://localhost:8080 npm run build`).

### Vanilla backup (no build step)
Open or serve `frontend/legacy/index.vanilla.html` (expects API at `http://localhost:8080`), e.g.:
```bash
cd frontend/legacy
python3 -m http.server 5500
```
Then open `http://localhost:5500/index.vanilla.html`.

## API
- `POST /api/auth/login`
  - body: `{ "username": "demo", "password": "demo123" }`
- `POST /api/attendance` (Bearer token required)
  - body: `{ "date": "2026-03-24", "attended": true }`
- `GET /api/attendance/month?year=2026&month=3` (Bearer token required)
- `GET /api/app/profile` — `{ "profile": "dev" }` or `"prod"` (no auth; used by the UI)

## Run backend
**Profiles:** **dev** (default) turns on SQL logging; **prod** turns it off. Shared settings stay in `application.properties`; overrides are in `application-dev.properties` and `application-prod.properties`.

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

If you omit `-Dspring-boot.run.profiles`, the app uses the default profile **dev** (`spring.profiles.default`).

Backend URL: `http://localhost:8080`

Swagger: `http://localhost:8080/swagger-ui/index.html#/auth-controller/login`
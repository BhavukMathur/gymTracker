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

The sign-in UI is on the **home page** (`/`):


| How you run the UI                                | Open in the browser                              |
| ------------------------------------------------- | ------------------------------------------------ |
| **npm** (`npm run dev`)                           | [http://localhost:5173/](http://localhost:5173/) |
| **Docker Compose** (default `FRONTEND_HOST_PORT`) | [http://localhost:3000/](http://localhost:3000/) |


If you only start the backend with Maven, open the UI via Vite or a static build; the API does not serve the React login screen by itself.

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

## GraphQL

The backend exposes GraphQL at `**POST /graphql**` (same origin as the REST API). Operations mirror the attendance REST endpoints above; send a **Bearer JWT** in the `Authorization` header, same as for `/api/attendance`.

Opening `**GET /graphql`** in a browser is allowed without a token; the server responds with **405 Method Not Allowed** and an `Allow: POST` header (queries and mutations must use **POST** with a JSON body).


| Operation                 | REST equivalent             | Description                                                                                                                |
| ------------------------- | --------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Query `attendanceMonth`   | `GET /api/attendance/month` | Attendance for the given calendar month (returns a list of `{ date, attended }`; REST returns a map keyed by date string). |
| Mutation `saveAttendance` | `POST /api/attendance`      | Upsert attendance for a date (`date` is ISO `yyyy-MM-dd`).                                                                 |


Schema lives in `backend/src/main/resources/graphql/schema.graphqls`. Controllers are under `com.gymtracker.graphql`.

### Steps to test (curl)

1. **Start the backend** (see [Run backend](#run-backend)).
2. **Obtain a JWT** (copy the `token` value from the response):

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}'
```

1. **Query month attendance** (replace `YOUR_JWT`):

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT" \
  -d '{
    "query": "query ($year: Int!, $month: Int!) { attendanceMonth(year: $year, month: $month) { date attended } }",
    "variables": { "year": 2026, "month": 3 }
  }'
```

1. **Mutation: save attendance** (replace `YOUR_JWT`):

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT" \
  -d '{
    "query": "mutation ($date: String!, $attended: Boolean!) { saveAttendance(date: $date, attended: $attended) { date attended message } }",
    "variables": { "date": "2026-03-15", "attended": true }
  }'
```

You can also use any GraphQL client (Insomnia, Postman, etc.): URL `http://localhost:8080/graphql`, method POST, JSON body with `query` and optional `variables`, plus the `Authorization` header.

## Docker (optional, full stack)

Docker Compose runs **three services**—frontend, backend, and MySQL—**one container each** (traffic: UI → API → DB). Host ports are chosen so they do not overlap local dev:


| Mode                            | UI                                             | API                                              |
| ------------------------------- | ---------------------------------------------- | ------------------------------------------------ |
| **npm** `npm run dev`           | [http://localhost:5173](http://localhost:5173) | proxied to `localhost:8080`                      |
| **Maven** `mvn spring-boot:run` | (use Vite or static)                           | [http://localhost:8080](http://localhost:8080)   |
| **Docker Compose**              | [http://localhost:3000](http://localhost:3000) | [http://localhost:18080](http://localhost:18080) |


```bash
docker compose up --build
```

Override with `FRONTEND_HOST_PORT`, `BACKEND_HOST_PORT`, and `VITE_API_BASE` if needed. Details: `**goLive.md**` (Docker section) and `**k8s/**` for Kubernetes.

**Host vs container port for the Docker backend:** `BACKEND_HOST_PORT` (default **18080**) is only the port on your machine. Inside the container Spring Boot still listens on **8080**, and the Compose **healthcheck** calls `http://localhost:8080` there—so changing `BACKEND_HOST_PORT` does **not** break the healthcheck. You would only change the healthcheck if you changed the app port inside the container (for example via `SERVER_PORT`).

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
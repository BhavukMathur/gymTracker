# Gym Attendance Tracker

Simple working project with:
- Spring Boot backend
- JWT login
- MySQL persistence
- Plain frontend calendar UI

## Requirements
- Java 17+ installed
- Maven installed globally (`mvn --version`)
- Python 3 installed (`python3 --version`)
- MySQL running locally

## Default login
- username: `demo`
- password: `demo123`

## MySQL config
Configured in `backend/src/main/resources/application.properties`:
- username: `root`
- password: `rootroot`
- db: `gymtracker` (auto-created if not exists)

## Run frontend (global Python)
```bash
cd frontend
python3 -m http.server 5500
```

Frontend URL: `http://localhost:5500`

## API
- `POST /api/auth/login`
  - body: `{ "username": "demo", "password": "demo123" }`
- `POST /api/attendance` (Bearer token required)
  - body: `{ "date": "2026-03-24", "attended": true }`
- `GET /api/attendance/month?year=2026&month=3` (Bearer token required)

## Run backend (global Maven)
```bash
cd backend
mvn spring-boot:run
```

Backend URL: `http://localhost:8080`

Swagger: `http://localhost:8080/swagger-ui/index.html#/auth-controller/login`
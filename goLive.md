# Going live (demo / practice)

Stack: **React (Vite) frontend**, **Spring Boot backend**, **MySQL**. CORS is already open for any origin (`SecurityConfig`), so the UI can call the API from a separate host. Use **HTTPS** URLs in production.

---

## Frontend

**What to host:** static files from `npm run build` (output: `frontend/dist`).

**Free-tier options:** [Vercel](https://vercel.com), [Netlify](https://netlify.com), [Cloudflare Pages](https://pages.cloudflare.com).

**Steps:**

1. Connect the repo (or deploy via CLI).
2. Set project root to **`frontend`**, install command `npm install`, build command **`npm run build`**, publish directory **`dist`** (adjust if your host expects paths relative to repo root).
3. Add a **build-time** environment variable: **`VITE_API_BASE`** = your backend’s public URL (e.g. `https://your-api.onrender.com`, no trailing slash). The app reads this in `frontend/src/api.js`; without it, production builds would still point at `localhost:8080`.
4. Redeploy the frontend whenever the backend URL changes.

---

## Backend

**What to host:** the Spring Boot JAR (Java 17+). Needs a **long-running** process (not typical serverless functions).

**Free / low-cost options (often with idle sleep or credits):** [Render](https://render.com), [Railway](https://railway.app), [Fly.io](https://fly.io). First request after sleep can be slow on free tiers—normal for demos.

**Steps:**

1. Create a **Web Service** (or equivalent) pointing at the **`backend`** directory (or a Dockerfile—see Docker below).
2. **Build:** e.g. `mvn -B -DskipTests package` (from `backend`).
3. **Start:** e.g. `java -jar target/*.jar` (exact JAR name from `pom.xml` / `target/`).
4. Set **`SPRING_PROFILES_ACTIVE=prod`** (prod turns off verbose SQL logging per `application-prod.properties`).
5. **Override secrets and DB** with environment variables (do not commit real values):
   - **`SPRING_DATASOURCE_URL`** — JDBC URL to hosted MySQL (cloud DBs often need `sslMode=REQUIRED` or similar—follow the provider’s JDBC example).
   - **`SPRING_DATASOURCE_USERNAME`** / **`SPRING_DATASOURCE_PASSWORD`**
   - **`APP_JWT_SECRET`** — long random string, **at least 32 characters** (maps to `app.jwt.secret`).

---

## Database

**What you need:** **MySQL** compatible with your existing `application.properties` / JPA setup.

**Steps:**

1. Provision a **MySQL** instance on a host that offers a free or trial tier (tiers change—confirm on the vendor’s pricing page). Common patterns: **MySQL add-on on the same platform as the API** (e.g. Railway-style plugin), or a **managed MySQL** service with a hobby/free tier.
2. Create a database and user; copy **host, port, database name, user, password**.
3. Build the JDBC URL and set **`SPRING_DATASOURCE_*`** on the backend as above.
4. With **`spring.jpa.hibernate.ddl-auto=update`**, schema updates apply on startup—fine for demos; for real production you’d usually use migrations and stricter DDL settings.

**Note:** Many free offers are **PostgreSQL-only**. Switching would require changing the Spring datasource driver and URL—not required if you stay on MySQL.

---

## Docker

**How Docker helps you go live**

- **Backend image:** A multi-stage `Dockerfile` can compile with Maven and run the JAR in a slim JRE image. The same image runs locally and on **Docker-friendly** hosts (Render, Fly.io, Railway, Kubernetes, a VPS).
- **Repeatable builds:** Less “works on my laptop” drift than ad hoc JDK installs on the server.
- **Local full stack:** **`docker compose`** runs **three services** (frontend → backend → MySQL) in separate containers, matching a typical split deployment. This is **additive**: you can still run the frontend with npm, the backend with Maven, and MySQL on the host exactly as in `readMe.md`.

**Compose (repo root)**

```bash
docker compose up --build
```

- UI: `http://localhost:${FRONTEND_HOST_PORT:-3000}` (nginx serving the Vite build).
- API: `http://localhost:${BACKEND_HOST_PORT:-18080}` — **not** 8080 on purpose, so **`mvn spring-boot:run` can keep using 8080** while Compose is not running, or while you run both stacks on different API ports.
- MySQL is **not** published to the host by default (avoids clashing with a local MySQL on port 3306). Override with Compose if you need host access.

**Port cheat sheet:** Vite dev UI **5173** (proxies `/api` → **8080**); local Spring Boot **8080**; Dockerized API on the host **18080**; Dockerized UI **3000**.

Optional environment variables (shell or `.env` next to `docker-compose.yml`): `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `APP_JWT_SECRET`, `VITE_API_BASE` (must match what the **browser** uses to reach the API, default `http://localhost:18080` with the default `BACKEND_HOST_PORT`), `FRONTEND_HOST_PORT`, `BACKEND_HOST_PORT`.

**Kubernetes-style layout**

Example manifests live under **`k8s/`** (one workload per tier: MySQL StatefulSet, backend Deployment, frontend Deployment). Build images from `backend/` and `frontend/` Dockerfiles, push or load them into your cluster, then apply:

```bash
kubectl apply -k k8s/
```

Edit `k8s/*secret*.yaml` for real passwords and JWT secret. Set **`VITE_API_BASE`** at **frontend image build time** to the public URL clients use to reach the API (Ingress, LoadBalancer, or `http://localhost:8080` when using `kubectl port-forward` on the backend Service).

**What Docker does not replace by itself**

- **Managed database in the cloud** is still usually a **separate** service unless you deliberately run MySQL in Compose on the same machine—which is acceptable for **practice demos**, weaker for production backups and HA.
- **Static frontend hosting** is still often done without containers (upload `dist/` to Vercel/Netlify/Pages) unless you add an **nginx** (or similar) service in Compose.
- Docker does **not** remove the need for **env vars** (`SPRING_DATASOURCE_*`, `APP_JWT_SECRET`, `SPRING_PROFILES_ACTIVE`) or for setting **`VITE_API_BASE`** when building the frontend for a remote API.

**Summary:** Use Docker to **package and run** the backend (and optionally DB + static files) consistently; use a **static host + managed MySQL + container or JAR deploy** for a typical split free-tier demo.

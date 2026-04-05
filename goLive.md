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
- **Local full stack:** **`docker-compose`** can run **MySQL + Spring Boot** (and optionally a container serving the Vite `dist/`) with one command—good for onboarding and for running everything on **one VM** (e.g. a small cloud instance).

**What Docker does not replace by itself**

- **Managed database in the cloud** is still usually a **separate** service unless you deliberately run MySQL in Compose on the same machine—which is acceptable for **practice demos**, weaker for production backups and HA.
- **Static frontend hosting** is still often done without containers (upload `dist/` to Vercel/Netlify/Pages) unless you add an **nginx** (or similar) service in Compose.
- Docker does **not** remove the need for **env vars** (`SPRING_DATASOURCE_*`, `APP_JWT_SECRET`, `SPRING_PROFILES_ACTIVE`) or for setting **`VITE_API_BASE`** when building the frontend for a remote API.

**Summary:** Use Docker to **package and run** the backend (and optionally DB + static files) consistently; use a **static host + managed MySQL + container or JAR deploy** for a typical split free-tier demo.

# Kafka for local development

The **in-app announcements** feature publishes events to Kafka and a consumer stores them in MySQL. The Spring app expects a broker at the address in `backend/src/main/resources/application.properties`:

```properties
spring.kafka.bootstrap-servers=127.0.0.1:9092
```

You do **not** get a working announcements pipeline without a **running** Kafka (and ZooKeeper) reachable at that address.

## Kafka in Docker, Spring and UI on the host (normal setup)

**Yes** — you can and usually should run only **ZooKeeper + Kafka in Docker**, and run **Spring Boot with Maven, MySQL, and the Vite dev server directly on the host** (not in a container). That is how **`./run-local.sh`** is wired: the broker listens on **`127.0.0.1:9092` on your machine** because the compose file **maps the container’s Kafka port to the host**. Anything on the host, including a JVM you start with `mvn spring-boot:run` or an IDE, connects to the broker the same as any other local process.

**Requirements:** same machine; nothing special beyond “Docker runs the broker” and “the app uses `127.0.0.1:9092` in `application.properties`” (or `SPRING_KAFKA_BOOTSTRAP_SERVERS` if you use another host port).

**Not required** for that setup: building a backend Docker image, or putting the Spring app in Kubernetes, unless you want to.

## Option 1: Docker (recommended, matches this repo)

From the **repository root** (same folder as `docker-compose.yml`):

```bash
docker compose up -d zookeeper kafka
```

`./run-local.sh` uses a fixed project name so repeated runs are predictable:

```bash
docker compose -p gymtracker-local -f docker-compose.yml up -d zookeeper kafka
```

You can use the same `docker compose -p gymtracker-local ...` prefix to **stop** only Kafka + ZooKeeper without changing other stacks.

- **ZooKeeper** and **Kafka** are defined in `docker-compose.yml` (Confluent 7.5.0).
- The broker is exposed to your machine as **`localhost:9092`** (override with `KAFKA_HOST_PORT` if that port is taken).
- On first start, the containers may take 30–60 seconds before `9092` accepts connections.

**Stop** (leaves other compose services, if any, as they were; match `-p` if you used it):

```bash
docker compose -p gymtracker-local -f docker-compose.yml stop zookeeper kafka
# or, without a project name:
# docker compose stop zookeeper kafka
```

**Remove** the containers and anonymous volumes (optional, broker data is recreated):

```bash
docker compose down
```

(Use `down -v` only if you know you do not need other project volumes such as the MySQL volume from the full stack.)

**Quick check** (optional):

```bash
docker compose exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

If the command returns without an error, the listener is up.

## Option 2: Already running a broker

If you use another local install (Homebrew, a tarball, etc.) that listens on **`127.0.0.1:9092`**, you do not need the Docker services above. Ensure nothing else is using `9092`, or point Spring to your broker, for example:

```bash
export SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9093
# then start the backend with that environment variable, or set it in application-dev.properties
```

## `run-local.sh` behavior

`./run-local.sh` and `./run-local.sh backend` will:

- If `SKIP_KAFKA=1` is **not** set, try to use Docker to run `zookeeper` and `kafka`, and wait until **`127.0.0.1:9092`** is open.
- If a broker is **already** listening on that port, Docker is not started.
- If Docker is unavailable and nothing is on `9092`, the script exits with an error. Install Docker / start Kafka by hand, or set `SKIP_KAFKA=1` to continue without a broker (announcement publish will fail until a broker is available).

## Troubleshooting

- **Address already in use (9092)**  
  Another process or another Kafka is using the port. Stop it, or set `KAFKA_HOST_PORT` in the environment and map Docker to a different host port, then set `SPRING_KAFKA_BOOTSTRAP_SERVERS` to match (for example `127.0.0.1:9093` if you map `9093:9092` and keep the in-container listener as configured).

- **Connection refused in the app**  
  Wait until the broker is listening; the first `docker compose up` can take a while. `run-local.sh` waits for TCP on `9092` before starting Spring Boot (when it manages Kafka).

- **No Docker**  
  Install a local Kafka+ZooKeeper (or KRaft) and listen on `9092`, or set `SKIP_KAFKA=1` and fix the environment before using admin announcements.

## Live updates on the dashboard (short summary)

**Kafka** feeds the **server** (publish → topic → consumer → MySQL). The **browser** does not talk to Kafka; it only calls the REST API (e.g. `GET /api/announcements`). A normal page visit loads the list **once** when that request runs, so new rows appear only after a **refetch** (navigate, effect re-run) or a **full refresh** unless you add one of the following.

| Approach | What it is | When it fits |
|----------|------------|----------------|
| **Polling** | Periodically `GET /api/announcements` (e.g. every 15–30s) while the dashboard is open. | **Fastest to add**; fine if a small delay is acceptable. |
| **SSE** | Long-lived HTTP stream; the server **pushes** an event (or signal) when something new is stored, and the client updates state. | **Near real-time**, one-way **server → browser**; a good match for “new announcement” on a **single** backend instance. |
| **WebSockets** | Two-way channel over one connection. | Use if you need **frequent** client↔server messaging; **overkill** for one-way “there is news” if SSE suffices. |
| **Web push / system notifications** | Service worker + browser/OS notification APIs. | Good to **ping the user** when the tab is in the background; it does **not** by itself keep the in-page list in sync—you still use **polling or SSE** (or refetch on click) to refresh what React shows. |

**Practical picks:** default to **polling** for simplicity; choose **SSE** for a more **live** in-tab feed on one app node. If you run **several** Spring instances, **SSE** (in-memory connection registries) needs a **shared pub/sub** (e.g. Redis) to broadcast events to the right process—or **polling** stays the simpler scaling story.

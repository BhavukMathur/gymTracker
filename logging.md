# Logging and observability

The backend uses layered techniques so you can correlate **API requests**, **responses**, and **operational metrics** without duplicating noise. Implementation lives under `backend/src/main/java/com/gymtracker/observability/` and `backend/src/main/resources/logback-spring.xml`.

## Typical fields

| Category | Examples |
| -------- | -------- |
| **Request** | HTTP method, path, query string, selected headers (e.g. `User-Agent`, `Authorization` presence only), client IP, request size, correlation/trace id |
| **Response** | HTTP status, duration (latency), response size; avoid logging full bodies for auth or PII-heavy payloads unless redacted |
| **Other metrics** | JVM/memory, thread pools, DB pool, error counts, business counters (e.g. logins per minute) |

## Mechanisms (Spring Boot)

| Method | Mechanism | What you log and how |
| ------ | --------- | -------------------- |
| **1** | **SLF4J loggers** | Inject `org.slf4j.Logger` (or Lombok `@Slf4j`) in controllers, services, or `@ControllerAdvice` handlers. Call `log.info` / `log.debug` with **structured messages** (method, URI, status, duration). SLF4J is the **facade**; it does not pick the file format or rolling policy by itself—that comes from the binding (Logback). |
| **2** | **Logback** | Configure **appenders**, **log levels**, **MDC** (e.g. put `requestId` in MDC in a filter, clear after request), and **JSON/console** output in `logback-spring.xml` or `logback.xml`. Use this to define **where** logs go (console, file, JSON) and **enrichment** (patterns including MDC keys). SLF4J + Logback is the usual Spring Boot default. |
| **3** | **AOP (before / after)** | Spring AOP (`@Aspect`) around **service** or **repository** beans: log **method entry** (args summarized or hashed), **exit** (return type or size), and **exceptions**. Good for **domain logic** and cross-cutting timing; for HTTP-specific fields prefer filters or `HandlerInterceptor` unless the aspect targets `@RestController` methods. |
| **4** | **Filters / interceptors** | **Servlet `Filter`** (e.g. `OncePerRequestFilter`): first/last touch of the HTTP exchange—log **request line**, assign **correlation id**, measure **total time**. **`HandlerInterceptor`** (Spring MVC): **preHandle** / **afterCompletion** for controller-bound timing and status. Use filters for **all** requests; interceptors when you need dispatcher/controller context. |
| **5** | **Distributed tracing** | **Micrometer Tracing** + **Brave** or **OpenTelemetry**: automatic **trace/span** IDs propagated across services (HTTP headers). Exporters send spans to **Zipkin**, **Jaeger**, **Tempo**, etc. Complements logs: correlate SLF4J lines with `traceId`/`spanId` (often via MDC) for **end-to-end** request paths. |
| **6** | **Spring Boot Actuator** | Expose **`/actuator`** endpoints (e.g. **`health`**, **`metrics`**, **`prometheus`** when enabled). Surfaces **JVM**, **HTTP server**, and custom **Micrometer** metrics—not a replacement for request logs, but essential for **dashboards and alerts**. Secure actuator in production (`management.endpoints.web.exposure`, authentication). |

**Practical combination:** use **SLF4J + Logback** for application logs, a **filter** (or MVC interceptor) for consistent **request/response metadata** and duration, **MDC** for correlation IDs, **Micrometer Tracing** for distributed traces, and **Actuator + Prometheus** (or similar) for **metrics**. Reserve **AOP** for depth around specific layers without cluttering every controller manually.

## Sample logs (captured from a real run)

The following lines were taken from backend output after the **`Backend logging:`** marker in a local `./run-local.sh` session (same capture as in the Cursor terminal log: entries from **GET `/api/attendance/month`** onward illustrate one full request; startup lines show Logback and Actuator). Logger names appear shortened as `c.g.*` in the console (`com.gymtracker.*`).

### 1 — SLF4J

```text
2026-04-09T23:48:35.310+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=e7a8c82247cbc8a1 c.g.controller.AttendanceController - [SLF4J] REST getMonthAttendance user=bhavukmathur year=2026 month=4
```

### 2 — Logback (layout + MDC in `logback-spring.xml`)

Every line uses the Logback console pattern (`reqId`, `traceId`, `spanId` from MDC). Startup also emits an explicit `[Logback]` line:

```text
2026-04-09T23:48:21.584+05:30 INFO  [main] reqId= traceId= spanId= c.g.o.LogbackStartupLogger - [Logback] GymTracker logging uses logback-spring.xml (appenders, levels, MDC keys: requestId, traceId, spanId)
```

### 3 — AOP (before / after)

```text
2026-04-09T23:48:35.310+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=e7a8c82247cbc8a1 c.g.o.ServiceLayerLoggingAspect - [AOP] before AttendanceService.getMonthAttendance(..) args.length=3
2026-04-09T23:48:35.324+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=e7a8c82247cbc8a1 c.g.o.ServiceLayerLoggingAspect - [AOP] after AttendanceService.getMonthAttendance(..) durationMs=13 returnType=LinkedHashMap
```

### 4 — Filter and interceptor

**Filter** (`HttpRequestLoggingFilter`):

```text
2026-04-09T23:48:35.222+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=b40fb45fd77b6fa7 c.g.o.HttpRequestLoggingFilter - [Filter] incoming GET /api/attendance/month?year=2026&month=4
2026-04-09T23:48:35.327+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=b40fb45fd77b6fa7 c.g.o.HttpRequestLoggingFilter - [Filter] completed GET /api/attendance/month status=200 durationMs=105
```

**Interceptor** (`RequestLoggingInterceptor`):

```text
2026-04-09T23:48:35.301+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=e7a8c82247cbc8a1 c.g.o.RequestLoggingInterceptor - [Interceptor] preHandle GET /api/attendance/month handler=com.gymtracker.controller.AttendanceController#getMonthAttendance(int, int, Principal)
2026-04-09T23:48:35.326+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=e7a8c82247cbc8a1 c.g.o.RequestLoggingInterceptor - [Interceptor] afterCompletion GET /api/attendance/month status=200 durationMs=25 exception=false
```

### 5 — Distributed tracing (Micrometer + Brave)

```text
2026-04-09T23:48:35.327+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=b40fb45fd77b6fa7 c.g.o.HttpRequestLoggingFilter - [Tracing] traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=b40fb45fd77b6fa7 (Micrometer Brave bridge; see MDC traceId/spanId in Logback pattern)
```

The same `traceId` / `spanId` values appear in the Logback prefix on each line for that request.

### 6 — Spring Boot Actuator + Micrometer

Spring Boot exposes the management base path (from the same run):

```text
2026-04-09T23:48:20.914+05:30 INFO  [main] reqId= traceId= spanId= o.s.b.a.e.web.EndpointLinksResolver - Exposing 3 endpoints beneath base path '/actuator'
```

On startup, the app logs where to scrape metrics and how the demo counter relates to request logs:

```text
2026-04-09T23:48:21.584+05:30 INFO  [main] reqId= traceId= spanId= c.g.o.LogbackStartupLogger - [Actuator] Micrometer metrics: GET /actuator/metrics, GET /actuator/prometheus; per-request counter gymtracker.observability.demo.requests is logged as [Actuator/Micrometer] in HttpRequestLoggingFilter
```

Per request, the filter increments a Micrometer counter and logs:

```text
2026-04-09T23:48:35.327+05:30 INFO  [http-nio-8080-exec-2] reqId=f55ba7d3-b8b2-476b-9d67-dc1494f09f5f traceId=69d7ed7bebe594beb40fb45fd77b6fa7 spanId=b40fb45fd77b6fa7 c.g.o.HttpRequestLoggingFilter - [Actuator/Micrometer] incremented counter gymtracker.observability.demo.requests method=GET uri=/api/attendance/month status=200
```

# Feature 16 — Spring Boot Actuator + Prometheus

## What we built

Two Maven dependencies (`spring-boot-starter-actuator` and
`micrometer-registry-prometheus`) plus a few lines of YAML give us a
fully-equipped operational surface for the backend:

- `/actuator/health` — overall + per-component health (DB up? disk
  full? SSL ok?). Public, used by load balancers and k8s probes.
- `/actuator/info` — build version + custom info contributors.
- `/actuator/metrics` — JSON snapshot of every meter.
- `/actuator/prometheus` — same metrics in the text format Prometheus
  scrapes.
- `/actuator/env`, `/actuator/loggers`, `/actuator/mappings`,
  `/actuator/beans` — admin-only diagnostic endpoints.

Plus three custom Micrometer meters that demonstrate the core
primitives — Counter, Gauge, Timer:

- `classicmodels.employee.deletions{strategy="..."}` (Counter) —
  bumped each time an employee is deleted, tagged with the strategy.
- `classicmodels.customers.count` (Gauge) — current row count from
  the customers table, sampled at scrape time.
- `classicmodels.photo.upload.duration` (Timer) — recorded around
  the file-write in `PhotoStorageService.save()`.

And a thin admin-only page at `/admin/health` that renders status,
build info, and a few key metrics by calling the actuator endpoints.

Files touched:

- `classicmodels-backend/pom.xml` — `spring-boot-starter-actuator`,
  `micrometer-registry-prometheus`
- `classicmodels-backend/src/main/resources/application.yml` —
  `management.*` config, `info.*` contributors
- `classicmodels-backend/src/main/java/.../security/SecurityConfig.java` —
  permit `/actuator/health`, `/actuator/info`, `/actuator/prometheus`;
  require ADMIN for everything else under `/actuator/**`
- `classicmodels-backend/src/main/java/.../metrics/AppMetrics.java` (new)
- `classicmodels-backend/src/main/java/.../service/EmployeeService.java` —
  inject metrics, increment deletion counter
- `classicmodels-backend/src/main/java/.../photo/PhotoStorageService.java` —
  wrap save() in the photo-upload timer
- `classicmodels-ui/src/app/admin/admin-health.component.ts` (new)
- `classicmodels-ui/src/app/admin/admin.routes.ts` (new)
- `classicmodels-ui/src/app/app.routes.ts` — `/admin` route
- `classicmodels-ui/src/app/app.component.ts` — `isAdmin()` helper
- `classicmodels-ui/src/app/app.html` — admin-only nav link

## Why this is worth learning

**Actuator is what makes a Spring Boot app operatable.** Without it,
running in production means SSH-ing in to check logs and manually
inspecting state. With it, you have a uniform HTTP surface for
liveness, configuration, runtime diagnostics, and metrics — all the
things SREs and on-call engineers need at 3 AM.

**Micrometer is the metrics standard.** It's a façade: you write
`Counter c = Counter.builder("my.metric").register(registry)` once,
and the same code emits to Prometheus, Datadog, New Relic, CloudWatch,
or whatever else the registry happens to be configured for. Same
relationship as SLF4J → Logback / Log4j: write against the API,
swap the backend at deploy time.

**Prometheus is the open-source metrics workhorse.** Pull-based
(servers scrape your `/metrics` endpoint, not the other way around),
multi-dimensional labels, a rich query language (PromQL), and the
canonical pairing with Grafana for dashboards. Even shops that don't
use Prometheus directly often consume the same text format because
every other tool understands it.

**Three meter types cover most observability needs.** Counter for
"how many things happened," Gauge for "what's the current value,"
Timer for "how long does this take." Once you internalize the trio,
adding observability to any service becomes mechanical.

## Background

### Spring Boot Actuator — the endpoint catalog

Out of the box, Actuator gives you 20+ endpoints. The ones you'll
use most:

| Endpoint | What it does |
|---|---|
| `health` | UP/DOWN status; per-component health checks. |
| `info` | Free-form info from `info.*` properties + `InfoContributor` beans. |
| `metrics` | JSON snapshot of every meter (`GET /actuator/metrics/{name}` for one). |
| `prometheus` | Same metrics in Prometheus text format. |
| `env` | Resolved Spring property values (sensitive — admin-only). |
| `loggers` | List + change log levels at runtime via POST. |
| `mappings` | Every URL → handler mapping in the app (debugging gold). |
| `beans` | The full bean graph. |
| `threaddump` | Live thread dump. |
| `heapdump` | Generates an HPROF heap dump file for download. |

By default only `health` is exposed via HTTP — everything else has to
be opted in via `management.endpoints.web.exposure.include`. This
is a security default, not an oversight: each endpoint exposes some
amount of internal state.

References:

- [Spring Boot — Production-ready Endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints)
- [Spring Boot — Actuator endpoint reference](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)

### Health endpoints — liveness vs. readiness

Kubernetes (and most production orchestrators) distinguishes:

- **Liveness probe** (`/actuator/health/liveness`) — "is the JVM
  alive?" If this fails, the pod is killed and restarted. Should fail
  only on unrecoverable problems (deadlock, OOM-recovered).
- **Readiness probe** (`/actuator/health/readiness`) — "is this
  instance ready to receive traffic?" If this fails, the pod stays
  alive but is removed from the load-balancer pool. Fails on temporary
  problems (DB unreachable, cache warming, bulk-import in progress).

Spring Boot exposes both when `management.endpoint.health.probes.enabled`
is true (we enabled it). The components contributing to each are
configurable; by default all health indicators contribute to both.

References:

- [Spring Boot — Kubernetes Probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes)
- [Kubernetes — Liveness, Readiness and Startup Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

### Micrometer — the three core meter types

```java
// Counter — strictly increasing, tagged
Counter requests = Counter.builder("orders.placed")
    .tag("region", "eu")
    .register(registry);
requests.increment();

// Gauge — sampleable value, can go up or down
AtomicInteger queueSize = new AtomicInteger();
Gauge.builder("worker.queue.size", queueSize, AtomicInteger::get)
    .register(registry);

// Timer — count + duration distribution
Timer.builder("processing.duration")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry);
timer.record(() -> doWork());

// Distribution Summary — like Timer but for non-time values
DistributionSummary payloadSize = DistributionSummary.builder("api.payload.size")
    .baseUnit("bytes")
    .register(registry);
payloadSize.record(payload.length);
```

Two more worth knowing:

- **LongTaskTimer** — for operations that span many seconds/minutes.
  Reports COUNT (in-flight) and TOTAL DURATION (in-flight time
  accumulated so far). Useful for batch jobs.
- **FunctionCounter / FunctionTimer** — wrap an existing counter
  (e.g. `executor.getCompletedTaskCount()`) without writing your own
  bookkeeping.

References:

- [Micrometer — Concepts](https://docs.micrometer.io/micrometer/reference/concepts.html)
- [Micrometer — Reference: meters](https://docs.micrometer.io/micrometer/reference/concepts/meters.html)
- [Spring Boot — Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)

### Tags — the "dimensional" part of metrics

The classic "1 metric per thing" model becomes a pain when you have
many variants of the same thing (per region, per status code, per
strategy). Modern metric systems use **tags** (also called labels):

```java
Counter.builder("classicmodels.employee.deletions")
    .tag("strategy", "CASCADE")
    .register(registry).increment();
```

emits a single timeseries `classicmodels_employee_deletions_total{strategy="CASCADE"}`.
A second call with `tag("strategy", "SOFT")` emits a SECOND timeseries
with the same metric name and a different tag value. Prometheus
considers them separate, but related.

Why this matters: in PromQL you can write:

```promql
sum by (strategy) (rate(classicmodels_employee_deletions_total[5m]))
```

…to get one line per strategy in a Grafana panel. Same data, sliced
on demand.

**Tag cardinality matters.** Each unique combination of tag values is
a separate timeseries. If you tag by `userId`, with a million users,
that's a million timeseries — Prometheus's storage and query cost
scales linearly with cardinality. Rule of thumb: tags should have a
small, bounded set of values (HTTP status code, region, env, type).
Never tag by anything user-controlled or unbounded.

References:

- [Prometheus — Naming labels](https://prometheus.io/docs/practices/naming/#labels)
- [Charity Majors — Cardinality matters](https://www.honeycomb.io/blog/cardinality-explained)

### Prometheus — the pull model

Prometheus scrapes metrics endpoints on a schedule (default 15s).
Your service writes counters/gauges/timers in memory; Prometheus
hits `/actuator/prometheus`, parses the text, stores the timeseries.

The text format looks like:

```
# HELP classicmodels_employee_deletions_total Total employees deleted
# TYPE classicmodels_employee_deletions_total counter
classicmodels_employee_deletions_total{strategy="SOFT",application="classicmodels-backend"} 7.0
classicmodels_employee_deletions_total{strategy="CASCADE",application="classicmodels-backend"} 2.0
```

Pull beats push for several reasons:

- Service teams don't have to know about the metrics infrastructure;
  they just expose the endpoint.
- A failed scrape is a clear signal (target down). With push, a
  silent service looks identical to a healthy one.
- Local scraping (Prometheus on the same network) avoids public
  endpoints.
- Same endpoint → many consumers (Prometheus, Datadog OpenMetrics
  receiver, ad-hoc curl).

Drawbacks: Prometheus has to know about all your service instances
(via service discovery — k8s, Consul, DNS, file-based, EC2 SD…).
Push works better for ephemeral jobs that finish before scrape time;
those use the Prometheus **Pushgateway**.

References:

- [Prometheus — Why pull?](https://prometheus.io/docs/introduction/comparison/#why-do-you-pull-rather-than-push?)
- [Prometheus — Documentation](https://prometheus.io/docs/introduction/overview/)
- [Cloud Native Computing Foundation — Prometheus](https://www.cncf.io/projects/prometheus/)

### Securing actuator endpoints — the threat model

Each endpoint leaks some amount of state:

- **`env`** — every property, including `spring.datasource.password`
  if it's in plain text. **Never** expose to the public internet.
- **`mappings`** — your full URL surface (might reveal hidden admin
  routes you didn't realize were exposed).
- **`heapdump`** — a full memory snapshot, often containing live
  user data + sensitive runtime state.
- **`beans`** — the bean graph, indirectly leaking architecture.

Common patterns:

| Endpoint | Exposure |
|---|---|
| `health/liveness`, `health/readiness` | Public (orchestrator probes). |
| `health` (full) | Authenticated, OR public with `show-details: when-authorized`. |
| `info` | Public (it's branded marketing for your service). |
| `prometheus` | Restricted by network (firewall/ACL), not auth. |
| Everything else | Authenticated + ADMIN role. |

Some teams also run actuator on a **separate port** (`management.server.port=9001`)
that isn't reachable from the internet at all — orchestrator and
metrics scrapers talk to that port, the public LB only sees the
main port. Defense in depth.

References:

- [Spring Boot — Securing Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security)
- [OWASP — Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) — adjacent: what you do AFTER you have the data

### Common production stack

The endpoints we expose plug into a standard observability pipeline:

```
[Spring Boot apps]
       │ exposes /actuator/prometheus
       ▼
  [Prometheus] ◄─ pulls metrics on schedule
       │ stores timeseries
       ▼
   [Grafana] ◄─── queries with PromQL, renders dashboards + alerts
       │
       │ alerts via webhook
       ▼
[PagerDuty / Slack]
```

Other slots in the pipeline:

- **Logs** → Loki / ELK / Splunk
- **Traces** → Jaeger / Tempo / Zipkin
- **All three (logs + metrics + traces)** → Datadog / New Relic / Honeycomb

References:

- [Charity Majors — Observability vs Monitoring](https://charity.wtf/2018/04/01/the-emerging-observability-paradigm/)
- [Google SRE Book — Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/)
- [The Three Pillars of Observability](https://www.honeycomb.io/blog/three-pillars-of-observability) — context for where metrics fit

## The code, walked through

### Configuration in `application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, env, loggers, mappings, beans
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    tags:
      application: classicmodels-backend

info:
  app:
    name: Classic Models Backend
    java-version: ${java.version}
```

A few things doing real work:

- **`exposure.include`** — strict opt-in list. Adding a new endpoint
  is one word; removing access just as easy.
- **`show-details: always`** — for the dev environment. Shows the
  per-component breakdown publicly. In prod use `when-authorized`.
- **`probes.enabled: true`** — gives us `/health/liveness` and
  `/health/readiness` separately, for k8s probes.
- **`metrics.tags.application`** — every meter gets this tag added.
  Lets Grafana show all instances of `classicmodels-backend`
  filtered cleanly even when several services share one Prometheus.

### Counter, with a tag

```java
public Counter employeeDeletions(String strategy) {
    return Counter.builder("classicmodels.employee.deletions")
            .description("Total employees deleted, tagged by strategy")
            .tag("strategy", strategy)
            .register(registry);
}
```

The registry caches by name + tags, so calling this from a hot path
is fine — the underlying meter is built once per unique strategy
value and reused. Increment with `.increment()`.

### Gauge — sampleable, no events

```java
private final AtomicInteger customerCount = new AtomicInteger(0);

public AppMetrics(MeterRegistry registry, ...) {
    registry.gauge("classicmodels.customers.count", customerCount);
}
```

The gauge holds a reference to the AtomicInteger. Every Prometheus
scrape, Micrometer reads `customerCount.get()` and emits the value.
We refresh it on startup (and could on a `@Scheduled` interval).

### Timer — count + duration distribution in one meter

```java
this.photoUploadTimer = Timer.builder("classicmodels.photo.upload.duration")
        .description("Time taken to write an uploaded employee photo to disk")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry);

// at the call site:
return metrics.photoUploadTimer().recordCallable(() -> {
    // ... actual work ...
});
```

`recordCallable` runs the lambda, captures the duration on success or
failure, and re-throws the exception. Result in Prometheus:

```
classicmodels_photo_upload_duration_seconds_count{...} 18
classicmodels_photo_upload_duration_seconds_sum{...} 4.32
classicmodels_photo_upload_duration_seconds{quantile="0.5",...} 0.1
classicmodels_photo_upload_duration_seconds{quantile="0.95",...} 0.42
classicmodels_photo_upload_duration_seconds{quantile="0.99",...} 1.8
```

One line of code, four numbers Grafana can plot.

### SecurityConfig — split exposure rules

```java
.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

Order matters — Spring Security evaluates matchers top to bottom.
The specific `health/**` + `info` + `prometheus` rule wins for those
paths; everything else under `/actuator/**` falls through to the
ADMIN-required rule.

### Frontend — the admin status page

```ts
forkJoin({
  health:  this.http.get<any>('/api/v1/actuator/health'),
  info:    this.http.get<any>('/api/v1/actuator/info'),
  metrics: forkJoin(this.METRIC_NAMES.map(m =>
    this.http.get<any>(`/api/v1/actuator/metrics/${m.key}`).toPromise()
      .then(r => ({ name: m.label, value: r?.measurements?.[0]?.value ?? 0 })),
  )),
}).subscribe(({ health, info, metrics }) => { ... });
```

Three actuator calls in parallel via `forkJoin`. The metrics endpoint
is per-name (you ask `/actuator/metrics/jvm.memory.used` and get
back a JSON with the current value); we hand-pick a list of metrics
to show. Production you'd skip this entirely and hit Grafana for the
real dashboard.

## How to test

1. **Restart the backend** (`SPRING_PROFILES_ACTIVE=local
   ./mvnw spring-boot:run`). The startup log should mention the
   actuator endpoints being exposed.
2. **Verify the public endpoints** without auth:
   ```sh
   curl http://localhost:9090/api/v1/actuator/health
   # → {"status":"UP","components":{"db":{"status":"UP",...}}}
   curl http://localhost:9090/api/v1/actuator/info
   # → {"app":{"name":"Classic Models Backend",...}}
   curl http://localhost:9090/api/v1/actuator/prometheus | head -20
   # → # HELP ... lots of metrics ...
   ```
3. **Verify the admin-only endpoints reject unauthenticated requests**:
   ```sh
   curl -i http://localhost:9090/api/v1/actuator/env
   # → HTTP/1.1 401 Unauthorized
   ```
4. **Sign in as `admin / admin123`** in the SPA. The toolbar should
   show a new "Status" link (not visible to non-admin users).
5. **Click "Status"** → you land at `/admin/health` showing:
   - Big UP / DOWN badge.
   - Per-component health rows (db, diskSpace, ping, ssl).
   - Build info from `/actuator/info`.
   - Selected metrics (JVM heap, CPU, HTTP request count, customer
     count, DB connection count).
6. **Trigger custom metrics**:
   - Delete a few employees through the UI.
   - Upload a photo to an employee.
   - Refresh the status page → customer count gauge stayed put,
     deletion counter went up.
7. **Inspect raw Prometheus output** for our custom meters:
   ```sh
   curl http://localhost:9090/api/v1/actuator/prometheus | grep classicmodels
   ```
   Expected lines:
   ```
   classicmodels_employee_deletions_total{strategy="NULLIFY",application="classicmodels-backend"} 3.0
   classicmodels_customers_count{application="classicmodels-backend"} 122.0
   classicmodels_photo_upload_duration_seconds_count{application="classicmodels-backend"} 1.0
   classicmodels_photo_upload_duration_seconds_sum{application="classicmodels-backend"} 0.045
   ```

## What you just learned

- **Spring Boot Actuator** as the production-ready endpoint package
  every Spring app should ship with.
- **Health vs. liveness vs. readiness** — three different "is it
  ok?" questions for three different audiences.
- **Micrometer** as the vendor-neutral metrics façade, with
  Counter/Gauge/Timer as the universal vocabulary.
- **Tags / labels** for dimensional metrics, and the cardinality
  trade-off.
- **Prometheus's pull model** and why it scales differently than
  push-based systems.
- **Securing actuator endpoints** via Spring Security request
  matchers, with health/info public and the rest ADMIN-only.
- **Reading metrics from a UI** — a thin admin page is a useful
  fallback before you set up a real Grafana dashboard.

## Study materials

### Actuator + Spring Boot

- [Spring Boot — Production-ready Features](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Spring Boot — Endpoints reference](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Baeldung — Actuator in Spring Boot](https://www.baeldung.com/spring-boot-actuators) — narrative walkthrough
- [Sergi Almar — Custom Actuator endpoints](https://reflectoring.io/spring-boot-custom-actuator-endpoint/) — for when you outgrow the built-ins

### Micrometer

- [Micrometer — Concepts](https://docs.micrometer.io/micrometer/reference/concepts.html)
- [Micrometer — Common timer/counter recipes](https://docs.micrometer.io/micrometer/reference/concepts/instrumenting-libraries.html)
- [Spring Boot — Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)

### Prometheus

- [Prometheus — Documentation](https://prometheus.io/docs/introduction/overview/)
- [Prometheus — Best Practices: Naming](https://prometheus.io/docs/practices/naming/)
- [PromLabs — PromQL primer](https://promlabs.com/promql-cheat-sheet/)
- [Robust Perception blog (Brian Brazil)](https://www.robustperception.io/blog/) — Prometheus-specific operational wisdom

### Grafana

- [Grafana — Getting started](https://grafana.com/docs/grafana/latest/getting-started/)
- [Grafana — JVM/Spring Boot dashboards](https://grafana.com/grafana/dashboards/?search=spring-boot) — pre-built dashboards you can import

### Production observability

- [Charity Majors — Observability is a hammer](https://www.honeycomb.io/blog/observability-a-3-year-retrospective)
- [Google SRE Book — chapter 6: Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/)
- [Google SRE Workbook — Practical Alerting](https://sre.google/workbook/alerting-on-slos/) — the four golden signals
- [Honeycomb — three pillars (and why it's not enough)](https://www.honeycomb.io/blog/three-pillars-of-observability)

### Beyond Prometheus

- [OpenTelemetry](https://opentelemetry.io/) — the emerging standard, unifies metrics + traces + logs in one wire format
- [Jaeger / Zipkin](https://www.jaegertracing.io/) — distributed tracing, complementary to metrics
- [Datadog / New Relic / Honeycomb](https://www.datadoghq.com/) — managed observability platforms
- [Grafana Loki](https://grafana.com/oss/loki/) — log aggregation in the same UI as your metrics

### Hardening

- [Spring Boot — Endpoint Security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security)
- [Cloud Native Patterns — Health checks](https://learnk8s.io/spring-boot-kubernetes-guide) — k8s-specific patterns
- [Twelve-Factor App — Admin processes](https://12factor.net/admin-processes) — broader operational doctrine

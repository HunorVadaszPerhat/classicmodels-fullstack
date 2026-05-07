# Feature 17 — Docker Compose for the full stack

## What we built

One command — `docker compose up --build` — boots the entire app:
MySQL 8.4 with seed data, the Spring Boot backend, and the Angular SPA
served by nginx with `/api/*` reverse-proxied to the backend. Open
`http://localhost/` and the app behaves identically to the dev mode,
except no Maven, no `ng serve`, no separate database start needed.

Files added:

- `classicmodels-backend/Dockerfile` — multi-stage Maven build →
  slim JRE runtime image.
- `classicmodels-backend/.dockerignore` — keeps `target/`, IDE files,
  and the local-only `application-local.yml` out of the build context.
- `classicmodels-ui/Dockerfile` — multi-stage Node build →
  nginx-alpine serve.
- `classicmodels-ui/nginx.conf` — serves the SPA, falls back to
  `/index.html` for HTML5 routes, reverse-proxies `/api/*` and
  WebSocket upgrades.
- `classicmodels-ui/.dockerignore`.
- `fullstack/docker-compose.yml` — the orchestrator: db + backend +
  frontend, with healthchecks, named volumes, and the right
  `depends_on` ordering.

The existing `classicmodels-backend/docker-compose.yml` (used by
`spring-boot-docker-compose` to auto-start MySQL during dev) is
untouched. The two modes coexist because they use different
container names, different network, and different volume names.

## Why this is worth learning

Docker Compose is the entry point to "infrastructure as code." Instead
of a README that says "install MySQL 8.4, set these passwords, run
this seed file, also start the backend, also start the frontend,
configure CORS, enable WS upgrades…" the README says `docker compose
up`. The compose file IS the README, and it's executable.

Three sub-skills converge:

**Multi-stage Docker builds.** Compiled-language services have a
build-time phase (compiler, package manager, source code, test deps)
and a run-time phase (just the binary + runtime). Multi-stage
Dockerfiles let you keep the build tooling out of the final image —
~600 MB for build, ~190 MB for runtime in this project's case.
Same trick works for Go, Rust, C#, anything that compiles.

**Layer caching.** Each line in a Dockerfile is a layer; Docker
re-uses cached layers as long as their inputs haven't changed.
Copying `pom.xml` first and resolving dependencies BEFORE copying
source code means a one-line code change rebuilds in seconds instead
of re-downloading the entire dependency tree. Same trick with
`package-lock.json` for npm.

**Service discovery + networking.** Inside a compose network, every
service is reachable by its service name as the hostname (`db`,
`backend`, `frontend`). No hardcoded IPs, no DNS to set up, no
`/etc/hosts` editing. This is the same model Kubernetes uses, just
simpler.

## Background

### Image vs. container

- **Image** — the static, immutable filesystem snapshot. Built once,
  pushed to a registry, pulled on demand. Like a stamp.
- **Container** — a running process based on an image, with its own
  filesystem layer for writes (which is discarded on restart unless
  you mount a volume). Like a stamp impression — you can make many
  from one stamp.

You build images, you run containers. The verbs in the Docker CLI
follow this distinction: `docker build`, `docker push` for images;
`docker run`, `docker exec`, `docker stop` for containers.

References:

- [Docker — Concepts: images](https://docs.docker.com/get-started/overview/#docker-objects)
- [Julia Evans — How containers work](https://wizardzines.com/zines/containers/) — friendly visual primer

### Multi-stage builds

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
COPY pom.xml settings.xml ./
RUN mvn -B -s settings.xml dependency:go-offline
COPY src ./src
RUN mvn -B -s settings.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
COPY --from=build /app/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

The first `FROM` starts a build stage. The second `FROM` starts a
fresh stage — anything from the first stage is discarded unless
explicitly carried over with `COPY --from=build`. The final image is
based on the **last** `FROM`; everything before is throwaway.

Why this matters: the JDK image is ~400 MB; the JRE image is ~180 MB.
The Maven dependency cache adds another couple hundred. By keeping
the build artifacts and Maven repo in the build stage and copying
just the jar to the runtime stage, the image we ship is dramatically
smaller. Faster pulls, faster scans, smaller surface area.

References:

- [Docker — Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Spring Boot — Container images](https://docs.spring.io/spring-boot/reference/packaging/container-images.html)

### Layer caching — why pom.xml comes first

Docker builds layer-by-layer. When a layer's input hasn't changed,
Docker reuses the cached result — no re-execution. Each `COPY` is a
checkpoint: if the COPYed files match the previous build, every
layer below is cached.

Compare these two orderings:

**Naive:**

```dockerfile
COPY .. .
RUN mvn package
```
Every code change rewrites the entire `.`. Maven re-downloads the
universe.

**Cache-aware:**
```dockerfile
COPY pom.xml ./
RUN mvn dependency:go-offline    # ← cached layer
COPY src ./src
RUN mvn package                  # ← only this re-runs
```
Code change → only the last two layers rebuild. Dependency download
happens once per `pom.xml` change.

Same pattern applies anywhere there's a "manifest first, deps next,
source last" build:

- `package.json` → `npm ci` → `COPY .` (Node)
- `requirements.txt` → `pip install` → `COPY .` (Python)
- `go.mod` + `go.sum` → `go mod download` → `COPY .` (Go)

References:

- [Docker — Layer caching best practices](https://docs.docker.com/build/cache/)
- [Increment — Caching strategies](https://increment.com/cloud/caching-strategy/)

### `.dockerignore` — keep secrets and clutter out

When you run `docker build`, Docker uploads the entire build context
(your project directory) to the daemon. Without filtering this:

- It's slow (gigabytes of `target/`, `node_modules/`).
- IDE config and editor swap files end up in image layers.
- Worst: secrets like `application-local.yml` ship in your image.

`.dockerignore` is a `.gitignore`-style filter applied to the build
context. Anything matching is silently excluded. Treat it as a
required file, not optional.

References:

- [Docker — .dockerignore](https://docs.docker.com/build/building/context/#dockerignore-files)

### Compose service discovery

Each service in `docker-compose.yml` becomes:
1. A container with `container_name: <name>`.
2. A DNS name `<service-name>` reachable from every other container
   on the same compose network.

So this in the backend's environment:

```yaml
SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/classicmodels
```

works because `db` is the service name of the MySQL container.
Compose creates a default network (one per project), every service
joins it, and the embedded DNS resolves names to the right IPs.

This is exactly the model Kubernetes uses — service names as
hostnames, DNS for discovery — but Compose is much simpler to
reason about for a single-host deployment.

References:

- [Docker — Networking in Compose](https://docs.docker.com/compose/networking/)
- [Docker — Compose specification](https://docs.docker.com/compose/compose-file/)

### Healthchecks + `depends_on: condition: service_healthy`

`depends_on` by itself only enforces *startup order* — backend starts
after db. But MySQL takes 5-10 seconds to become available; a backend
launched immediately after MySQL's container starts will fail to
connect.

Adding a `healthcheck` to the db service gives Compose a way to
verify "is it actually ready?" Then the backend's
`depends_on.db.condition: service_healthy` blocks startup until the
healthcheck passes.

```yaml
db:
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost",
           "-uappuser", "-papppass"]
    interval: 10s
    timeout: 5s
    retries: 10
backend:
  depends_on:
    db:
      condition: service_healthy
```

Without `service_healthy`, the alternative is application-level
retry logic (Spring's HikariCP does this for you, but only after a
configurable timeout — sometimes the backend gives up before the DB
is ready).

References:

- [Compose — Healthcheck](https://docs.docker.com/compose/compose-file/05-services/#healthcheck)
- [Spring Boot — Connection retry](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.sql.datasource.connection-pool) — what HikariCP does on its own

### nginx as both static-file server AND reverse proxy

In production deployments, the same nginx container often does two
jobs: serve the SPA's static assets and forward API calls to the
backend. The reasons:

1. **Same-origin** — the browser sees everything coming from
   `http://yourdomain.com/`. No CORS to configure, no preflights,
   no cookie domain mess.
2. **TLS termination** — one HTTPS cert, applied at the edge. Backend
   speaks plain HTTP behind it.
3. **Caching** — nginx is much better at caching static assets than
   any backend.

The config trick is `try_files`:

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

For each incoming request, nginx tries:
1. `$uri` — exact match for a file on disk (`/main.123abc.js`).
2. `$uri/` — directory with an index file.
3. `/index.html` — the SPA itself, lets Angular's router handle the URL.

Without this, a hard reload on `/employees/123` would 404 — the SPA
isn't there as a file.

For WebSocket upgrades:

```nginx
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

These are NOT in nginx's defaults; without them STOMP from Feature 10
would fail with "Connection refused" or hang at the handshake.

References:

- [nginx — Beginner's Guide](https://nginx.org/en/docs/beginners_guide.html)
- [Mozilla — try_files explained](https://developer.mozilla.org/en-US/docs/Web/Performance/Lazy_loading)
- [nginx — WebSocket proxying](https://nginx.org/en/docs/http/websocket.html)

### Volumes: bind mounts vs. named volumes

```yaml
volumes:
  - db_data:/var/lib/mysql                            # ← named volume
  - ../backend/db/init/01.sql:/docker-entrypoint-initdb.d/01.sql:ro  # ← bind mount
```

- **Named volumes** (`db_data:`) are managed by Docker. The location
  on disk is opaque to you (somewhere under `/var/lib/docker/volumes/`).
  Survive `docker compose down`. Best for app data.
- **Bind mounts** (`../path:...`) point at a specific path on the host
  filesystem. Best for config / source code in dev (changes reflect
  immediately).

The `:ro` suffix means read-only — the container can read the file
but not write. Use for any file you don't intend the container to
modify.

References:

- [Docker — Manage data in containers](https://docs.docker.com/storage/)

## The code, walked through

### Backend Dockerfile — layer-cache-friendly

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml settings.xml ./
COPY .mvn ./.mvn
RUN mvn -B -s settings.xml dependency:go-offline
COPY src ./src
RUN mvn -B -s settings.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S -G app -u 1000 app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/*.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Why this order specifically:
- pom.xml + settings.xml are copied **before** src/, so the
  dependency-download layer is cached unless the POM changes.
- `dependency:go-offline` populates the local Maven repo. After this,
  the source compile step is fully offline — predictable, fast.
- The final stage drops Maven and the JDK; only the JRE remains. Image
  is ~190 MB instead of ~600 MB.
- Switching to a non-root user (`USER app`) follows the principle of
  least privilege. Some platforms (OpenShift) enforce this.

### Frontend Dockerfile + nginx config

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration=production

FROM nginx:1.27-alpine AS runtime
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist/classicmodels-ui/browser/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

`npm ci` is the production-grade install: strict lockfile, fails on
mismatch, wipes `node_modules` first. Faster + more reproducible
than `npm install`.

The nginx config covers three concerns: SPA routing, API reverse-proxy,
WebSocket upgrades — see Background → "nginx as both…" above.

### docker-compose.yml — the orchestration

```yaml
services:
  db:
    image: mysql:8.4
    healthcheck: ...
    volumes:
      - db_data:/var/lib/mysql
      - ./classicmodels-backend/db/init/01-mysqlsampledatabase_auto_increment.sql:/docker-entrypoint-initdb.d/01-init.sql:ro

  backend:
    build:
      context: ./classicmodels-backend
    depends_on:
      db: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/classicmodels?...
      SPRING_DOCKER_COMPOSE_ENABLED: "false"
    expose: ["9090"]

  frontend:
    build:
      context: ./classicmodels-ui
    depends_on: [backend]
    ports:
      - "80:80"

volumes:
  db_data:
```

Three services on one network. `backend` reaches MySQL via `db:3306`.
The browser talks to `frontend:80` (mapped to host `:80`). The
`frontend` reverse-proxies `/api/*` to `backend:9090` via nginx.

`SPRING_DOCKER_COMPOSE_ENABLED: "false"` prevents Spring's
`spring-boot-docker-compose` from re-firing inside the container —
without it, the backend container would try to start its own MySQL
on top of the one we already have, and fail in confusing ways.

## How to test

**Prerequisites:** Docker Desktop (Mac/Windows) or Docker Engine + Compose
plugin (Linux). Verify with:

```sh
docker --version
docker compose version
```

**Build and run.** From the project root (`/fullstack/`):

```sh
docker compose up --build
```

The first run takes 2-5 minutes (downloading base images, resolving
Maven deps, npm install, building both apps). Subsequent runs reuse
cached layers and finish in seconds unless you change something.

When the log settles you should see lines from all three services:

```
classicmodels-fullstack-db        | ... ready for connections
classicmodels-fullstack-backend   | ... Started ClassicmodelsBackendApplication
classicmodels-fullstack-frontend  | nginx ... start worker process
```

**Open `http://localhost/`** in a browser. The login page shows.
Sign in as `admin / admin123`. Everything works:

- Lists, detail pages, charts, dashboard, org chart, CLV.
- WebSocket live-updates (Feature 10) via the nginx WebSocket-upgrade
  config.
- Actuator endpoints reachable through the proxy:
  `http://localhost/api/v1/actuator/health`.

**Inspect what's running:**

```sh
docker compose ps               # status of each service
docker compose logs -f backend  # tail backend logs
docker compose logs -f frontend # tail nginx logs
docker compose exec db sh       # shell into the MySQL container
```

**Stop and clean up:**

```sh
# Stops + removes containers, keeps the named volume
docker compose down

# Same, plus wipes the database volume so the seed re-runs next time
docker compose down -v
```

**Modify code, rebuild, restart:**

```sh
docker compose up --build backend   # rebuild + restart only backend
```

Layer caching means a backend code change rebuilds in seconds.

## Caveats

**OAuth2 redirect URI changes.** In dev mode the SPA runs on
`localhost:4200`. Inside the compose stack, the SPA runs on
`localhost:80`. Google rejects redirect URIs that don't match what's
registered. To use Google sign-in inside the compose stack, register
`http://localhost/api/v1/login/oauth2/code/google` (no `:4200`) in
Google Cloud Console as an additional Authorized redirect URI, and
set the `redirect-uri` in the backend's OAuth2 config (or environment
variable) accordingly.

**Corporate networks.** If your machine sits behind a TLS-intercepting
proxy (Zscaler / Bluecoat / Netskope), `docker build` may fail with
PKIX errors when pulling base images, fetching Maven deps, or
running `npm ci`. Two workarounds:

- Configure Docker Desktop to trust your corporate root CA
  (Settings → Resources → File sharing / Proxies, plus copy the cert
  into `/etc/ssl/certs/` of the build image via a `RUN` step).
- Build images on a non-corporate network and push them to a registry
  the corporate build agents can pull from.

**Spring Boot Buildpacks (alternative).** Instead of a hand-written
Dockerfile, you can run `./mvnw spring-boot:build-image` to produce
an OCI image using Cloud Native Buildpacks. The result is more
optimized (per-application-layer image, faster updates) but the
Dockerfile approach is easier to read for learning. Either works.

## What you just learned

- **Image vs. container** as the foundational vocabulary distinction.
- **Multi-stage Dockerfiles** for keeping build tooling out of
  runtime images.
- **Layer caching** as the engineering trick that makes iterative
  builds fast — pom.xml first, source last.
- **`.dockerignore`** as required hygiene for build speed and
  secret containment.
- **Compose service discovery** — service names become DNS names,
  no IPs needed.
- **`depends_on: condition: service_healthy`** + healthchecks for
  correct startup ordering.
- **nginx as static server + reverse proxy** with `try_files` for
  SPA routes and Upgrade headers for WebSocket.
- **Named volumes vs. bind mounts** and when to use each.
- **The compose-up developer experience** as the entry point to
  infrastructure-as-code.

## Study materials

### Docker fundamentals

- [Docker — Get Started](https://docs.docker.com/get-started/) — official intro
- [Julia Evans — How containers work](https://wizardzines.com/zines/containers/) — illustrated zine
- [Containers from scratch](https://www.youtube.com/watch?v=8fi7uSYlOdc) — Liz Rice's classic talk demonstrating how containers actually work
- [The Twelve-Factor App](https://12factor.net/) — operational doctrine that Docker fits neatly into

### Dockerfiles + multi-stage

- [Docker — Build best practices](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [Docker — Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [BuildKit](https://docs.docker.com/build/buildkit/) — modern build backend with parallelism + better caching

### Compose

- [Docker Compose docs](https://docs.docker.com/compose/)
- [Compose specification](https://github.com/compose-spec/compose-spec/blob/main/spec.md) — the standardized format
- [Awesome Compose](https://github.com/docker/awesome-compose) — example stacks for common combinations

### Spring Boot containers

- [Spring Boot — Container images guide](https://docs.spring.io/spring-boot/reference/packaging/container-images.html)
- [Spring Boot — Cloud Native Buildpacks](https://docs.spring.io/spring-boot/reference/packaging/container-images/cloud-native-buildpacks.html) — the Dockerfile-less alternative
- [Spring Boot — Docker Compose support](https://docs.spring.io/spring-boot/reference/features/dev-services.html#features.dev-services.docker-compose) — what we use in dev mode

### Angular / nginx production

- [Angular — Production builds](https://angular.dev/tools/cli/build)
- [nginx — Beginner's Guide](https://nginx.org/en/docs/beginners_guide.html)
- [nginx — Deploy SPA](https://nginx.org/en/docs/http/websocket.html) — WebSocket proxying

### From Compose to production

- [Kubernetes — Concepts](https://kubernetes.io/docs/concepts/) — the next step up when one machine isn't enough
- [Helm](https://helm.sh/) — packaging for Kubernetes
- [GitHub — kompose](https://github.com/kubernetes/kompose) — converts docker-compose.yml to k8s manifests
- [Docker Swarm](https://docs.docker.com/engine/swarm/) — Docker's own (less popular) k8s alternative

### Image security

- [Trivy](https://github.com/aquasecurity/trivy) — vulnerability scanner for images
- [Docker Scout](https://docs.docker.com/scout/) — built-in scanning in Docker Desktop
- [Distroless images](https://github.com/GoogleContainerTools/distroless) — even smaller than alpine, no shell at all
- [Sigstore + Cosign](https://docs.sigstore.dev/) — signing + verifying container images

### CI integration

- [GitHub Actions — Docker build/push](https://docs.github.com/en/actions/publishing-packages/publishing-docker-images) — what Feature 18 will pick up
- [Docker Hub vs GitHub Container Registry vs ECR](https://www.docker.com/blog/registry-overview/) — where built images live

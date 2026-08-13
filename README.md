# DeployTrack

Application deployment and monitoring platform — track projects, deployments, and logs across environments.

**Status:** Phase 10 (AWS deployment) complete. See [docs/requirements.md](docs/requirements.md) and [docs/openapi.yaml](docs/openapi.yaml) for the full plan.

## Running the whole stack

Requires Docker. Nothing else — no JDK, Node, or Maven.

```bash
docker compose up -d --build
```

Open http://localhost:3000 and use **Continue to the demo** for a read-only workspace with sample deployment history.

Nginx serves the built frontend and proxies `/api` to the backend, so everything is same-origin and no CORS configuration exists anywhere in the project. The backend port is deliberately not published to the host — it is reachable only through Nginx on the Docker network.

Startup is gated on health checks: the backend waits for Postgres to accept connections before Flyway runs, and the frontend waits for the backend to report healthy.

To stop, and to discard the database volume as well:

```bash
docker compose down -v
```

## Monitoring

`GET /api/dashboard/stats` returns deployment counts by status, success rate, recent activity windows, average duration, and the most recent deployments. Every figure is aggregated in SQL rather than by loading rows and counting in Java.

### Live log streaming

`GET /api/deployments/{id}/logs/stream` is a Server-Sent Events stream that pushes log lines as they are written:

```bash
curl -N http://localhost:8080/api/deployments/1/logs/stream -H "Authorization: Bearer $TOKEN"
```

It emits `log` events per line and a final `deployment-complete` event when the deployment settles, at which point the server closes the stream. SSE rather than WebSocket because delivery is one-directional — no reason to pay for full duplex, and browsers reconnect automatically via `EventSource`.

Events are published only after the writing transaction commits, so a rolled-back transaction can never surface a log line that later vanishes.

Known limitation: the subscriber registry is in-memory, so a stream only receives logs written by the instance it is connected to. Running multiple instances behind a load balancer would need a shared broker (Redis pub/sub or similar) to fan events out across them.

## Deployments

Triggering a deployment returns `202 Accepted`, not `201 Created` — the record exists, but the work it represents has not finished. Status transitions asynchronously to `SUCCESS` or `FAILED`, and clients poll `GET /api/deployments/{id}` for the outcome.

`IN_PROGRESS` is the only non-terminal state. Once a deployment settles it cannot change, so a retried CI callback can never rewrite history. Two deployments to the same project and environment cannot run concurrently.

A project's `status` is derived from its latest deployment (`IDLE`, `DEPLOYING`, `ACTIVE`, `FAILING`) rather than stored, so it can never drift out of sync with the deployment history.

### Completing a deployment

Real pipelines report outcomes through the callback endpoint:

```bash
curl -X PATCH http://localhost:8080/api/deployments/1/status -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"status":"SUCCESS"}'
```

Without one, a built-in simulator advances deployments through progress logs to a terminal state so the app demonstrates end to end. Disable it with `SIMULATOR_ENABLED=false`; tune it with `SIMULATOR_STEP_DELAY` and `SIMULATOR_FAILURE_RATE`.

## Authentication

Stateless JWT. Register, log in to receive a signed token, then send it as `Authorization: Bearer <token>` on every request.

```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"you","email":"you@example.com","password":"supersecret123"}'
```

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"you@example.com","password":"supersecret123"}'
```

Self-registration always assigns the `DEVELOPER` role — a client cannot choose its own role.

### First admin

Because nobody can self-register as an `ADMIN`, the first one is created on startup from environment variables, and only when no admin exists yet:

```bash
ADMIN_EMAIL=admin@deploytrack.dev ADMIN_PASSWORD=pick-a-strong-one mvn spring-boot:run
```

Leave these unset and the app logs a warning instead of creating an account with a predictable password. Once an admin exists this never runs again, so restarting cannot reset or overwrite it.

Tokens expire after 15 minutes. Override with `JWT_EXPIRY=8h` while developing; refresh tokens are deferred past MVP.

The signing key in `application.yml` is a local development default and is public, since this repository is public — anyone can read it and forge a token. That is only safe because it never leaves a developer machine. Running with `SPRING_PROFILES_ACTIVE=prod` requires `JWT_SECRET`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD` to be supplied explicitly; the application refuses to start if any are missing rather than falling back to a known key.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL
- **Frontend:** React 19, TypeScript, Tailwind CSS, Vite
- **Infra:** Docker, Nginx, Docker Compose; GitHub Actions and AWS to follow

## Developing

The containerised stack above is how the app runs in production. While developing, run only the database in Docker and the two halves natively, for hot reload:

```bash
docker compose up -d postgres
```

Requires JDK 21, Maven, and Node.

This project targets Java 21. If `mvn -version` reports a different Java version, point `JAVA_HOME` at your JDK 21 install first — on Homebrew that's `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

Postgres runs on host port `5433`, not the default `5432` — if you already have a local PostgreSQL install this avoids colliding with it. `application.yml` already points at `5433`.

Then the API, on `http://localhost:8080`:

```bash
cd backend && mvn spring-boot:run
```

And the frontend, on `http://localhost:5173`, which proxies `/api` to the backend exactly as Nginx does in production:

```bash
cd frontend && npm install && npm run dev
```

## Deploying to AWS

Infrastructure is defined in [terraform/](terraform/) and deployed by [the deploy workflow](.github/workflows/deploy.yml). [docs/deployment.md](docs/deployment.md) is the full runbook, including AWS account setup, billing alerts, and teardown.

A single EC2 instance runs both containers against managed RDS PostgreSQL. The database sits in a private subnet with no internet route, reachable only from the application's security group. Neither the instance nor GitHub Actions stores AWS credentials — the instance uses an IAM role and CI authenticates through OIDC, so all credentials are temporary and rotate automatically.

No NAT gateway or load balancer is created; at this scale both would cost more per month than everything else combined and neither is needed.

## Continuous integration

Every pull request runs [the CI workflow](.github/workflows/ci.yml): backend tests, a frontend typecheck and build, and both Docker image builds. `main` requires all three to pass before a pull request can be merged, so it stays deployable.

Integration tests run against a real PostgreSQL container in CI, not an in-memory substitute — the runner provides the Docker daemon Testcontainers needs.

## Running tests

Unit tests only — fast, no Docker needed:

```bash
mvn test
```

Unit plus integration tests. Integration tests run against a real PostgreSQL container via Testcontainers rather than an in-memory substitute, so they need Docker running:

```bash
mvn verify
```

## Database migrations

The schema is owned by Flyway, not Hibernate. Migrations live in [backend/src/main/resources/db/migration](backend/src/main/resources/db/migration) and are applied automatically on startup, in order, exactly once.

`ddl-auto` is set to `validate`, so Hibernate only checks that the entities match what the migrations produced and refuses to start if they have drifted. Adding a field to an entity without a matching migration fails at boot with a clear message rather than silently altering the database.

To change the schema, add a new file — never edit one that has already run, since Flyway records a checksum of each applied migration and will refuse to start if one changes underneath it:

```
V2__add_project_repository_url.sql
```

## Project layout

```
backend/    Spring Boot API (controller -> service -> repository -> entity)
docs/       Requirements and OpenAPI contract
frontend/   React app (Phase 7)
```

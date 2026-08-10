# DeployTrack

Application deployment and monitoring platform — track projects, deployments, and logs across environments.

**Status:** Phase 5 (deployment tracking) complete. See [docs/requirements.md](docs/requirements.md) and [docs/openapi.yaml](docs/openapi.yaml) for the full plan.

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

Tokens expire after 15 minutes. Override with `JWT_EXPIRY=8h` while developing; refresh tokens are deferred past MVP. The signing key in `application.yml` is for local development only — production supplies `JWT_SECRET` from the environment.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL
- **Frontend:** React, TypeScript, Tailwind CSS (Phase 7)
- **Infra:** Docker Compose, GitHub Actions, AWS (Phases 8-10)

## Running locally

Requires JDK 21, Maven, and Docker Desktop.

This project targets Java 21. If `mvn -version` reports a different Java version, point `JAVA_HOME` at your JDK 21 install first — on Homebrew that's `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

```bash
docker compose up -d
```

Postgres runs on host port `5433`, not the default `5432` — if you already have a local PostgreSQL install (Postgres.app, `brew services`, etc.) this avoids colliding with it. `backend/src/main/resources/application.yml` is already pointed at `5433` by default.

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. Try it:

```bash
curl http://localhost:8080/api/projects
```

## Running tests

Unit tests only — fast, no Docker needed:

```bash
mvn test
```

Unit plus integration tests. Integration tests run against a real PostgreSQL container via Testcontainers rather than an in-memory substitute, so they need Docker running:

```bash
mvn verify
```

## Project layout

```
backend/    Spring Boot API (controller -> service -> repository -> entity)
docs/       Requirements and OpenAPI contract
frontend/   React app (Phase 7)
```

# DeployTrack

Application deployment and monitoring platform — track projects, deployments, and logs across environments.

**Status:** Phase 3 (authentication) complete. See [docs/requirements.md](docs/requirements.md) and [docs/openapi.yaml](docs/openapi.yaml) for the full plan.

## Authentication

Stateless JWT. Register, log in to receive a signed token, then send it as `Authorization: Bearer <token>` on every request.

```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"you","email":"you@example.com","password":"supersecret123"}'
```

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"you@example.com","password":"supersecret123"}'
```

Self-registration always assigns the `DEVELOPER` role — a client cannot choose its own role. Promoting someone to `ADMIN` is a database operation until user management ships.

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

```bash
cd backend
mvn test
```

## Project layout

```
backend/    Spring Boot API (controller -> service -> repository -> entity)
docs/       Requirements and OpenAPI contract
frontend/   React app (Phase 7)
```

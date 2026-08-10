# DeployTrack — Phase 1 requirements

## Roles and permissions

Three roles, enforced via Spring Security + JWT claims.

| Action | VIEWER | DEVELOPER | ADMIN |
|---|---|---|---|
| View projects, deployments, logs | yes | yes | yes |
| Create a project | no | yes | yes |
| Edit a project | no | own only | any |
| Delete a project | no | own only | any |
| Trigger a deployment | no | yes | yes |
| Manage users and roles | no | no | yes |

Two independent checks guard every write:

1. **Role** — "what kind of user are you?", enforced by `@PreAuthorize` on the controller.
2. **Ownership** — "is this record yours?", enforced in the service once the entity is loaded, since a role annotation cannot see which row is being touched.

Both must pass. Skipping the second is how IDOR (insecure direct object reference) vulnerabilities happen: a correctly authenticated user with the right role simply changes the id in the URL to reach someone else's data. `ADMIN` bypasses the ownership check by design.

A brand-new registration defaults to `DEVELOPER`. Only an `ADMIN` can promote/demote roles (Phase 4+, not MVP).

## User stories

### Auth
- As a new user, I can register with a username, email, and password, so I can access the platform.
- As a registered user, I can log in and receive a JWT, so I can make authenticated requests.
- As a logged-in user, if my token expires or is invalid, I'm redirected to login with a clear message instead of seeing a broken page.
- As any authenticated user, I can see my own profile (username, email, role).

### Projects
- As a developer, I see a list of my projects, each showing a status derived from its latest deployment.
- As a developer, I can create a project with a name and description; duplicate names are rejected with an inline error.
- As a developer with no projects yet, I see an empty state that invites me to create one, not a blank grid.
- As a developer, I can search and filter projects by status.
- As an admin, I can delete a project (developers cannot).

### Deployments
- As a developer, I can trigger a deployment for a project by specifying a version and target environment (`dev`, `staging`, `production`).
- As a developer, I can view a project's deployment history, filterable by environment and status.
- As a developer, I can open a single deployment to see its full detail and logs.
- As a developer, I can watch a deployment's status update from `in progress` to `success`/`failed` without refreshing the page.
- As a viewer, I can see deployment history but cannot trigger one.

### Logs
- As a developer, I can view logs for a deployment, filterable by level (`INFO`/`WARN`/`ERROR`/`DEBUG`).
- As a developer, I can page through a large volume of logs without the page freezing (no unbounded `SELECT *`).

## Non-functional requirements

These are the things that separate "it works" from "it's built the way a team would build it" — they're contract-level decisions, not afterthoughts to bolt on later.

- **Explicit list semantics.** Every list endpoint response must let the frontend distinguish "still loading," "loaded, zero results," and "failed to load" — this is what makes empty/loading/error states possible without frontend guesswork.
- **Structured error responses.** No raw stack traces ever reach the client. A single error envelope shape is used everywhere (see `openapi.yaml`), with a separate shape for field-level validation errors so the frontend can highlight the right input.
- **Uniform auth failures.** Missing/expired/invalid JWT → `401` always. Valid JWT but wrong role → `403` always. The frontend can handle both generically (401 → redirect to login, 403 → show "you don't have access").
- **Pagination on every list endpoint** (`page`, `size`), required from day one on `deployments` and `logs` — these are the two collections that grow unbounded.
- **Idempotent-safe deployment triggering.** Triggering a deployment returns `202 Accepted` with the new deployment in `IN_PROGRESS` status; the actual state transition to `SUCCESS`/`FAILED` happens asynchronously (simulated in the MVP, real CI/CD callback later).

## Out of scope for MVP (explicitly deferred, not forgotten)

- Refresh tokens (MVP uses a short-lived access token only; login again when it expires)
- Team/multi-tenant project ownership (MVP: one flat list of projects, `created_by` tracked but no sharing model)
- Real CI/CD integration (MVP simulates deployment success/failure; wiring to an actual pipeline is a stretch goal)
- WebSocket/SSE live log streaming (MVP polls; the `logs/stream` endpoint in the API contract is marked as a stretch addition for Phase 6)

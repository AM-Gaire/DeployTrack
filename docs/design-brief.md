# DeployTrack — UI design brief

A handoff document for designing the DeployTrack frontend. The backend is built
and running; every field, state, and status listed here is real, so anything
designed against this document is implementable without changing the API.

---

## 1. What the product is

DeployTrack is a web application for engineering teams to track application
deployments and monitor their health.

A user creates **projects** (one per application). Each project has
**deployments** (a specific version shipped to a specific environment). Each
deployment produces **logs**. A **dashboard** aggregates the whole picture.

It is an internal ops tool, not a consumer product. Users are software
engineers checking "did that deploy work, and if not, why". They are looking at
this on a desktop monitor, often alongside a terminal, sometimes on a wall
display. Density and scannability matter more than delight.

**Reference products** for tone and layout: Vercel dashboard, GitHub Actions,
Netlify deploys, Grafana. Not: consumer SaaS marketing pages.

---

## 2. Users and roles

Three roles, enforced by the backend. The UI must reflect them — never show a
control that will only return a 403.

| Role | Can do |
|---|---|
| `VIEWER` | Read everything: projects, deployments, logs, dashboard. Changes nothing. |
| `DEVELOPER` | Everything a viewer can, plus create projects, trigger deployments, and edit or delete **projects they created**. |
| `ADMIN` | Everything, including editing and deleting **any** project. |

Two rules that affect design:

1. A `DEVELOPER` sees "New project" and "Deploy" buttons. A `VIEWER` must not.
2. A `DEVELOPER` sees "Edit" and "Delete" only on projects they created. On
   someone else's project those controls are absent, not disabled.

Everyone who self-registers becomes a `DEVELOPER`. There is no role picker in
the registration form.

---

## 3. Screens required

Six screens. Each one below lists its purpose, the exact data available, every
state it must handle, and the actions on it.

### 3.1 Login

**Purpose:** authenticate and receive a token.

**Fields:** email, password.

**Actions:** submit; link to register; **sign in to the demo account**.

**States:**
- Default
- Submitting (button shows progress, form disabled)
- Invalid credentials → a single message, `"Invalid email or password"`. The
  API deliberately returns the same message for an unknown email and a wrong
  password, so the design must not imply which was wrong.
- Field validation: both fields required.

**Demo access.** This is the entry point for anyone visiting the deployed app
without an account — a recruiter following a link, for instance. The login
screen needs a visible, low-friction way in: a "Try the demo" affordance that
signs the visitor into a read-only account pre-populated with realistic
projects, deployment history, and logs.

It should be obvious without dominating the form — someone who *does* have an
account still needs the normal fields to be the primary path. The demo account
has the `VIEWER` role, so once inside, every create and edit control is
correctly absent, which doubles as a demonstration of the permission model.

There is deliberately **no marketing or landing page**. DeployTrack is an
internal tool; the login screen is the front door, and the demo account is how
a stranger gets through it.

### 3.2 Register

**Purpose:** create an account.

**Fields:** username (3–50 chars), email, password (minimum 8 chars).

**Actions:** submit; link to login.

**States:**
- Default, submitting
- Field-level validation errors, shown inline beneath the offending field. The
  API returns these as a list of `{field, message}` pairs, so the design needs
  a per-field error slot, not one message at the top.
- Conflict: `"That email is already registered"` or `"That username is already
  taken"` — a form-level message.

**Note:** no role selector. Role is assigned server-side.

### 3.3 Dashboard

**Purpose:** the "is everything healthy" view. This is the landing page after
login.

**Data available** (all from one request):

| Field | Type | Notes |
|---|---|---|
| `totalProjects` | number | |
| `totalDeployments` | number | |
| `deploymentsByStatus` | `{IN_PROGRESS, SUCCESS, FAILED}` | All three keys always present, including zeros |
| `successRatePercent` | number **or null** | Null when nothing has finished yet |
| `deploymentsLast24Hours` | number | |
| `deploymentsLast7Days` | number | |
| `averageDurationSeconds` | number **or null** | Null when nothing has finished |
| `recentDeployments` | array, max 10, newest first | Each is a full deployment object |

**States:**
- Loading, error
- **Fresh account**: zero projects, zero deployments, and both `successRatePercent`
  and `averageDurationSeconds` are `null`. This must not render as "0%", which
  reads as "everything is failing". Design an explicit "no data yet" treatment.
- Populated

### 3.4 Projects list

**Purpose:** find a project and see its health at a glance.

**Per project:**

| Field | Type | Notes |
|---|---|---|
| `id` | number | |
| `name` | string, max 100 | |
| `description` | string, max 500, **may be empty** | |
| `status` | `IDLE` \| `DEPLOYING` \| `ACTIVE` \| `FAILING` | Derived from latest deployment |
| `createdBy` | `{id, username, email, role}` | |
| `latestDeployment` | deployment object **or null** | Null when the project has never deployed |
| `createdAt` | timestamp | |

**Status meanings — these are not arbitrary:**

- `IDLE` — no deployments have ever run. Distinct from `ACTIVE`; nothing is running.
- `DEPLOYING` — latest deployment is in progress right now.
- `ACTIVE` — latest deployment succeeded.
- `FAILING` — latest deployment failed.

**Controls:** search by name; filter by status; pagination (server-side,
returns `content`, `page`, `size`, `totalElements`).

**Actions:** open a project; "New project" (developer/admin only).

**States:**
- Loading, error
- **Empty — no projects at all**: an invitation to create the first one.
- **Empty — search or filter returned nothing**: this is a *different* state.
  It needs "no projects match" plus a way to clear the filters, not a "create
  your first project" prompt.
- Populated
- A project with no description, and one with `IDLE` status and
  `latestDeployment: null` — both must render sensibly.

### 3.5 Project detail

**Purpose:** one application's deployment history.

**Shows:** project name, description, derived status, who created it, and a
paginated list of its deployments.

**Per deployment in the list:**

| Field | Type | Notes |
|---|---|---|
| `id` | number | |
| `version` | string, max 50 | e.g. `2.4.1`, `3.0.0-rc1` |
| `environment` | `dev` \| `staging` \| `production` | |
| `status` | `IN_PROGRESS` \| `SUCCESS` \| `FAILED` | |
| `deployedBy` | user object | |
| `startedAt` | timestamp | |
| `completedAt` | timestamp **or null** | Null while in progress |

**Controls:** filter deployments by environment and by status; pagination.
Deployments are returned newest first.

**Actions:** open a deployment; "Deploy" (developer/admin); edit and delete the
project (owner or admin only).

**States:**
- Loading, error, project not found (404)
- **No deployments yet** — the `IDLE` case
- Populated

### 3.6 Deployment detail — the important one

**Purpose:** watch a deployment happen, or find out why one failed. This is the
screen the product exists for.

**Shows:** version, environment, status, who triggered it, start time, and
elapsed or total duration. Plus the full log stream.

**Per log line:**

| Field | Type | Notes |
|---|---|---|
| `level` | `DEBUG` \| `INFO` \| `WARN` \| `ERROR` | |
| `message` | string, **can be long** — stack traces belong here | |
| `timestamp` | timestamp | |

**Live streaming:** while a deployment is `IN_PROGRESS`, log lines arrive in
real time over a Server-Sent Events connection. The design must make this
visibly live — a static list that happens to update is indistinguishable from
a stale page. When the deployment settles, the stream sends a completion event
and closes.

**Controls:** filter logs by level; pagination for finished deployments (logs
can run to thousands of lines, so an unbounded list is not acceptable).

**States — this screen has the most:**
- Loading, error, deployment not found
- **In progress**: live indicator active, lines appending, elapsed time ticking,
  no completion time yet
- **Succeeded**: final state, total duration shown, stream closed
- **Failed**: final state, error lines must be findable quickly — this is
  someone debugging at 6pm
- **No logs yet**: a deployment that just started
- **Very long log output**: thousands of lines
- **Very long single line**: a stack trace that must not break the layout
- **Connection lost**: the SSE stream dropped and is reconnecting

---

## 4. Data reference

Real response shapes. Design against these.

```jsonc
// GET /api/projects  →  paginated
{
  "content": [
    {
      "id": 1,
      "name": "inventory-api",
      "description": "Stock levels across warehouses",
      "status": "ACTIVE",                    // IDLE | DEPLOYING | ACTIVE | FAILING
      "createdBy": {
        "id": 2, "username": "amrit",
        "email": "amrit@example.com",
        "role": "DEVELOPER"                  // ADMIN | DEVELOPER | VIEWER
      },
      "latestDeployment": {                  // null if never deployed
        "id": 42, "projectId": 1,
        "version": "2.4.1",
        "environment": "production",         // dev | staging | production
        "status": "SUCCESS",                 // IN_PROGRESS | SUCCESS | FAILED
        "deployedBy": { "id": 2, "username": "amrit", "email": "…", "role": "DEVELOPER" },
        "startedAt": "2026-08-10T15:27:10Z",
        "completedAt": "2026-08-10T15:27:14Z" // null while IN_PROGRESS
      },
      "createdAt": "2026-08-10T15:20:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 4
}

// GET /api/dashboard/stats
{
  "totalProjects": 4,
  "totalDeployments": 147,
  "deploymentsByStatus": { "IN_PROGRESS": 2, "SUCCESS": 118, "FAILED": 27 },
  "successRatePercent": 81.4,        // null when nothing has settled
  "deploymentsLast24Hours": 12,
  "deploymentsLast7Days": 63,
  "averageDurationSeconds": 4.1,     // null when nothing has settled
  "recentDeployments": [ /* up to 10 deployment objects, newest first */ ]
}

// GET /api/deployments/{id}/logs  →  paginated, oldest first
{
  "content": [
    {
      "id": 8, "deploymentId": 2,
      "level": "INFO",               // DEBUG | INFO | WARN | ERROR
      "message": "Pulling container image",
      "timestamp": "2026-08-10T16:28:05Z"
    }
  ],
  "page": 0, "size": 50, "totalElements": 6
}

// Errors — every failure uses this shape
{
  "timestamp": "2026-08-10T15:26:47Z",
  "status": 409,
  "error": "Conflict",
  "message": "A deployment to PRODUCTION is already in progress for this project",
  "path": "/api/projects/4/deployments"
}

// Validation errors add per-field detail
{
  "status": 400, "error": "Bad Request", "message": "Validation failed",
  "fieldErrors": [ { "field": "password", "message": "password must be at least 8 characters" } ]
}
```

### Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` | 201 |
| POST | `/api/auth/login` | Returns `{accessToken, expiresIn, user}` |
| GET | `/api/users/me` | Current user |
| GET | `/api/projects` | `?search=&page=&size=` |
| POST | `/api/projects` | 201, developer/admin |
| GET | `/api/projects/{id}` | |
| PUT | `/api/projects/{id}` | Owner or admin |
| DELETE | `/api/projects/{id}` | 204, owner or admin |
| GET | `/api/projects/{id}/deployments` | `?environment=&status=&page=&size=` |
| POST | `/api/projects/{id}/deployments` | **202 Accepted**, not 201 |
| GET | `/api/deployments/{id}` | |
| GET | `/api/deployments/{id}/logs` | `?level=&page=&size=` |
| GET | `/api/deployments/{id}/logs/stream` | Server-Sent Events |

### Status codes the UI must handle

| Code | Meaning | UI behaviour |
|---|---|---|
| 401 | Token missing, invalid, or expired | Redirect to login with a message |
| 403 | Authenticated, but role or ownership forbids it | "You don't have access" — do not redirect |
| 404 | Doesn't exist | Not-found state on the page |
| 409 | Conflict — duplicate name, or a deployment already running for that environment | Inline message with the server's text |
| 400 | Validation failed | Per-field inline errors |

**Tokens expire after 15 minutes.** Sessions ending mid-use is normal, not an
edge case. Design what a user sees when a request fails because their token
expired while they were reading a page.

---

## 5. Design constraints

**Must have:**

- Desktop-first. This is a tool used on a monitor. It should not break on a
  tablet, but phone layouts are not a priority.
- Light and dark themes. Engineers run dark; a dashboard that only works in
  light is half-built.
- Every screen designed in **all** its states: loading, empty, error, populated.
  A design that only shows the happy path is not finished — the states are
  where most of the work is.
- Status colour must be consistent everywhere and must never be the *only*
  signal. Pair colour with a label or icon so it survives colour blindness and
  greyscale printing.
- Long content must not break layout: 100-character project names, 500-character
  descriptions, stack traces in log lines.

**Suggested status colours** (adjust freely, keep them consistent):

| Meaning | Applies to |
|---|---|
| Success / green | deployment `SUCCESS`, project `ACTIVE` |
| Warning / amber | deployment `IN_PROGRESS`, project `DEPLOYING`, log `WARN` |
| Danger / red | deployment `FAILED`, project `FAILING`, log `ERROR` |
| Neutral / grey | project `IDLE`, log `DEBUG` |
| Info / blue | log `INFO` |

**Deliberately out of scope** — do not design these:

- Settings or profile pages
- Admin user management (no backend endpoints exist)
- **Marketing or landing pages.** This is an internal tool, not a product being
  sold. The login screen is the front door; a demo account is how a visitor
  without credentials gets in.
- Onboarding flows or tours
- Mobile-first layouts

**Copy guidance:**

- Sentence case everywhere. Not Title Case.
- Errors say what happened and what to do: "That name's already taken. Try
  another." Never "Error: constraint violation".
- Empty states are invitations, not apologies. "Create your first project", not
  "No data available".
- Timestamps: relative for recency ("2 hours ago") with the absolute value on
  hover. Durations in human units ("4.1s", "2m 14s").

---

## 6. Prompts

Paste these directly. Prompt A is the full brief; B–G are per screen.

### Prompt A — everything at once

> Design a web application UI for **DeployTrack**, an internal tool engineering
> teams use to track application deployments and monitor their health. Think
> Vercel dashboard or GitHub Actions, not a consumer SaaS product — the users
> are software engineers checking whether a deploy worked and debugging it when
> it didn't. Desktop-first, information-dense, calm.
>
> Design six screens: **login**, **register**, **dashboard**, **projects list**,
> **project detail**, and **deployment detail with a live log viewer**. There is
> no marketing or landing page — the login screen is the front door, and it needs
> a visible "Try the demo" affordance so a stranger can get into a read-only
> demo account in one click.
>
> Design each screen in all of its states — loading, empty, error, and populated.
> The states are the majority of the work; a happy-path-only design is not
> finished.
>
> Provide both light and dark themes.
>
> The domain: a user creates *projects* (one per application). Each project has
> *deployments* — a version shipped to `dev`, `staging`, or `production`. Each
> deployment is `IN_PROGRESS`, `SUCCESS`, or `FAILED`, and produces *log lines*
> at `DEBUG`, `INFO`, `WARN`, or `ERROR`. A project's own status is derived from
> its most recent deployment: `IDLE` (never deployed), `DEPLOYING`, `ACTIVE`, or
> `FAILING`.
>
> Three roles: `VIEWER` (read-only), `DEVELOPER` (creates projects, triggers
> deployments, edits only their own projects), `ADMIN` (edits anything). Never
> show a control a user isn't permitted to use.
>
> The deployment detail screen is the most important. While a deployment runs,
> log lines stream in live over Server-Sent Events. Make that visibly live — a
> static list that happens to update looks identical to a stale page.
>
> Status colour must be consistent and never the only signal; pair it with a
> label or icon.

### Prompt B — dashboard

> Design the landing screen of DeployTrack, an internal deployment monitoring
> tool for engineers. It answers "is everything healthy?" at a glance.
>
> It shows: total projects, total deployments, success rate as a percentage,
> average deployment duration in seconds, deployments in the last 24 hours and
> last 7 days, a breakdown of deployments by status (`IN_PROGRESS`, `SUCCESS`,
> `FAILED`), and a list of up to 10 most recent deployments — each with a project
> name, version, environment, and status.
>
> Design three states: loading; a **fresh account** where there is no data at all
> and both success rate and average duration are genuinely unknown (this must
> not render as "0%", which reads as total failure); and fully populated.
>
> Desktop-first, dense but calm. Light and dark themes.

### Prompt C — projects list

> Design a projects list for DeployTrack, an internal deployment monitoring tool.
> Use a dense list, not a card grid — it needs to work with fifty projects.
>
> Each row shows: project name, an optional description, a status badge, and a
> summary of the most recent deployment (version, environment, and how long ago).
> Status is one of `ACTIVE` (last deploy succeeded), `DEPLOYING` (one running
> now), `FAILING` (last deploy failed), or `IDLE` (never deployed — show "no
> deployments yet" instead of a deployment summary).
>
> Include a search field, a status filter, and pagination.
>
> Design these states: loading; **no projects at all** (invite the user to create
> their first); **search returned nothing** (a distinct state — offer to clear
> filters, do not invite them to create a first project); an error state with a
> retry; and populated.
>
> A "New project" button appears only for users allowed to create one.
>
> Desktop-first. Light and dark themes.

### Prompt D — project detail

> Design a project detail screen for DeployTrack, an internal deployment tool.
>
> The header shows the project name, description, a derived status badge, and who
> created it. Below is a paginated list of that project's deployments, newest
> first, each showing version, environment (`dev`, `staging`, `production`),
> status (`IN_PROGRESS`, `SUCCESS`, `FAILED`), who triggered it, and when.
>
> Include filters for environment and status, and a "Deploy" action for permitted
> users. Edit and delete controls appear only for the project's creator or an
> admin — absent, not disabled, for everyone else.
>
> States: loading; a project with **no deployments yet**; not found; error; and
> populated.
>
> Desktop-first. Light and dark themes.

### Prompt E — deployment detail with live logs

> Design the most important screen of DeployTrack, an internal deployment
> monitoring tool: the deployment detail view with a live log stream.
>
> The header shows the version being deployed, the target environment, the
> current status, who triggered it, and either elapsed time (while running) or
> total duration (once finished).
>
> Below is the log output. Each line has a timestamp, a level (`DEBUG`, `INFO`,
> `WARN`, `ERROR`), and a message that may be very long — stack traces appear
> here and must not break the layout. Include a filter by level.
>
> While the deployment runs, lines arrive **live** over a streaming connection.
> The design must make this unmistakably live; a list that quietly updates looks
> identical to a stale page.
>
> Design these states: still running with lines arriving; succeeded; **failed**
> (someone is debugging under pressure — error lines must be findable fast);
> just started with no logs yet; thousands of lines; and the streaming connection
> dropping and reconnecting.
>
> Desktop-first, monospaced log area, comfortable for long reading sessions.
> Light and dark themes.

### Prompt F — login and register

> Design login and registration screens for DeployTrack, an internal deployment
> monitoring tool for engineering teams. Clean and unfussy — this is a tool
> people log into daily, not a product being sold to them.
>
> Login: email and password, a submit button, and a link to register. Failed
> login shows one message, "Invalid email or password", which deliberately does
> not reveal whether the email exists.
>
> The login screen is also the app's front door for strangers — there is no
> marketing or landing page. Include a visible "Try the demo" affordance that
> signs a visitor into a read-only demo account. It must be easy to spot without
> displacing the normal login fields as the primary path.
>
> Register: username, email, and password (minimum 8 characters), with a link to
> login. Validation errors appear inline beneath each field. There is no role
> selector — roles are assigned by the server.
>
> Design default, submitting, field-error, and form-error states for both.
>
> Desktop-first. Light and dark themes.

### Prompt G — the states, as a system

> For DeployTrack, an internal deployment monitoring tool, design a consistent
> system for the four states every data-backed screen must handle:
>
> 1. **Loading** — skeleton placeholders matching the shape of the content that
>    will replace them, not a spinner.
> 2. **Empty** — an invitation with a clear action. Distinguish "nothing exists
>    yet" from "your search matched nothing"; they need different copy and
>    different actions.
> 3. **Error** — say what happened and offer a retry. Never show a raw error code
>    or stack trace.
> 4. **Session expired** — access tokens last 15 minutes, so this happens often.
>    Design what a user sees when a request fails mid-session.
>
> Show each applied to a list screen and to a detail screen so the system is
> visibly consistent. Light and dark themes.

---

## 7. Handing designs back

Whatever comes back needs to be implementable against the API above. Worth
checking before building:

- Does the login screen offer an obvious way into the demo account?
- Does every screen have a loading, empty, and error state?
- Does anything require a field the API doesn't return?
- Does anything assume a status outside the listed enums?
- Are role-restricted controls actually hidden for roles that lack permission?
- Does the deployment screen make live streaming visibly live?
- Do long values — 100-char names, 500-char descriptions, stack traces — survive?

Anything that fails those either needs a design revision or a backend change,
and it is much cheaper to find out now.

import type { DeploymentStatus, Environment, ProjectStatus } from '../api/types'

// Every status carries a dot AND a word, never colour alone. Roughly one in
// twelve men has a colour vision deficiency, and screenshots routinely end up
// greyscale in tickets -- colour on its own is a single point of failure.

const TONE = {
  ok: 'bg-ok-soft text-ok',
  warn: 'bg-warn-soft text-warn',
  bad: 'bg-bad-soft text-bad',
  idle: 'bg-idle-soft text-muted',
} as const

type Tone = keyof typeof TONE

function Pill({ tone, label }: { tone: Tone; label: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 font-mono text-[11px] font-medium tracking-wide ${TONE[tone]}`}
    >
      <span aria-hidden="true" className="size-1.5 rounded-full bg-current" />
      {label}
    </span>
  )
}

const DEPLOYMENT_TONE: Record<DeploymentStatus, Tone> = {
  IN_PROGRESS: 'warn',
  SUCCESS: 'ok',
  FAILED: 'bad',
}

const DEPLOYMENT_LABEL: Record<DeploymentStatus, string> = {
  IN_PROGRESS: 'IN PROGRESS',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
}

export function DeploymentStatusPill({ status }: { status: DeploymentStatus }) {
  return <Pill tone={DEPLOYMENT_TONE[status]} label={DEPLOYMENT_LABEL[status]} />
}

// Derived server-side from the latest deployment; IDLE means the project has
// never deployed, which is genuinely different from ACTIVE.
const PROJECT_TONE: Record<ProjectStatus, Tone> = {
  ACTIVE: 'ok',
  DEPLOYING: 'warn',
  FAILING: 'bad',
  IDLE: 'idle',
}

export function ProjectStatusPill({ status }: { status: ProjectStatus }) {
  return <Pill tone={PROJECT_TONE[status]} label={status} />
}

// Outlined rather than filled: environment is context, not state, and giving
// it a solid colour would compete with the status pill beside it.
export function EnvBadge({ environment }: { environment: Environment }) {
  return (
    <span className="inline-flex items-center rounded-full border border-line-strong px-2 py-0.5 font-mono text-[11px] text-muted">
      {environment}
    </span>
  )
}

export function LiveIndicator() {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-ok-soft px-2 py-0.5 font-mono text-[11px] text-ok">
      <span aria-hidden="true" className="size-1.5 animate-pulse rounded-full bg-current" />
      LIVE
    </span>
  )
}

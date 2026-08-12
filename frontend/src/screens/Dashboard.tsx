import { Link } from 'react-router-dom'
import { dashboard } from '../api/endpoints'
import type { DashboardStats, DeploymentStatus } from '../api/types'
import { DeploymentStatusPill, EnvBadge } from '../components/Status'
import { ErrorState, Skeleton } from '../components/states'
import { MetricCard, Panel } from '../components/ui'
import { absoluteTime, duration, timeAgo } from '../lib/format'
import { useAsync } from '../lib/useAsync'

const BAR_COLOUR: Record<DeploymentStatus, string> = {
  SUCCESS: 'bg-ok',
  FAILED: 'bg-bad',
  IN_PROGRESS: 'bg-warn',
}

const BAR_LABEL: Record<DeploymentStatus, string> = {
  SUCCESS: 'Success',
  FAILED: 'Failed',
  IN_PROGRESS: 'In progress',
}

export function Dashboard() {
  const { data, loading, error, reload } = useAsync<DashboardStats>(() => dashboard.stats(), [])

  if (loading) {
    return (
      <div className="flex flex-col gap-5">
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex flex-col gap-2 rounded-md bg-surface-2 px-3.5 py-3">
              <Skeleton className="h-2.5 w-24" />
              <Skeleton className="h-7 w-16" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (error || !data) {
    return <ErrorState message={error?.message} onRetry={reload} />
  }

  const totalSettled = data.deploymentsByStatus.SUCCESS + data.deploymentsByStatus.FAILED
  const barTotal = Math.max(data.totalDeployments, 1)

  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-xl font-semibold">Dashboard</h1>

      <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
        {/* Null renders as "No data yet" rather than 0%. Showing zero would
            tell a new user everything is failing. */}
        <MetricCard
          label="Success rate"
          value={data.successRatePercent === null ? null : `${data.successRatePercent}%`}
        />
        <MetricCard label="Deployments" value={String(data.totalDeployments)} />
        <MetricCard
          label="Avg duration"
          value={data.averageDurationSeconds === null ? null : duration(data.averageDurationSeconds)}
        />
        <MetricCard label="Last 24 hours" value={String(data.deploymentsLast24Hours)} />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <Panel title="By status">
          {totalSettled === 0 && data.deploymentsByStatus.IN_PROGRESS === 0 ? (
            <p className="px-3.5 py-6 text-center text-[13px] text-muted">
              No deployments recorded yet.
            </p>
          ) : (
            <div className="flex flex-col gap-3 p-3.5">
              {(Object.keys(BAR_LABEL) as DeploymentStatus[]).map((status) => (
                <div key={status} className="flex flex-col gap-1.5">
                  <div className="flex items-baseline justify-between">
                    <span className="font-mono text-xs text-muted">{BAR_LABEL[status]}</span>
                    <span className="font-mono text-[13px] font-semibold tabular-nums">
                      {data.deploymentsByStatus[status]}
                    </span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-surface-2">
                    <span
                      className={`block h-full rounded-full ${BAR_COLOUR[status]}`}
                      style={{ width: `${(data.deploymentsByStatus[status] / barTotal) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Panel>

        <Panel title="Recent deployments">
          {data.recentDeployments.length === 0 ? (
            <p className="px-3.5 py-6 text-center text-[13px] text-muted">
              Deployments will appear here as they run.
            </p>
          ) : (
            <ul className="divide-y divide-line">
              {data.recentDeployments.map((d) => (
                <li key={d.id}>
                  <Link
                    to={`/deployments/${d.id}`}
                    className="flex items-center gap-3 px-3.5 py-2.5 hover:bg-surface-2"
                  >
                    <DeploymentStatusPill status={d.status} />
                    <span className="min-w-0 flex-1 truncate font-mono text-[13px]">
                      {d.version}
                    </span>
                    <EnvBadge environment={d.environment} />
                    <span
                      className="hidden shrink-0 font-mono text-[11px] text-faint sm:inline"
                      title={absoluteTime(d.startedAt)}
                    >
                      {timeAgo(d.startedAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  )
}

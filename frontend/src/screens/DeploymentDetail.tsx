import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { deployments, logs } from '../api/endpoints'
import type { Deployment, LogEntry, LogLevel, Paged } from '../api/types'
import { DeploymentStatusPill, EnvBadge, LiveIndicator } from '../components/Status'
import { ErrorState, SkeletonRows } from '../components/states'
import { Banner, MetricCard, Panel } from '../components/ui'
import { absoluteTime, clockTime, elapsedSince } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { useLogStream } from '../lib/useLogStream'

const LEVEL_COLOUR: Record<LogLevel, string> = {
  DEBUG: 'text-faint',
  INFO: 'text-accent',
  WARN: 'text-warn',
  ERROR: 'text-bad',
}

export function DeploymentDetail() {
  const { deploymentId } = useParams()
  const id = Number(deploymentId)

  const [level, setLevel] = useState<LogLevel | ''>('')
  const [streamed, setStreamed] = useState<LogEntry[]>([])
  const [status, setStatus] = useState<Deployment['status'] | null>(null)
  // Ticks once a second so elapsed time on a running deployment actually
  // advances instead of freezing at whatever it was when the page loaded.
  const [, setNow] = useState(Date.now())

  const deployment = useAsync<Deployment>(() => deployments.get(id), [id])
  const history = useAsync<Paged<LogEntry>>(
    () => logs.list(id, { level: level || undefined, size: 200 }),
    [id, level],
  )

  const current = status ?? deployment.data?.status ?? null
  const isRunning = current === 'IN_PROGRESS'

  useEffect(() => {
    if (!isRunning) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [isRunning])

  const appendLog = useCallback((entry: LogEntry) => {
    setStreamed((previous) => [...previous, entry])
  }, [])

  const onComplete = useCallback(() => {
    // Refetch rather than guessing the outcome: the server knows whether it
    // succeeded or failed, and the header must agree with the log.
    deployment.reload()
  }, [deployment])

  const streamState = useLogStream(id, isRunning, appendLog, onComplete)

  useEffect(() => {
    if (deployment.data) setStatus(deployment.data.status)
  }, [deployment.data])

  if (deployment.loading) {
    return <Panel><SkeletonRows rows={3} /></Panel>
  }

  if (deployment.error || !deployment.data) {
    const notFound = deployment.error instanceof ApiError && deployment.error.isNotFound
    return (
      <ErrorState
        message={notFound ? 'That deployment does not exist.' : deployment.error?.message}
        onRetry={deployment.reload}
      />
    )
  }

  const d = deployment.data
  const historyLogs = history.data?.content ?? []
  // Streamed lines are appended to the fetched history. Filtering the streamed
  // ones client-side keeps the level filter honest for lines that arrive live.
  const visibleStreamed = level ? streamed.filter((l) => l.level === level) : streamed
  const seen = new Set(historyLogs.map((l) => l.id))
  const allLogs = [...historyLogs, ...visibleStreamed.filter((l) => !seen.has(l.id))]

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <Link
          to={`/projects/${d.projectId}`}
          className="font-mono text-xs text-faint hover:text-muted"
        >
          ← Back to project
        </Link>
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className="font-mono text-xl font-semibold">{d.version}</h1>
          <DeploymentStatusPill status={d.status} />
          <EnvBadge environment={d.environment} />
        </div>
      </div>

      {d.status === 'FAILED' && (
        <Banner tone="bad">
          Failed after {elapsedSince(d.startedAt, d.completedAt)}. The error lines below are
          highlighted.
        </Banner>
      )}

      <div className="grid gap-2.5 sm:grid-cols-3">
        <MetricCard
          label={isRunning ? 'Elapsed' : 'Duration'}
          value={elapsedSince(d.startedAt, d.completedAt)}
        />
        <MetricCard label="Environment" value={d.environment} />
        <MetricCard label="Triggered by" value={d.deployedBy.username} />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <h2 className="text-[15px] font-semibold">Logs</h2>
          {streamState === 'live' && <LiveIndicator />}
          {streamState === 'reconnecting' && (
            <span className="font-mono text-[11px] text-warn">Reconnecting…</span>
          )}
        </div>
        <select
          aria-label="Filter logs by level"
          value={level}
          onChange={(e) => setLevel(e.target.value as LogLevel | '')}
          className="rounded-md border border-line-strong bg-surface px-3 py-1.5 text-xs"
        >
          <option value="">All levels</option>
          <option value="INFO">Info</option>
          <option value="WARN">Warn</option>
          <option value="ERROR">Error</option>
          <option value="DEBUG">Debug</option>
        </select>
      </div>

      <div className="overflow-x-auto rounded-md border border-line py-2 font-mono text-xs leading-7">
        {history.loading && allLogs.length === 0 ? (
          <p className="px-3.5 py-4 text-muted">Loading logs…</p>
        ) : allLogs.length === 0 ? (
          <p className="px-3.5 py-4 text-muted">
            {isRunning ? 'Waiting for the first log line…' : 'No logs recorded for this deployment.'}
          </p>
        ) : (
          allLogs.map((entry) => (
            <div
              key={entry.id}
              // Only the failing lines are tinted. Recolouring the whole log
              // would destroy the signal someone is scrolling to find.
              className={`flex gap-2.5 whitespace-pre px-3.5 ${
                entry.level === 'ERROR' ? 'bg-bad-soft' : ''
              }`}
            >
              <span className="tabular-nums text-faint" title={absoluteTime(entry.timestamp)}>
                {clockTime(entry.timestamp)}
              </span>
              <span className={`w-12 shrink-0 ${LEVEL_COLOUR[entry.level]}`}>{entry.level}</span>
              <span>{entry.message}</span>
            </div>
          ))
        )}
        {isRunning && streamState === 'live' && (
          <div className="flex gap-2.5 px-3.5 text-faint">
            <span className="animate-pulse">▌</span>
          </div>
        )}
      </div>
    </div>
  )
}

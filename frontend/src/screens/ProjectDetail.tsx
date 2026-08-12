import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { deployments, projects } from '../api/endpoints'
import type { Deployment, DeploymentStatus, Environment, Paged, Project } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { DeploymentStatusPill, EnvBadge, ProjectStatusPill } from '../components/Status'
import { EmptyState, ErrorState, NoMatchState, SkeletonRows } from '../components/states'
import { Button, Panel } from '../components/ui'
import { absoluteTime, elapsedSince, timeAgo } from '../lib/format'
import { useAsync } from '../lib/useAsync'

const PAGE_SIZE = 20

export function ProjectDetail() {
  const { projectId } = useParams()
  const id = Number(projectId)
  const { can, isOwner } = useAuth()

  const [environment, setEnvironment] = useState<Environment | ''>('')
  const [status, setStatus] = useState<DeploymentStatus | ''>('')

  const project = useAsync<Project>(() => projects.get(id), [id])
  const history = useAsync<Paged<Deployment>>(
    () =>
      deployments.listForProject(id, {
        environment: environment || undefined,
        status: status || undefined,
        size: PAGE_SIZE,
      }),
    [id, environment, status],
  )

  if (project.loading) {
    return <Panel><SkeletonRows rows={3} /></Panel>
  }

  if (project.error) {
    const notFound = project.error instanceof ApiError && project.error.isNotFound
    return (
      <ErrorState
        message={notFound ? 'That project does not exist, or has been deleted.' : project.error.message}
        onRetry={project.reload}
      />
    )
  }

  if (!project.data) return null

  const p = project.data
  // Role permits the action; ownership permits it on this record. Both are
  // checked, mirroring ProjectService.requireCanModify on the backend.
  const canModify = can('DEVELOPER', 'ADMIN') && isOwner(p.createdBy.id)
  const isFiltered = environment !== '' || status !== ''

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <Link to="/projects" className="font-mono text-xs text-faint hover:text-muted">
          ← Projects
        </Link>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl font-semibold">{p.name}</h1>
            <ProjectStatusPill status={p.status} />
          </div>
          <div className="flex gap-2">
            {can('DEVELOPER', 'ADMIN') && <Button variant="primary">Deploy</Button>}
            {canModify && <Button>Edit</Button>}
            {canModify && <Button variant="danger">Delete</Button>}
          </div>
        </div>
        {p.description && <p className="max-w-[70ch] text-[13px] text-muted">{p.description}</p>}
        <p className="font-mono text-xs text-faint">
          Created by {p.createdBy.username} · <span title={absoluteTime(p.createdAt)}>{timeAgo(p.createdAt)}</span>
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <select
          aria-label="Filter by environment"
          value={environment}
          onChange={(e) => setEnvironment(e.target.value as Environment | '')}
          className="rounded-md border border-line-strong bg-surface px-3 py-1.5 text-[13px]"
        >
          <option value="">All environments</option>
          <option value="dev">dev</option>
          <option value="staging">staging</option>
          <option value="production">production</option>
        </select>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(e) => setStatus(e.target.value as DeploymentStatus | '')}
          className="rounded-md border border-line-strong bg-surface px-3 py-1.5 text-[13px]"
        >
          <option value="">All statuses</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="SUCCESS">Success</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>

      <Panel title="Deployments">
        {history.loading ? (
          <SkeletonRows rows={3} />
        ) : history.error ? (
          <ErrorState message={history.error.message} onRetry={history.reload} />
        ) : !history.data || history.data.content.length === 0 ? (
          isFiltered ? (
            <NoMatchState
              onClear={() => {
                setEnvironment('')
                setStatus('')
              }}
            />
          ) : (
            <EmptyState
              title="No deployments yet"
              body="Trigger a deployment to start tracking versions and logs for this project."
            />
          )
        ) : (
          <ul className="divide-y divide-line">
            {history.data.content.map((d) => (
              <li key={d.id}>
                <Link
                  to={`/deployments/${d.id}`}
                  className="flex items-center gap-3 px-3.5 py-3 hover:bg-surface-2"
                >
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-[13px] font-medium">{d.version}</span>
                      <EnvBadge environment={d.environment} />
                      <DeploymentStatusPill status={d.status} />
                    </span>
                    <span className="mt-1 block font-mono text-xs text-muted">
                      {d.deployedBy.username} ·{' '}
                      <span title={absoluteTime(d.startedAt)}>{timeAgo(d.startedAt)}</span>
                      {d.completedAt && ` · ${elapsedSince(d.startedAt, d.completedAt)}`}
                    </span>
                  </span>
                  <span aria-hidden="true" className="shrink-0 text-faint">
                    ›
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  )
}

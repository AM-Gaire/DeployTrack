import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { projects } from '../api/endpoints'
import type { Paged, Project } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ProjectForm } from '../components/ProjectForm'
import { ProjectStatusPill } from '../components/Status'
import { EmptyState, ErrorState, NoMatchState, SkeletonRows } from '../components/states'
import { Button, Panel } from '../components/ui'
import { absoluteTime, timeAgo } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { useDebounced } from '../lib/useDebounced'

const PAGE_SIZE = 20

export function Projects() {
  const { can } = useAuth()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)

  // Without debouncing, every keystroke fires a request. The race guard in
  // useAsync stops stale responses winning, but it does not stop the requests.
  const debouncedSearch = useDebounced(search, 250)

  const { data, loading, error, reload } = useAsync<Paged<Project>>(
    () => projects.list({ search: debouncedSearch, page, size: PAGE_SIZE }),
    [debouncedSearch, page],
  )

  const isFiltered = debouncedSearch.trim().length > 0
  const canCreate = can('DEVELOPER', 'ADMIN')

  function clearFilters() {
    setSearch('')
    setPage(0)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">Projects</h1>
        {/* Absent, not disabled, for a viewer. A greyed-out button invites a
            click and then explains nothing. */}
        {canCreate && (
          <Button variant="primary" onClick={() => setCreating(true)}>
            New project
          </Button>
        )}
      </div>

      <div className="relative max-w-sm">
        <svg
          aria-hidden="true"
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-faint"
          width="14"
          height="14"
          viewBox="0 0 16 16"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
        >
          <circle cx="7" cy="7" r="4.5" />
          <path d="M10.5 10.5 14 14" />
        </svg>
        <input
          type="search"
          aria-label="Search projects"
          placeholder="Search projects"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          className="w-full rounded-md border border-line-strong bg-surface py-2 pl-9 pr-3 text-[13px] placeholder:text-faint"
        />
      </div>

      <Panel>
        {loading ? (
          <SkeletonRows />
        ) : error ? (
          <ErrorState message={error.message} onRetry={reload} />
        ) : !data || data.content.length === 0 ? (
          // Two genuinely different empty states. Offering "create your first
          // project" to someone whose search missed would be wrong -- they
          // already have projects.
          isFiltered ? (
            <NoMatchState query={debouncedSearch} onClear={clearFilters} />
          ) : (
            <EmptyState
              title="Create your first project"
              body="Projects group deployments and logs by application."
              action={
                canCreate ? (
                  <Button variant="primary" onClick={() => setCreating(true)}>
                    New project
                  </Button>
                ) : undefined
              }
            />
          )
        ) : (
          <ul className="divide-y divide-line">
            {data.content.map((project) => (
              <li key={project.id}>
                <Link
                  to={`/projects/${project.id}`}
                  className="flex items-center gap-3 px-3.5 py-3 hover:bg-surface-2"
                >
                  <span className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium">{project.name}</span>
                      <ProjectStatusPill status={project.status} />
                      {/* Present only for an admin, who sees everyone's
                          projects and needs to tell them apart. */}
                      {project.createdBy && (
                        <span className="font-mono text-[11px] text-faint">
                          {project.createdBy.username}
                        </span>
                      )}
                    </span>
                    <span className="truncate font-mono text-xs text-muted">
                      {project.latestDeployment ? (
                        <span title={absoluteTime(project.latestDeployment.startedAt)}>
                          {project.latestDeployment.version} → {project.latestDeployment.environment}
                          {' · '}
                          {timeAgo(project.latestDeployment.startedAt)}
                        </span>
                      ) : (
                        'No deployments yet'
                      )}
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

      {data && data.totalElements > 0 && (
        <div className="flex items-center justify-between">
          <span className="font-mono text-xs text-faint">
            {data.content.length} of {data.totalElements} projects
          </span>
          <div className="flex gap-2">
            <Button disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <Button
              disabled={(page + 1) * PAGE_SIZE >= data.totalElements}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {creating && (
        <ProjectForm
          onClose={() => setCreating(false)}
          // Straight into the new project: the next thing anyone does after
          // creating one is deploy to it.
          onSaved={(project) => navigate(`/projects/${project.id}`)}
        />
      )}
    </div>
  )
}

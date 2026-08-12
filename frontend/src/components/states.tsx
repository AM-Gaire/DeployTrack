import type { ReactNode } from 'react'
import { Button } from './ui'

// The four states every data-backed screen has to survive. Kept as shared
// components so they cannot drift apart from one screen to the next.

export function Skeleton({ className = '' }: { className?: string }) {
  return <span aria-hidden="true" className={`block animate-pulse rounded bg-surface-2 ${className}`} />
}

export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="divide-y divide-line" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex flex-col gap-2 px-3.5 py-3.5">
          <Skeleton className="h-3 w-2/5" />
          <Skeleton className="h-2.5 w-3/5" />
        </div>
      ))}
    </div>
  )
}

function StateShell({
  icon,
  title,
  body,
  action,
}: {
  icon: ReactNode
  title: string
  body: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <div className="grid size-9 place-items-center rounded-lg bg-surface-2 text-faint">{icon}</div>
      <h3 className="text-[15px] font-semibold">{title}</h3>
      <p className="max-w-[42ch] text-[13px] text-muted">{body}</p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

const FolderIcon = (
  <svg width="17" height="17" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.4">
    <path d="M2 5.5A1.5 1.5 0 0 1 3.5 4h3l1.5 2h6A1.5 1.5 0 0 1 15.5 7.5v5A1.5 1.5 0 0 1 14 14H3.5A1.5 1.5 0 0 1 2 12.5z" />
    <path d="M8.75 8.5v3M7.25 10h3" />
  </svg>
)

const SearchIcon = (
  <svg width="17" height="17" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.4">
    <circle cx="8" cy="8" r="5" />
    <path d="M11.8 11.8 15.5 15.5" />
  </svg>
)

const AlertIcon = (
  <svg width="17" height="17" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.4">
    <circle cx="9" cy="9" r="7" />
    <path d="M9 5.5V10M9 12.5h.01" />
  </svg>
)

// "Nothing exists yet" and "your filters matched nothing" are deliberately
// separate components. Offering to create a first project to someone whose
// search simply missed is wrong -- they already have projects.
export function EmptyState({
  title,
  body,
  action,
}: {
  title: string
  body: string
  action?: ReactNode
}) {
  return <StateShell icon={FolderIcon} title={title} body={body} action={action} />
}

export function NoMatchState({ query, onClear }: { query?: string; onClear: () => void }) {
  return (
    <StateShell
      icon={SearchIcon}
      title={query ? `No projects match “${query}”` : 'No projects match those filters'}
      body="Try a different search, or clear the filters to see everything."
      action={<Button onClick={onClear}>Clear filters</Button>}
    />
  )
}

export function ErrorState({ message, onRetry }: { message?: string; onRetry: () => void }) {
  return (
    <StateShell
      icon={AlertIcon}
      title="Couldn't load this"
      // Falls back to a plain sentence rather than surfacing a raw status code
      // or stack trace, which tells the user nothing actionable.
      body={message ?? 'The server didn’t respond. Check your connection and try again.'}
      action={<Button onClick={onRetry}>Retry</Button>}
    />
  )
}

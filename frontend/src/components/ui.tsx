import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'default' | 'ghost' | 'danger'
}

const VARIANT = {
  primary: 'bg-accent border-accent text-white dark:text-[#08131A] hover:opacity-90',
  default: 'bg-surface border-line-strong text-ink hover:bg-surface-2',
  ghost: 'bg-transparent border-transparent text-muted hover:bg-surface-2',
  danger: 'bg-transparent border-line-strong text-bad hover:bg-bad-soft',
} as const

export function Button({ variant = 'default', className = '', ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center gap-1.5 rounded-md border px-3 py-1.5 text-[13px] font-medium transition-opacity disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT[variant]} ${className}`}
    />
  )
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
}

export function Field({ label, error, className = '', id, ...props }: FieldProps) {
  const inputId = id ?? props.name
  const errorId = error ? `${inputId}-error` : undefined
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={inputId} className="text-xs font-medium text-muted">
          {label}
        </label>
      )}
      <input
        id={inputId}
        // Screen readers announce the error alongside the field rather than
        // leaving it as unassociated text nearby.
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        {...props}
        className={`w-full rounded-md border bg-surface px-3 py-2 text-[13px] text-ink placeholder:text-faint ${
          error ? 'border-bad' : 'border-line-strong'
        } ${className}`}
      />
      {error && (
        <span id={errorId} className="text-xs text-bad">
          {error}
        </span>
      )}
    </div>
  )
}

export function Panel({
  title,
  action,
  children,
}: {
  title?: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="overflow-hidden rounded-md border border-line">
      {title && (
        <div className="flex items-center justify-between gap-3 border-b border-line bg-surface-2 px-3.5 py-2.5">
          <h2 className="text-[13px] font-semibold">{title}</h2>
          {action}
        </div>
      )}
      {children}
    </div>
  )
}

export function MetricCard({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md bg-surface-2 px-3.5 py-3">
      <span className="font-mono text-[11px] uppercase tracking-wider text-muted">{label}</span>
      {value === null ? (
        // Null is not zero. The API returns null for success rate and average
        // duration until a deployment settles; rendering 0% would claim
        // everything is failing, which is the opposite of the truth.
        <span className="text-[15px] text-faint">No data yet</span>
      ) : (
        <span className="font-mono text-2xl font-semibold tabular-nums tracking-tight">{value}</span>
      )}
    </div>
  )
}

export function Banner({
  tone,
  children,
}: {
  tone: 'bad' | 'warn' | 'ok'
  children: ReactNode
}) {
  const tones = {
    bad: 'bg-bad-soft text-bad',
    warn: 'bg-warn-soft text-warn',
    ok: 'bg-ok-soft text-ok',
  }
  return (
    <div role="alert" className={`rounded-md px-3 py-2.5 text-[13px] ${tones[tone]}`}>
      {children}
    </div>
  )
}

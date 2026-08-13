// Relative time for recency, with the absolute value available on hover via a
// title attribute. Engineers reading a deployment list want "2m ago" at a
// glance but the exact timestamp when correlating with another system.

const UNITS: [limit: number, divisor: number, name: Intl.RelativeTimeFormatUnit][] = [
  [60, 1, 'second'],
  [3600, 60, 'minute'],
  [86400, 3600, 'hour'],
  [604800, 86400, 'day'],
  [2629800, 604800, 'week'],
  [31557600, 2629800, 'month'],
  [Infinity, 31557600, 'year'],
]

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

export function timeAgo(iso: string): string {
  const seconds = (Date.now() - new Date(iso).getTime()) / 1000
  if (seconds < 45) return 'just now'
  for (const [limit, divisor, unit] of UNITS) {
    if (Math.abs(seconds) < limit) {
      return relative.format(-Math.round(seconds / divisor), unit)
    }
  }
  return 'just now'
}

export function absoluteTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  })
}

export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

// Durations in human units. Anything reaching the screen is rounded -- raw
// float division produces values like 4.100000000000001.
export function duration(seconds: number): string {
  if (seconds < 60) return `${seconds.toFixed(1)}s`
  const mins = Math.floor(seconds / 60)
  const secs = Math.round(seconds % 60)
  return `${mins}m ${secs}s`
}

export function elapsedSince(iso: string, until?: string | null): string {
  const end = until ? new Date(until).getTime() : Date.now()
  return duration(Math.max(0, (end - new Date(iso).getTime()) / 1000))
}

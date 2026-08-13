import { useEffect, useState } from 'react'

// Delays a rapidly-changing value so a search box does not fire a request per
// keystroke. 250ms is short enough to feel immediate and long enough to
// collapse a burst of typing into one request.
export function useDebounced<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}

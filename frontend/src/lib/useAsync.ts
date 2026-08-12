import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'

interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: ApiError | Error | null
  reload: () => void
}

// One place that owns loading/error/data, so no screen invents its own
// slightly-different version and forgets a state.
export function useAsync<T>(fetcher: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | Error | null>(null)
  const [nonce, setNonce] = useState(0)

  // Guards against a slow earlier request resolving after a newer one and
  // overwriting fresher data -- the classic race when a user types in a
  // search box faster than the network responds.
  const requestId = useRef(0)

  useEffect(() => {
    const id = ++requestId.current
    setLoading(true)
    setError(null)

    fetcher()
      .then((result) => {
        if (id === requestId.current) setData(result)
      })
      .catch((err) => {
        if (id !== requestId.current) return
        // A 401 has already been handled centrally by the API client, which
        // clears the session and routes to login. Surfacing an error card
        // underneath that redirect would just flash confusing text.
        if (err instanceof ApiError && err.status === 401) return
        setError(err instanceof Error ? err : new Error(String(err)))
      })
      .finally(() => {
        if (id === requestId.current) setLoading(false)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  const reload = useCallback(() => setNonce((n) => n + 1), [])

  return { data, loading, error, reload }
}

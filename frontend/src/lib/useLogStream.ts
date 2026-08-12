import { useEffect, useRef, useState } from 'react'
import { tokenStore } from '../api/client'
import { logs } from '../api/endpoints'
import type { LogEntry } from '../api/types'

export type StreamState = 'connecting' | 'live' | 'closed' | 'reconnecting'

// Consumes the SSE stream with fetch rather than EventSource.
//
// EventSource is the obvious API for server-sent events, but it cannot set
// request headers -- there is no way to send Authorization: Bearer with it.
// The usual workaround is putting the token in the query string, which is a
// bad trade: URLs land in server access logs, browser history, and referrer
// headers, so the token leaks into places nobody is auditing.
//
// fetch can carry the header, at the cost of parsing the wire format by hand
// and reimplementing reconnection. That is the right side of the trade.
export function useLogStream(
  deploymentId: number,
  enabled: boolean,
  onLog: (entry: LogEntry) => void,
  onComplete: () => void,
) {
  const [state, setState] = useState<StreamState>(enabled ? 'connecting' : 'closed')

  // Held in refs so a re-render caused by an arriving log line does not tear
  // down and rebuild the connection.
  const onLogRef = useRef(onLog)
  const onCompleteRef = useRef(onComplete)
  onLogRef.current = onLog
  onCompleteRef.current = onComplete

  useEffect(() => {
    if (!enabled) {
      setState('closed')
      return
    }

    const controller = new AbortController()
    let cancelled = false
    let attempt = 0

    async function connect() {
      try {
        setState(attempt === 0 ? 'connecting' : 'reconnecting')

        const response = await fetch(logs.streamUrl(deploymentId), {
          headers: { Authorization: `Bearer ${tokenStore.get() ?? ''}` },
          signal: controller.signal,
        })

        if (!response.ok || !response.body) {
          throw new Error(`Stream failed with ${response.status}`)
        }

        setState('live')
        attempt = 0

        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
        let buffer = ''

        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += value

          // Events are separated by a blank line. Anything after the last
          // separator is a partial event and stays buffered until the rest
          // of it arrives -- chunk boundaries do not respect message
          // boundaries.
          const parts = buffer.split('\n\n')
          buffer = parts.pop() ?? ''

          for (const part of parts) {
            handleEvent(part)
          }
        }

        // The server closes the stream once the deployment settles.
        if (!cancelled) setState('closed')
      } catch (error) {
        if (cancelled || controller.signal.aborted) return

        // Back off rather than hammering a server that may be restarting,
        // capped so a long outage does not push the retry hours away.
        attempt += 1
        const delay = Math.min(1000 * 2 ** (attempt - 1), 15000)
        setState('reconnecting')
        setTimeout(() => {
          if (!cancelled) void connect()
        }, delay)
      }
    }

    function handleEvent(raw: string) {
      let event = 'message'
      const dataLines: string[] = []

      for (const line of raw.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      }

      if (dataLines.length === 0) return

      try {
        const payload = JSON.parse(dataLines.join('\n'))
        if (event === 'log') {
          onLogRef.current(payload as LogEntry)
        } else if (event === 'deployment-complete') {
          onCompleteRef.current()
          setState('closed')
        }
      } catch {
        // A malformed frame is not worth tearing the stream down for.
      }
    }

    void connect()

    return () => {
      cancelled = true
      controller.abort()
    }
  }, [deploymentId, enabled])

  return state
}

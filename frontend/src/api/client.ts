import type { FieldError } from './types'

const TOKEN_KEY = 'deploytrack.token'

// Stored in localStorage so a refresh does not log the user out.
//
// The trade-off is real and worth naming: any script running on this page can
// read localStorage, so a single XSS hole leaks the token. The genuinely safe
// answer is an httpOnly cookie the browser attaches automatically and
// JavaScript cannot touch -- but that needs the backend to set and read
// cookies and to re-enable CSRF protection, which the header-based API
// deliberately does not use. Documented in the README rather than pretended
// away.
export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// Thrown for every non-2xx response so callers can branch on status rather
// than parsing messages.
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: FieldError[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }

  // A 403 means the caller is authenticated but not permitted. Distinct from
  // 401, which means the session is gone -- the UI reacts very differently.
  get isForbidden() {
    return this.status === 403
  }

  get isNotFound() {
    return this.status === 404
  }

  // Duplicate name, or a deployment already running for that environment.
  get isConflict() {
    return this.status === 409
  }

  fieldError(field: string) {
    return this.fieldErrors.find((e) => e.field === field)?.message
  }
}

// Notified when the server rejects the session, so AuthContext can clear state
// and route to login from one place. Without this, every screen would need its
// own expired-token handling.
type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler = () => {}
export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler
}

interface RequestOptions {
  method?: string
  body?: unknown
  // Login and register must not trigger the expired-session redirect: a wrong
  // password is a 401 too, and bouncing the user off the login page they are
  // already on would be nonsense.
  skipAuthRedirect?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, skipAuthRedirect = false } = options
  const token = tokenStore.get()

  const response = await fetch(path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })

  if (response.status === 401 && !skipAuthRedirect) {
    // Tokens last 15 minutes, so this is routine rather than exceptional.
    tokenStore.clear()
    onUnauthorized()
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  // 204 No Content has no body to parse.
  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json()
    return new ApiError(
      response.status,
      body.message ?? 'Something went wrong.',
      body.fieldErrors ?? [],
    )
  } catch {
    // A non-JSON error body means something failed before reaching the API --
    // a proxy, or the backend being down entirely.
    return new ApiError(response.status, 'The server could not be reached.')
  }
}

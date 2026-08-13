import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Banner, Button, Field } from '../components/ui'
import { Logo } from '../components/Logo'

// Credentials for the seeded read-only demo workspace. Not a secret -- the
// whole point is that a visitor can use them, and the account cannot change
// anything because it holds the VIEWER role.
const DEMO_EMAIL = 'demo@deploytrack.dev'
const DEMO_PASSWORD = 'demo-readonly'

export function Login() {
  const { login, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Return the user to whatever they were trying to reach before their session
  // ended, rather than dumping everyone on the dashboard.
  const returnTo = (location.state as { from?: string } | null)?.from ?? '/'

  async function signIn(withEmail: string, withPassword: string) {
    setError(null)
    setSubmitting(true)
    try {
      await login(withEmail, withPassword)
      navigate(returnTo, { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? // One message for both a wrong password and an unknown email. The
            // API deliberately does not reveal which accounts exist, and the
            // UI must not undo that.
            'Invalid email or password.'
          : 'Could not sign in. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void signIn(email, password)
  }

  return (
    <div className="grid min-h-dvh place-items-center px-6 py-12">
      <div className="w-full max-w-[320px]">
        <Logo className="mb-7" />

        <h1 className="text-xl font-semibold">Sign in</h1>
        <p className="mt-1 text-[13px] text-muted">
          Track every deployment across your environments.
        </p>

        {sessionExpired && (
          <div className="mt-4">
            <Banner tone="warn">Your session expired. Sign in to continue.</Banner>
          </div>
        )}

        {error && (
          <div className="mt-4">
            <Banner tone="bad">{error}</Banner>
          </div>
        )}

        <form onSubmit={onSubmit} className="mt-5 flex flex-col gap-3.5">
          <Field
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            required
            placeholder="name@company.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Field
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>

        <div className="my-5 flex items-center gap-2.5 font-mono text-[11px] text-faint">
          <span className="h-px flex-1 bg-line" />
          OR
          <span className="h-px flex-1 bg-line" />
        </div>

        <div className="flex flex-col gap-2 rounded-md border border-dashed border-line-strong px-3.5 py-3">
          <p className="text-xs text-muted">
            No account? Explore a read-only workspace with real deployment history.
          </p>
          <Button disabled={submitting} onClick={() => void signIn(DEMO_EMAIL, DEMO_PASSWORD)}>
            Continue to the demo
          </Button>
        </div>

        <p className="mt-5 text-center text-xs text-muted">
          Need an account?{' '}
          <Link to="/register" className="text-accent">
            Register
          </Link>
        </p>
      </div>
    </div>
  )
}

import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { auth } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { Banner, Button, Field } from '../components/ui'
import { Logo } from '../components/Logo'

export function Register() {
  const { login } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  // Per-field messages come back from the API as a fieldErrors array, so they
  // are rendered beneath the offending input rather than lumped into one
  // banner the user has to map back to a field themselves.
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await auth.register(username, email, password)
      // Sign straight in. Making someone who just typed their credentials
      // type them again is pointless friction.
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(err.fieldErrors.map((e) => [e.field, e.message])))
      } else if (err instanceof ApiError && err.isConflict) {
        // The server says exactly which of email or username is taken.
        setFormError(err.message)
      } else {
        setFormError('Could not create your account. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-dvh place-items-center px-6 py-12">
      <div className="w-full max-w-[320px]">
        <Logo className="mb-7" />

        <h1 className="text-xl font-semibold">Create an account</h1>
        <p className="mt-1 text-[13px] text-muted">
          You'll be able to create projects and trigger deployments.
        </p>

        {formError && (
          <div className="mt-4">
            <Banner tone="bad">{formError}</Banner>
          </div>
        )}

        <form onSubmit={onSubmit} className="mt-5 flex flex-col gap-3.5">
          <Field
            label="Username"
            name="username"
            autoComplete="username"
            required
            minLength={3}
            maxLength={50}
            placeholder="amrit"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            error={fieldErrors.username}
          />
          <Field
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            required
            placeholder="name@company.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
          />
          <Field
            label="Password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
          />
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </Button>
        </form>

        <p className="mt-5 text-center text-xs text-muted">
          Already have an account?{' '}
          <Link to="/login" className="text-accent">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}

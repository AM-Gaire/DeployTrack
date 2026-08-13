import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { deployments } from '../api/endpoints'
import type { Deployment, Environment } from '../api/types'
import { Modal } from './Modal'
import { Banner, Button, Field } from './ui'

export function DeployForm({
  projectId,
  onClose,
  onTriggered,
}: {
  projectId: number
  onClose: () => void
  onTriggered: (deployment: Deployment) => void
}) {
  const [version, setVersion] = useState('')
  const [environment, setEnvironment] = useState<Environment>('staging')
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      // Returns 202 Accepted -- the deployment has started, not finished.
      const deployment = await deployments.trigger(projectId, version, environment)
      onTriggered(deployment)
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(err.fieldErrors.map((e) => [e.field, e.message])))
      } else if (err instanceof ApiError && err.isConflict) {
        // Only one deployment may run per project and environment at a time.
        // The server's message names the environment, so it is shown verbatim
        // rather than replaced with something vaguer.
        setFormError(err.message)
      } else if (err instanceof ApiError && err.isForbidden) {
        setFormError('Your role does not permit triggering deployments.')
      } else {
        setFormError('Could not start the deployment. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Trigger deployment" onClose={onClose}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3.5">
        {formError && <Banner tone="bad">{formError}</Banner>}

        <Field
          label="Version"
          name="version"
          required
          maxLength={50}
          placeholder="2.4.1"
          value={version}
          onChange={(e) => setVersion(e.target.value)}
          error={fieldErrors.version}
        />

        <div className="flex flex-col gap-1.5">
          <label htmlFor="environment" className="text-xs font-medium text-muted">
            Environment
          </label>
          <select
            id="environment"
            name="environment"
            value={environment}
            onChange={(e) => setEnvironment(e.target.value as Environment)}
            className="w-full rounded-md border border-line-strong bg-surface px-3 py-2 text-[13px] text-ink"
          >
            <option value="dev">dev</option>
            <option value="staging">staging</option>
            <option value="production">production</option>
          </select>
          {/* Production is not the default. Making the riskiest target require
              a deliberate selection is cheap insurance against a mis-click. */}
          {environment === 'production' && (
            <span className="text-xs text-warn">This deploys to production.</span>
          )}
        </div>

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? 'Starting…' : 'Deploy'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

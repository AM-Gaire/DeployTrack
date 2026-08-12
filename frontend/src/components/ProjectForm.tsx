import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { projects } from '../api/endpoints'
import type { Project } from '../api/types'
import { Modal } from './Modal'
import { Banner, Button, Field } from './ui'

// Used for both create and edit. The two differ only in which endpoint they
// call and what the fields start as, so splitting them would duplicate all the
// error handling for no gain.
export function ProjectForm({
  project,
  onClose,
  onSaved,
}: {
  project?: Project
  onClose: () => void
  onSaved: (project: Project) => void
}) {
  const editing = project !== undefined

  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const saved = editing
        ? await projects.update(project.id, name, description)
        : await projects.create(name, description)
      onSaved(saved)
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(err.fieldErrors.map((e) => [e.field, e.message])))
      } else if (err instanceof ApiError && err.isConflict) {
        // The backend rejects a duplicate name with 409 and an explanatory
        // message. Attaching it to the name field points at the actual
        // problem instead of showing a banner the user has to interpret.
        setFieldErrors({ name: err.message })
      } else if (err instanceof ApiError && err.isForbidden) {
        setFormError('You can only edit projects you created.')
      } else {
        setFormError('Could not save the project. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={editing ? 'Edit project' : 'New project'} onClose={onClose}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3.5">
        {formError && <Banner tone="bad">{formError}</Banner>}

        <Field
          label="Name"
          name="name"
          required
          maxLength={100}
          placeholder="inventory-api"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name}
        />

        <div className="flex flex-col gap-1.5">
          <label htmlFor="description" className="text-xs font-medium text-muted">
            Description
          </label>
          <textarea
            id="description"
            name="description"
            rows={3}
            maxLength={500}
            placeholder="What this application does"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full resize-y rounded-md border border-line-strong bg-surface px-3 py-2 text-[13px] text-ink placeholder:text-faint"
          />
          {fieldErrors.description && (
            <span className="text-xs text-bad">{fieldErrors.description}</span>
          )}
        </div>

        <div className="mt-1 flex justify-end gap-2">
          <Button type="button" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? 'Saving…' : editing ? 'Save changes' : 'Create project'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

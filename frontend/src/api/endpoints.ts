import { request } from './client'
import type {
  DashboardStats,
  Deployment,
  DeploymentStatus,
  Environment,
  LogEntry,
  LoginResponse,
  LogLevel,
  Paged,
  Project,
  UserSummary,
} from './types'

// One place where every URL lives. Screens call these rather than building
// paths inline, so a route change is a single edit.

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export const auth = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
      skipAuthRedirect: true,
    }),

  register: (username: string, email: string, password: string) =>
    request<UserSummary>('/api/auth/register', {
      method: 'POST',
      body: { username, email, password },
      skipAuthRedirect: true,
    }),

  me: () => request<UserSummary>('/api/users/me'),
}

export const projects = {
  list: (params: { search?: string; page?: number; size?: number } = {}) =>
    request<Paged<Project>>(`/api/projects${query(params)}`),

  get: (id: number) => request<Project>(`/api/projects/${id}`),

  create: (name: string, description: string) =>
    request<Project>('/api/projects', { method: 'POST', body: { name, description } }),

  update: (id: number, name: string, description: string) =>
    request<Project>(`/api/projects/${id}`, { method: 'PUT', body: { name, description } }),

  remove: (id: number) => request<void>(`/api/projects/${id}`, { method: 'DELETE' }),
}

export const deployments = {
  listForProject: (
    projectId: number,
    params: {
      environment?: Environment
      status?: DeploymentStatus
      page?: number
      size?: number
    } = {},
  ) => request<Paged<Deployment>>(`/api/projects/${projectId}/deployments${query(params)}`),

  // Returns 202 Accepted -- the deployment record exists but the work has not
  // finished. Callers must not treat this as "deployed".
  trigger: (projectId: number, version: string, environment: Environment) =>
    request<Deployment>(`/api/projects/${projectId}/deployments`, {
      method: 'POST',
      body: { version, environment },
    }),

  get: (id: number) => request<Deployment>(`/api/deployments/${id}`),
}

export const logs = {
  list: (
    deploymentId: number,
    params: { level?: LogLevel; page?: number; size?: number } = {},
  ) => request<Paged<LogEntry>>(`/api/deployments/${deploymentId}/logs${query(params)}`),

  streamUrl: (deploymentId: number) => `/api/deployments/${deploymentId}/logs/stream`,
}

// Mirrors the backend DTOs in com.deploytrack.dto. Kept deliberately close to
// the Java records so a change on either side shows up as a type error rather
// than a runtime surprise.

export type Role = 'ADMIN' | 'DEVELOPER' | 'VIEWER'
export type DeploymentStatus = 'IN_PROGRESS' | 'SUCCESS' | 'FAILED'
export type ProjectStatus = 'IDLE' | 'DEPLOYING' | 'ACTIVE' | 'FAILING'
export type Environment = 'dev' | 'staging' | 'production'
export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

export interface UserSummary {
  id: number
  username: string
  email: string
  role: Role
}

export interface Deployment {
  id: number
  projectId: number
  version: string
  environment: Environment
  status: DeploymentStatus
  deployedBy: UserSummary
  startedAt: string
  // Null while IN_PROGRESS. The type says so, which forces every caller to
  // handle a deployment that has not finished.
  completedAt: string | null
}

export interface Project {
  id: number
  name: string
  description: string | null
  status: ProjectStatus
  // Only sent to an admin. A developer sees nothing but their own projects,
  // and a viewer has no reason to know who owns what, so the field is omitted
  // rather than sent and hidden -- anything sent is visible in the network tab.
  createdBy: UserSummary | null
  // Null when the project has never deployed -- the IDLE case.
  latestDeployment: Deployment | null
  createdAt: string
}

export interface LogEntry {
  id: number
  deploymentId: number
  level: LogLevel
  message: string
  timestamp: string
}

export interface DashboardStats {
  totalProjects: number
  totalDeployments: number
  deploymentsByStatus: Record<DeploymentStatus, number>
  // Null, not zero, until something has settled. Rendering null as 0 would
  // claim everything is failing.
  successRatePercent: number | null
  deploymentsLast24Hours: number
  deploymentsLast7Days: number
  averageDurationSeconds: number | null
  recentDeployments: Deployment[]
}

export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: UserSummary
}

export interface FieldError {
  field: string
  message: string
}

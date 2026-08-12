import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'
import { Button } from './ui'

function initials(username: string) {
  return username.slice(0, 2).toUpperCase()
}

export function AppShell() {
  const { user, logout } = useAuth()

  const link = ({ isActive }: { isActive: boolean }) =>
    `border-b-2 py-3.5 text-[13px] transition-colors ${
      isActive ? 'border-accent font-medium text-ink' : 'border-transparent text-muted hover:text-ink'
    }`

  return (
    <div className="min-h-dvh">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-5">
          <div className="flex items-center gap-6">
            <Logo />
            <nav className="flex items-center gap-5">
              <NavLink to="/" end className={link}>
                Dashboard
              </NavLink>
              <NavLink to="/projects" className={link}>
                Projects
              </NavLink>
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {user && (
              <div className="flex items-center gap-2">
                <span
                  aria-hidden="true"
                  className="grid size-6 place-items-center rounded-full bg-accent-soft font-mono text-[10px] font-semibold text-accent"
                >
                  {initials(user.username)}
                </span>
                <span className="hidden text-xs text-muted sm:inline">
                  {user.username}
                  {/* Role is shown because it explains why controls are or are
                      not present -- a viewer who cannot see a Deploy button
                      should know why. */}
                  <span className="ml-1.5 font-mono text-[10px] text-faint">{user.role}</span>
                </span>
              </div>
            )}
            <Button variant="ghost" onClick={logout}>
              Sign out
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-5 py-6">
        <Outlet />
      </main>
    </div>
  )
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { setUnauthorizedHandler, tokenStore } from '../api/client'
import { auth } from '../api/endpoints'
import type { Role, UserSummary } from '../api/types'

interface AuthState {
  user: UserSummary | null
  // Distinguishes "still checking the stored token" from "definitely signed
  // out". Without it the app flashes the login screen on every refresh before
  // the session is confirmed.
  loading: boolean
  sessionExpired: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
  can: (...roles: Role[]) => boolean
  isOwner: (ownerId: number) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)

  // The API client calls this whenever the server rejects the session, so
  // expiry is handled once here rather than in every screen that fetches.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      setSessionExpired(true)
    })
  }, [])

  // A token in storage proves nothing -- it may have expired while the tab was
  // closed. Ask the server who it belongs to before trusting it.
  useEffect(() => {
    const token = tokenStore.get()
    if (!token) {
      setLoading(false)
      return
    }
    auth
      .me()
      .then(setUser)
      .catch(() => {
        tokenStore.clear()
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const response = await auth.login(email, password)
    tokenStore.set(response.accessToken)
    setSessionExpired(false)
    setUser(response.user)
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setSessionExpired(false)
    setUser(null)
  }, [])

  // Role and ownership checks live here so screens never reimplement the
  // permission rules -- and never disagree with the backend about them.
  const can = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user])

  // Mirrors ProjectService.requireCanModify: the owner, or any admin.
  const isOwner = useCallback(
    (ownerId: number) => !!user && (user.role === 'ADMIN' || user.id === ownerId),
    [user],
  )

  const value = useMemo(
    () => ({ user, loading, sessionExpired, login, logout, can, isOwner }),
    [user, loading, sessionExpired, login, logout, can, isOwner],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

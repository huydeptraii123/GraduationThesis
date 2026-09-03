import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import * as authApi from './authApi'
import { clearStoredAuth, getStoredAuth, setStoredAuth } from './authStorage'

interface AuthUser {
  username: string
  role: string
}

interface AuthContextValue {
  user: AuthUser | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const stored = getStoredAuth()
    return stored ? { username: stored.username, role: stored.role } : null
  })
  const logoutTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const logout = useCallback(() => {
    clearTimeout(logoutTimer.current)
    clearStoredAuth()
    setUser(null)
  }, [])

  const scheduleAutoLogout = useCallback((expiresAt: number) => {
    clearTimeout(logoutTimer.current)
    logoutTimer.current = setTimeout(logout, Math.max(0, expiresAt - Date.now()))
  }, [logout])

  useEffect(() => {
    const stored = getStoredAuth()
    if (stored) {
      scheduleAutoLogout(stored.expiresAt)
    }
    return () => clearTimeout(logoutTimer.current)
  }, [scheduleAutoLogout])

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await authApi.login({ username, password })
      const expiresAt = Date.now() + response.expiresInMs
      setStoredAuth({ token: response.token, username: response.username, role: response.role, expiresAt })
      setUser({ username: response.username, role: response.role })
      scheduleAutoLogout(expiresAt)
    },
    [scheduleAutoLogout],
  )

  return <AuthContext.Provider value={{ user, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

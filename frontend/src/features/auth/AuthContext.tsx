import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import * as authApi from './authApi'
import { parseRole, type Role } from './permissions'
import { clearStoredAuth, getStoredAuth, setStoredAuth } from './authStorage'
import { clearLastSimulation } from '../dashboard/simulationStore'

interface AuthUser {
  username: string
  /** Backend trả về chuỗi; thu hẹp về union ngay tại biên để mọi nơi tra bảng quyền được kiểm kiểu. */
  role: Role
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
    if (!stored) {
      return null
    }
    // Phiên cũ trong localStorage có thể mang mã vai trò không còn tồn tại — coi như chưa đăng nhập
    // thay vì dựng một người dùng có vai trò vô nghĩa.
    const role = parseRole(stored.role)
    if (!role) {
      clearStoredAuth()
      return null
    }
    return { username: stored.username, role }
  })
  const logoutTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const logout = useCallback(() => {
    clearTimeout(logoutTimer.current)
    clearStoredAuth()
    // Đăng xuất chỉ đổi trạng thái trong cùng một trang, không tải lại trình duyệt — nên mọi bộ
    // nhớ cấp module vẫn nguyên vẹn. Không xóa ở đây thì người đăng nhập kế tiếp mở trang chủ sẽ
    // thấy nguyên số liệu đơn hàng của phiên trước.
    clearLastSimulation()
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
      const role = parseRole(response.role)
      if (!role) {
        throw new Error(`Tài khoản đang mang vai trò không được hỗ trợ (${response.role}).`)
      }
      const expiresAt = Date.now() + response.expiresInMs
      setStoredAuth({ token: response.token, username: response.username, role, expiresAt })
      setUser({ username: response.username, role })
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

export interface StoredAuth {
  token: string
  username: string
  role: string
  expiresAt: number
}

const STORAGE_KEY = 'slatcut.auth'

export function getStoredAuth(): StoredAuth | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null

  let auth: StoredAuth
  try {
    auth = JSON.parse(raw) as StoredAuth
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }

  if (auth.expiresAt <= Date.now()) {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
  return auth
}

export function setStoredAuth(auth: StoredAuth): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(auth))
}

export function clearStoredAuth(): void {
  localStorage.removeItem(STORAGE_KEY)
}

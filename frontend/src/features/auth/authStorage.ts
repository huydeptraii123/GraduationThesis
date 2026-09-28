export interface StoredAuth {
  token: string
  username: string
  role: string
  expiresAt: number
}

const STORAGE_KEY = 'slatcut.auth'

/**
 * "Ghi nhớ đăng nhập" quyết định phiên nằm ở đâu: `localStorage` dùng chung mọi tab và sống qua lần
 * đóng trình duyệt (tới khi token hết hạn), `sessionStorage` là của RIÊNG một tab và mất khi đóng tab
 * đó. Ghi vào một nơi thì xóa nơi kia để không còn token cũ nằm lại sau khi đăng xuất.
 *
 * Đọc `sessionStorage` TRƯỚC: lựa chọn riêng của tab này phải thắng phiên dùng chung. Đọc ngược lại
 * thì tab B đăng nhập `planner` không ghi nhớ sẽ âm thầm gửi token của `admin` vừa đăng nhập có ghi
 * nhớ ở tab A — giao diện vẫn hiện `planner` nhưng mọi request chạy bằng quyền ADMIN.
 */
const STORES = [sessionStorage, localStorage]

function readFrom(store: Storage): StoredAuth | null {
  const raw = store.getItem(STORAGE_KEY)
  if (!raw) return null

  let auth: StoredAuth
  try {
    auth = JSON.parse(raw) as StoredAuth
  } catch {
    store.removeItem(STORAGE_KEY)
    return null
  }

  if (auth.expiresAt <= Date.now()) {
    store.removeItem(STORAGE_KEY)
    return null
  }
  return auth
}

export function getStoredAuth(): StoredAuth | null {
  for (const store of STORES) {
    const auth = readFrom(store)
    if (auth) return auth
  }
  return null
}

export function setStoredAuth(auth: StoredAuth, remember = true): void {
  const [target, other] = remember ? [localStorage, sessionStorage] : [sessionStorage, localStorage]
  other.removeItem(STORAGE_KEY)
  target.setItem(STORAGE_KEY, JSON.stringify(auth))
}

export function clearStoredAuth(): void {
  STORES.forEach((store) => store.removeItem(STORAGE_KEY))
}

import { httpClient } from '../../api/httpClient'
import type { Page, PageParams } from '../../api/pagination'
import type { Role } from '../auth/permissions'
import type {
  ChangePasswordRequest,
  CreateUserRequest,
  ResetPasswordRequest,
  UpdateUserRequest,
  UserResponse,
} from './types'

const USERS_URL = '/api/v1/users'

export interface UserFilterParams extends PageParams {
  keyword?: string
  roleCode?: Role | null
  /** Bỏ trống là không lọc; true là còn hiệu lực, false là đã khóa. */
  enabled?: boolean | null
}

export function listUsers(params: UserFilterParams): Promise<Page<UserResponse>> {
  return httpClient.get<Page<UserResponse>>(USERS_URL, { params }).then((res) => res.data)
}

export function getUser(id: number): Promise<UserResponse> {
  return httpClient.get<UserResponse>(`${USERS_URL}/${id}`).then((res) => res.data)
}

export function createUser(payload: CreateUserRequest): Promise<UserResponse> {
  return httpClient.post<UserResponse>(USERS_URL, payload).then((res) => res.data)
}

export function updateUser(id: number, payload: UpdateUserRequest): Promise<UserResponse> {
  return httpClient.put<UserResponse>(`${USERS_URL}/${id}`, payload).then((res) => res.data)
}

export function resetPassword(id: number, payload: ResetPasswordRequest): Promise<void> {
  return httpClient.post(`${USERS_URL}/${id}/reset-password`, payload).then(() => undefined)
}

/**
 * Đổi mật khẩu của chính người đang đăng nhập — không nhận id, backend lấy tên đăng nhập từ JWT.
 * Nằm dưới `/auth` chứ không phải `/api/v1` (giống `/auth/login`).
 */
export function changeOwnPassword(payload: ChangePasswordRequest): Promise<void> {
  return httpClient.post('/auth/change-password', payload).then(() => undefined)
}

import { httpClient } from '../../api/httpClient'
import type { LoginRequest, LoginResponse } from './types'

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return httpClient.post<LoginResponse>('/auth/login', payload).then((res) => res.data)
}

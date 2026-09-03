import axios from 'axios'
import { getStoredAuth, clearStoredAuth } from '../features/auth/authStorage'

export const httpClient = axios.create({
  baseURL: '',
})

httpClient.interceptors.request.use((config) => {
  const auth = getStoredAuth()
  if (auth) {
    config.headers.set('Authorization', `Bearer ${auth.token}`)
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginRequest = error.config?.url === '/auth/login'
    if (error.response?.status === 401 && !isLoginRequest) {
      clearStoredAuth()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

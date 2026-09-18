import { httpClient } from '../../api/httpClient'
import type { DashboardResponse } from './types'

const DASHBOARD_URL = '/api/v1/dashboard'

export function getDashboard(): Promise<DashboardResponse> {
  return httpClient.get<DashboardResponse>(DASHBOARD_URL).then((res) => res.data)
}

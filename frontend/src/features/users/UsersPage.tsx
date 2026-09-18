import { Alert, Card, Space, Statistic, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { useAuth } from '../auth/AuthContext'
import { canManageUsers } from '../auth/permissions'
import { UserTable } from './UserTable'
import { listUsers } from './usersApi'
import type { UserResponse } from './types'

export function UsersPage() {
  const { user } = useAuth()
  const canManage = canManageUsers(user)

  const [users, setUsers] = useState<UserResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Đánh số từng lượt nạp: hai lượt chồng nhau mà lượt cũ về sau sẽ ghi đè kết quả mới hơn.
  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    // Danh sách tài khoản là endpoint ADMIN-only: PLANNER gọi vào chỉ nhận 403, nên không gọi.
    if (!canManage) {
      setLoading(false)
      return
    }
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const loaded = await listUsers()
      if (loadId !== latestLoadId.current) {
        return
      }
      setUsers(loaded)
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được danh sách tài khoản.'))
    } finally {
      if (loadId === latestLoadId.current) {
        setLoading(false)
      }
    }
  }, [canManage])

  useEffect(() => {
    // Nạp dữ liệu lần đầu từ backend — cờ loading buộc phải bật ngay trước khi gọi API.
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  const activeCount = useMemo(() => users.filter((item) => item.enabled).length, [users])

  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        Quản lý người dùng
      </Typography.Title>

      {!canManage ? (
        <RoleRestrictionNotice requiredRole="ADMIN" action="xem và quản lý tài khoản người dùng" />
      ) : (
        <>
          {loadError && <Alert type="error" showIcon style={{ marginBottom: 16 }} title={loadError} />}

          <Space size={16} style={{ marginBottom: 16 }} wrap>
            <Card size="small" style={{ width: 220 }}>
              <Statistic title="Tổng số tài khoản" value={users.length} loading={loading} />
            </Card>
            <Card size="small" style={{ width: 220 }}>
              <Statistic title="Đang hoạt động" value={activeCount} loading={loading} />
            </Card>
          </Space>

          <UserTable
            users={users}
            currentUsername={user?.username ?? ''}
            loading={loading}
            onChanged={() => void reload()}
          />
        </>
      )}
    </div>
  )
}

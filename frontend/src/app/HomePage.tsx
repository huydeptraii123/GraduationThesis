import { Typography } from 'antd'
import { useAuth } from '../features/auth/AuthContext'

export function HomePage() {
  const { user } = useAuth()

  return (
    <Typography.Title level={3}>
      Xin chào {user?.username} ({user?.role})
    </Typography.Title>
  )
}

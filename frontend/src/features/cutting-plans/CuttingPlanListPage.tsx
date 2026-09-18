import { ThunderboltOutlined } from '@ant-design/icons'
import { Alert, Button, Input, Select, Space, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { extractErrorMessage } from '../../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { canGenerateCuttingPlan } from '../auth/permissions'
import { GenerateCuttingPlanModal } from './GenerateCuttingPlanModal'
import { listCuttingPlans } from './cuttingPlansApi'
import type { CuttingPlanStatus, CuttingPlanSummaryResponse } from './types'

const STATUS_LABEL: Record<CuttingPlanStatus, { text: string; color: string }> = {
  COMPLETED: { text: 'Hoàn tất', color: 'success' },
  FAILED: { text: 'Thất bại', color: 'error' },
}

export function CuttingPlanListPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  // Chỉ PLANNER chạy thuật toán sinh phương án cắt, khớp @PreAuthorize của POST /generate.
  const canGenerate = canGenerateCuttingPlan(user)

  const [plans, setPlans] = useState<CuttingPlanSummaryResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<CuttingPlanStatus | null>(null)
  const [generateOpen, setGenerateOpen] = useState(false)

  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const loaded = await listCuttingPlans()
      if (loadId !== latestLoadId.current) {
        return
      }
      setPlans(loaded)
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được danh sách phương án cắt.'))
    } finally {
      if (loadId === latestLoadId.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase()
    return plans.filter((plan) => {
      if (statusFilter && plan.status !== statusFilter) {
        return false
      }
      if (!needle) {
        return true
      }
      return `cp-${plan.id}`.includes(needle) || dayjs(plan.runAt).format('DD/MM/YYYY HH:mm').includes(needle)
    })
  }, [plans, keyword, statusFilter])

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Phương án cắt
          </Typography.Title>
          <Typography.Text type="secondary">Lịch sử các lần chạy thuật toán sinh phương án cắt.</Typography.Text>
        </div>
        {canGenerate && (
          <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => setGenerateOpen(true)}>
            Sinh phương án cắt mới
          </Button>
        )}
      </Space>

      {!canGenerate && <RoleRestrictionNotice requiredRole="PLANNER" action="sinh phương án cắt mới" />}

      {loadError && <Alert type="error" showIcon style={{ margin: '16px 0' }} title={loadError} />}

      <Space style={{ margin: '16px 0' }} wrap>
        <Input
          allowClear
          placeholder="Tìm theo mã phương án #CP-, ngày chạy"
          style={{ width: 280 }}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <Select<CuttingPlanStatus | null>
          allowClear
          placeholder="Trạng thái: Tất cả"
          style={{ width: 180 }}
          value={statusFilter}
          onChange={(value) => setStatusFilter(value ?? null)}
          options={[
            { value: 'COMPLETED', label: 'Hoàn tất' },
            { value: 'FAILED', label: 'Thất bại' },
          ]}
        />
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={filtered}
        pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `${total} lần chạy` }}
        columns={[
          {
            title: 'Thời điểm chạy',
            dataIndex: 'runAt',
            render: (value: string) => dayjs(value).format('DD/MM/YYYY HH:mm'),
          },
          {
            title: 'Mã phương án',
            render: (_, plan) => <Tag>#CP-{plan.id}</Tag>,
          },
          {
            title: 'Phạm vi',
            render: (_, plan) => `${plan.scopeOrderCount} đơn · đến ${dayjs(plan.scopeCutoffDate).format('DD/MM/YYYY')}`,
          },
          {
            title: 'Tỷ lệ phế',
            render: (_, plan) => {
              const ratio = plan.totalStockUsedM > 0 ? (plan.totalWasteM / plan.totalStockUsedM) * 100 : 0
              return (
                <span>
                  {ratio.toFixed(1)}% / {plan.totalWasteM.toFixed(1)}m
                </span>
              )
            },
          },
          {
            title: 'Trạng thái',
            align: 'center',
            render: (_, plan) => <Tag color={STATUS_LABEL[plan.status].color}>{STATUS_LABEL[plan.status].text}</Tag>,
          },
          {
            title: 'Hành động',
            align: 'right',
            render: (_, plan) => <a onClick={() => navigate(`/cutting-plans/${plan.id}`)}>Xem chi tiết →</a>,
          },
        ]}
      />

      <GenerateCuttingPlanModal
        open={generateOpen}
        onClose={() => setGenerateOpen(false)}
        onGenerated={(newPlanId) => {
          setGenerateOpen(false)
          void reload()
          navigate(`/cutting-plans/${newPlanId}`)
        }}
      />
    </div>
  )
}

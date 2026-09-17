import { DownloadOutlined } from '@ant-design/icons'
import { Alert, Space, Spin, Tabs, Tag, Tooltip, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { extractErrorMessage } from '../../api/apiError'
import { CuttingPlanByOrderTab } from './CuttingPlanByOrderTab'
import { CuttingPlanByStickTab } from './CuttingPlanByStickTab'
import { CuttingPlanOverviewTab } from './CuttingPlanOverviewTab'
import { createOrderColorAssigner } from './orderColorPalette'
import { buildCuttingBatches, buildDedupedOrderRows } from './cuttingBatches'
import { getCuttingPlan } from './cuttingPlansApi'
import type { CuttingPlanResponse, CuttingPlanStatus } from './types'

const STATUS_LABEL: Record<CuttingPlanStatus, { text: string; color: string }> = {
  COMPLETED: { text: 'Hoàn tất', color: 'success' },
  FAILED: { text: 'Thất bại', color: 'error' },
}

export function CuttingPlanDetailPage() {
  const { id } = useParams<{ id: string }>()
  const planId = Number(id)

  const [plan, setPlan] = useState<CuttingPlanResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const loaded = await getCuttingPlan(planId)
      setPlan(loaded)
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, 'Không tải được chi tiết phương án cắt.'))
    } finally {
      setLoading(false)
    }
  }, [planId])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  // Gán màu theo đơn hàng dùng chung xuyên suốt mọi thanh của CHÍNH lần chạy này — tạo lại khi
  // đổi sang phương án khác, giữ nguyên khi re-render cùng 1 phương án.
  // Chỉ tạo lại khi đổi sang phương án khác (plan.id) — bản thân hàm không đọc plan, chỉ dùng id làm khóa nhớ lại.
  // oxlint-disable-next-line react-hooks/exhaustive-deps
  const assignOrderColor = useMemo(() => createOrderColorAssigner(), [plan?.id])

  const orderRows = useMemo(() => (plan ? buildDedupedOrderRows(plan) : []), [plan])
  const batches = useMemo(() => buildCuttingBatches(orderRows), [orderRows])

  if (loading && !plan) {
    return <Spin style={{ marginTop: 48 }} />
  }

  if (loadError && !plan) {
    return <Alert type="error" showIcon title={loadError} />
  }

  if (!plan) {
    return null
  }

  return (
    <div>
      <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
        <div>
          <Space align="center">
            <Typography.Title level={3} style={{ margin: 0 }}>
              Phương án cắt #CP-{plan.id}
            </Typography.Title>
            <Tag color={STATUS_LABEL[plan.status].color}>{STATUS_LABEL[plan.status].text}</Tag>
          </Space>
          <Typography.Text type="secondary">
            Chạy lúc {dayjs(plan.runAt).format('DD/MM/YYYY HH:mm')} · Phạm vi {plan.scopeOrderCount} đơn đến{' '}
            {dayjs(plan.scopeCutoffDate).format('DD/MM/YYYY')} · Tỷ lệ phế{' '}
            {plan.totalStockUsedM > 0 ? ((plan.totalWasteM / plan.totalStockUsedM) * 100).toFixed(1) : '0.0'}%
          </Typography.Text>
        </div>
        <Tooltip title="Sẽ có ở báo cáo thiếu vật tư (task riêng, chưa làm ở đây)">
          <span>
            <Typography.Link disabled>
              <DownloadOutlined /> Xuất Excel
            </Typography.Link>
          </span>
        </Tooltip>
      </Space>

      {loadError && <Alert type="error" showIcon style={{ margin: '16px 0' }} title={loadError} />}

      <Tabs
        style={{ marginTop: 16 }}
        items={[
          {
            key: 'overview',
            label: 'Tổng quan',
            children: <CuttingPlanOverviewTab plan={plan} orderRows={orderRows} batches={batches} />,
          },
          {
            key: 'by-order',
            label: `Chi tiết theo đơn hàng (${plan.details.reduce((n, d) => n + d.items.length, 0) + plan.shortages.length})`,
            children: <CuttingPlanByOrderTab plan={plan} />,
          },
          {
            key: 'by-stick',
            label: 'Chi tiết xuất kho theo phôi',
            children: <CuttingPlanByStickTab plan={plan} assignOrderColor={assignOrderColor} />,
          },
        ]}
      />
    </div>
  )
}

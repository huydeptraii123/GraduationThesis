import { CheckCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, App, Button, Empty, Space, Spin, Tabs, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { extractErrorMessage, hasStatus } from '../../api/apiError'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { useAuth } from '../auth/AuthContext'
import { canApproveCuttingPlan } from '../auth/permissions'
import { CuttingPlanByOrderTab } from './CuttingPlanByOrderTab'
import { CuttingPlanByStickTab } from './CuttingPlanByStickTab'
import { CuttingPlanOverviewTab } from './CuttingPlanOverviewTab'
import { buildCuttingBatches, buildDedupedOrderRows } from './cuttingBatches'
import { approveCuttingPlan, getApprovalPreview } from './cuttingPlansApi'
import { createOrderColorAssigner } from './orderColorPalette'
import { toDisplayPlan } from './toDisplayPlan'
import type { CuttingPlanApprovalPreviewResponse } from './types'

/**
 * Màn hình duyệt phương án cắt — nơi duy nhất trong hệ thống làm thay đổi tồn kho và trạng thái đơn
 * hàng ngoài các luồng nhập/sửa dữ liệu.
 *
 * Phương án hiển thị ở đây chưa được lưu: mở màn hình là tính lại trên trạng thái hiện tại, thoát
 * ra là mất. Chỉ khi bấm duyệt, hệ thống mới ghi phương án, trừ tồn kho và đánh dấu các đơn trong
 * phạm vi.
 */
export function CuttingPlanApprovalPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { message } = App.useApp()
  const canApprove = canApproveCuttingPlan(user)

  const [preview, setPreview] = useState<CuttingPlanApprovalPreviewResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [approving, setApproving] = useState(false)
  const [staleWarning, setStaleWarning] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const loaded = await getApprovalPreview()
      setPreview(loaded)
      setLoadError(null)
    } catch (error) {
      // Xóa luôn phương án cũ chứ không chỉ hiện lỗi: nút Duyệt đọc phạm vi từ đây, để lại phương
      // án cũ nghĩa là vẫn bấm duyệt được bằng dấu vân đã lỗi thời — đúng vòng lặp 409 mà lần tải
      // lại này sinh ra để cắt.
      setPreview(null)
      setLoadError(extractErrorMessage(error, 'Không tính được phương án cắt để duyệt.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!canApprove) {
      // oxlint-disable-next-line react/set-state-in-effect
      setLoading(false)
      return
    }
    void reload()
  }, [canApprove, reload])

  const displayPlan = useMemo(() => (preview ? toDisplayPlan(preview) : null), [preview])
  // Bảng màu gán theo đơn hàng, dựng lại mỗi lần tính lại phương án — khóa nhớ là chính đối tượng
  // preview, vì phương án chưa lưu không có mã để làm khóa.
  // oxlint-disable-next-line react-hooks/exhaustive-deps
  const assignOrderColor = useMemo(() => createOrderColorAssigner(), [preview])
  const orderRows = useMemo(() => (displayPlan ? buildDedupedOrderRows(displayPlan) : []), [displayPlan])
  const batches = useMemo(() => buildCuttingBatches(orderRows), [orderRows])

  async function handleApprove() {
    if (!preview) {
      return
    }
    setApproving(true)
    try {
      const approved = await approveCuttingPlan(preview.stateFingerprint)
      message.success(`Đã duyệt phương án cắt #CP-${approved.id}.`)
      navigate(`/cutting-plans/${approved.id}`)
    } catch (error) {
      const failure = extractErrorMessage(error, 'Không duyệt được phương án cắt.')
      setApproving(false)
      // CHỈ 409 mới là "dữ liệu đã đổi trong lúc xem xét". Khi đó phải tính lại ngay, vì bấm duyệt
      // lần nữa trên đúng phương án vừa bị từ chối thì dấu vân cũ vẫn lệch và lần nào cũng hỏng.
      // Mọi lỗi khác — mất quyền, máy chủ lỗi, rớt mạng — tính lại cũng vô ích, và nói với người
      // dùng rằng "phương án đã được tính lại" là nói sai chuyện vừa xảy ra.
      if (hasStatus(error, 409)) {
        setStaleWarning(failure)
        await reload()
      } else {
        message.error(failure)
      }
      return
    }
    setApproving(false)
  }

  if (!canApprove) {
    return (
      <div>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Duyệt phương án cắt
        </Typography.Title>
        <div style={{ marginTop: 16 }}>
          <RoleRestrictionNotice requiredRole="PLANNER" action="duyệt phương án cắt" />
        </div>
      </div>
    )
  }

  const scopeOrderCount = preview?.plan.scopeOrderCount ?? 0

  return (
    <div>
      <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Duyệt phương án cắt
          </Typography.Title>
          <Typography.Text type="secondary">
            {preview
              ? `Phạm vi ${scopeOrderCount} đơn có ngày giao đến ${dayjs(preview.scopeCutoffDate).format('DD/MM/YYYY')} · Tính lúc ${dayjs(preview.plan.computedAt).format('HH:mm:ss')}`
              : 'Đơn hàng có ngày giao trong vòng 3 ngày tới, tối đa 70 đơn mỗi đợt.'}
          </Typography.Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void reload()}>
            Tính lại
          </Button>
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            loading={approving}
            disabled={loading || scopeOrderCount === 0}
            onClick={() => void handleApprove()}
          >
            Duyệt phương án
          </Button>
        </Space>
      </Space>

      <Alert
        type="info"
        showIcon
        style={{ marginTop: 16 }}
        title="Phương án dưới đây chưa được lưu."
        description="Chỉ khi bấm Duyệt phương án, hệ thống mới ghi lại phương án, trừ tồn kho và đưa các đơn trong phạm vi ra khỏi hàng chờ."
      />

      {staleWarning && (
        <Alert
          type="warning"
          showIcon
          closable
          style={{ marginTop: 16 }}
          title={staleWarning}
          description="Phương án đã được tính lại trên trạng thái mới nhất. Hãy xem lại rồi duyệt."
          onClose={() => setStaleWarning(null)}
        />
      )}

      {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

      {preview != null && preview.plan.blockedOrderCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 16 }}
          title={`${preview.plan.blockedOrderCount} đơn trong hạn giao đang bị bỏ qua vì mẫu cửa chưa có định mức BOM dùng được.`}
          description="Những đơn này không nằm trong phạm vi duyệt và sẽ ở lại hàng chờ cho tới khi định mức được khai báo."
        />
      )}

      <Spin spinning={loading}>
        {displayPlan != null && scopeOrderCount > 0 ? (
          <Tabs
            style={{ marginTop: 16 }}
            items={[
              {
                key: 'overview',
                label: 'Tổng quan',
                children: <CuttingPlanOverviewTab plan={displayPlan} orderRows={orderRows} batches={batches} />,
              },
              {
                key: 'by-order',
                // Cùng công thức với màn hình phương án đã duyệt: tab này liệt kê từng đoạn cắt và
                // từng dòng thiếu vật tư, không phải các dòng đã gộp của mức chi tiết theo đơn hàng.
                label: `Chi tiết theo đơn hàng (${displayPlan.details.reduce((n, d) => n + d.items.length, 0) + displayPlan.shortages.length})`,
                children: <CuttingPlanByOrderTab plan={displayPlan} />,
              },
              {
                key: 'by-stick',
                label: 'Chi tiết xuất kho theo phôi',
                children: <CuttingPlanByStickTab plan={displayPlan} assignOrderColor={assignOrderColor} />,
              },
            ]}
          />
        ) : (
          !loading &&
          loadError == null && (
            <Empty
              style={{ marginTop: 48 }}
              description="Không có đơn hàng nào tới hạn xử lý trong đợt này."
            />
          )
        )}
      </Spin>
    </div>
  )
}

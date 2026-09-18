import { ThunderboltOutlined } from '@ant-design/icons'
import { Alert, App, Modal, Spin, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { generateCuttingPlan, getScopePreview } from './cuttingPlansApi'
import type { CuttingPlanScopePreviewResponse } from './types'

interface Props {
  open: boolean
  onClose: () => void
  onGenerated: (newPlanId: number) => void
}

/**
 * Mockup gốc hiện toast tiến trình bất đồng bộ sau khi xác nhận — bỏ vì POST /generate trả kết quả
 * ngay trong 1 request (không có job nền), chỉ cần loading spinner trong lúc chờ.
 */
export function GenerateCuttingPlanModal({ open, onClose, onGenerated }: Props) {
  const { message } = App.useApp()
  const [preview, setPreview] = useState<CuttingPlanScopePreviewResponse | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [generating, setGenerating] = useState(false)

  const loadPreview = useCallback(async () => {
    setPreviewLoading(true)
    try {
      const loaded = await getScopePreview()
      setPreview(loaded)
      setPreviewError(null)
    } catch (error) {
      setPreviewError(extractErrorMessage(error, 'Không xem trước được phạm vi xử lý.'))
    } finally {
      setPreviewLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!open) {
      return
    }
    // oxlint-disable-next-line react/set-state-in-effect
    setPreview(null)
    setPreviewError(null)
    void loadPreview()
  }, [open, loadPreview])

  async function handleConfirm() {
    setGenerating(true)
    try {
      const plan = await generateCuttingPlan()
      message.success(`Đã sinh phương án cắt #CP-${plan.id}.`)
      onGenerated(plan.id)
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không sinh được phương án cắt.'))
    } finally {
      setGenerating(false)
    }
  }

  return (
    <Modal
      open={open}
      title={
        <span>
          <ThunderboltOutlined /> Xác nhận sinh phương án cắt mới?
        </span>
      }
      onCancel={onClose}
      okText="Sinh phương án cắt"
      cancelText="Hủy"
      confirmLoading={generating}
      onOk={handleConfirm}
      okButtonProps={{ icon: <ThunderboltOutlined />, disabled: previewLoading || Boolean(previewError) }}
    >
      <Typography.Paragraph>
        Hệ thống sẽ tự động xử lý các đơn hàng chưa có kết quả cắt, có ngày giao trong vòng 3 ngày tới (ngưỡng
        t+3), tối đa 70 đơn/lần chạy. Đơn ngoài phạm vi sẽ được xử lý ở lần chạy sau.
      </Typography.Paragraph>

      {previewError && <Alert type="error" showIcon title={previewError} style={{ marginBottom: 12 }} />}

      <Spin spinning={previewLoading}>
        <div style={{ background: '#fafafa', padding: 12, borderRadius: 4, lineHeight: 2 }}>
          <div>
            Đơn hàng thỏa mãn điều kiện: <strong>{preview ? `${preview.eligibleOrderCount} đơn hàng` : '—'}</strong>
          </div>
          <div>
            Ngưỡng ngày giao tối đa:{' '}
            <strong>{preview ? dayjs(preview.scopeCutoffDate).format('DD/MM/YYYY') : '—'}</strong>
          </div>
          <div>
            Mô tả thuật toán: <strong>Best Fit Decreasing mở rộng 4 mức ưu tiên</strong>
          </div>
        </div>
      </Spin>
    </Modal>
  )
}

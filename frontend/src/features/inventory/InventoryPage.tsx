import { Alert, Card, Space, Statistic, Tabs, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { InventoryBatchTable } from './InventoryBatchTable'
import { SlatMaterialTable } from './SlatMaterialTable'
import { listBatches, listMaterials } from './inventoryApi'
import type { InventoryBatchResponse, SlatMaterialResponse } from './types'

export function InventoryPage() {
  const { user } = useAuth()
  // Nghiệp vụ tồn kho thuộc về PLANNER; ADMIN vẫn xem được nhưng không thấy các nút ghi.
  const canEdit = user?.role === 'PLANNER'

  const [batches, setBatches] = useState<InventoryBatchResponse[]>([])
  const [materials, setMaterials] = useState<SlatMaterialResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Đánh số từng lượt nạp: hai lượt chồng nhau (vd bấm xóa liên tiếp) mà lượt cũ về sau
  // sẽ ghi đè kết quả mới hơn và tắt cờ loading quá sớm nếu không bỏ qua kết quả cũ.
  const latestLoadId = useRef(0)

  // Nạp cả hai danh sách cùng lúc: bảng lô tồn cần mã và nhóm vật tư nằm ở danh mục loại thanh nan.
  const reload = useCallback(async () => {
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const [loadedBatches, loadedMaterials] = await Promise.all([listBatches(), listMaterials()])
      if (loadId !== latestLoadId.current) {
        return
      }
      setBatches(loadedBatches)
      setMaterials(loadedMaterials)
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được dữ liệu tồn kho.'))
    } finally {
      if (loadId === latestLoadId.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    // Nạp dữ liệu lần đầu từ backend — đây đúng là trường hợp đồng bộ với hệ thống ngoài mà effect dùng để làm;
    // cờ loading buộc phải bật ngay trước khi gọi API nên không tránh được setState đồng bộ ở đây.
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  const totals = useMemo(() => {
    let totalSticks = 0
    let totalMm = 0
    batches.forEach((batch) => {
      totalSticks += batch.soThanh
      totalMm += batch.doDaiThanhMm * batch.soThanh
    })
    return { totalSticks, totalMeters: totalMm / 1000 }
  }, [batches])

  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        Tồn kho thanh nan
      </Typography.Title>

      {loadError && (
        <Alert type="error" showIcon style={{ marginBottom: 16 }} title={loadError} />
      )}

      <Space size={16} style={{ marginBottom: 16 }} wrap>
        <Card size="small" style={{ width: 260 }}>
          <Statistic
            title="Tổng chiều dài tồn kho"
            value={totals.totalMeters}
            // Statistic mặc định ngăn cách kiểu en-US, lệch với các bảng bên dưới đang dùng vi-VN.
            formatter={(value) => Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}
            suffix="mét"
            loading={loading}
          />
        </Card>
        <Card size="small" style={{ width: 260 }}>
          <Statistic
            title="Tổng số thanh"
            value={totals.totalSticks}
            formatter={(value) => Number(value).toLocaleString('vi-VN')}
            suffix="thanh"
            loading={loading}
          />
        </Card>
      </Space>

      <Tabs
        items={[
          {
            key: 'batches',
            label: `Tồn kho theo lô (${batches.length})`,
            children: (
              <InventoryBatchTable
                batches={batches}
                materials={materials}
                canEdit={canEdit}
                loading={loading}
                onChanged={() => void reload()}
              />
            ),
          },
          {
            key: 'materials',
            label: `Danh mục loại thanh nan (${materials.length})`,
            children: (
              <SlatMaterialTable
                materials={materials}
                canEdit={canEdit}
                loading={loading}
                onChanged={() => void reload()}
              />
            ),
          },
        ]}
      />
    </div>
  )
}

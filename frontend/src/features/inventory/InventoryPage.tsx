import { Alert, Card, Space, Statistic, Tabs, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { canEditInventoryBatch, canEditSlatMaterial } from '../auth/permissions'
import { InventoryBatchTable } from './InventoryBatchTable'
import { SlatMaterialTable } from './SlatMaterialTable'
import { getInventorySummary, listMaterialOptions } from './inventoryApi'
import type { InventorySummaryResponse, SlatMaterialResponse } from './types'

export function InventoryPage() {
  const { user } = useAuth()
  // Hai tab, hai luật quyền khác nhau: lô tồn kho là vận hành (PLANNER), còn danh mục loại thanh
  // nan là dữ liệu nền tảng nên ADMIN cũng sửa được.
  const canEditBatches = canEditInventoryBatch(user)
  const canEditMaterials = canEditSlatMaterial(user)

  // Trang cha chỉ còn giữ 2 thứ KHÔNG phân trang được: số liệu tổng hợp toàn kho và danh mục vật tư
  // đầy đủ (dùng cho dropdown + tra mã/nhóm ở bảng lô tồn). Dữ liệu bảng do chính bảng tự tải theo
  // từng trang.
  const [summary, setSummary] = useState<InventorySummaryResponse | null>(null)
  const [materials, setMaterials] = useState<SlatMaterialResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Đánh số từng lượt nạp: hai lượt chồng nhau (vd bấm xóa liên tiếp) mà lượt cũ về sau
  // sẽ ghi đè kết quả mới hơn và tắt cờ loading quá sớm nếu không bỏ qua kết quả cũ.
  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const [loadedSummary, loadedMaterials] = await Promise.all([getInventorySummary(), listMaterialOptions()])
      if (loadId !== latestLoadId.current) {
        return
      }
      setSummary(loadedSummary)
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
            value={summary?.totalLengthM ?? 0}
            // Statistic mặc định ngăn cách kiểu en-US, lệch với các bảng bên dưới đang dùng vi-VN.
            formatter={(value) => Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}
            suffix="mét"
            loading={loading}
          />
        </Card>
        <Card size="small" style={{ width: 260 }}>
          <Statistic
            title="Tổng số thanh"
            value={summary?.totalSticks ?? 0}
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
            // Số trên nhãn tab lấy từ số liệu tổng hợp, không phải độ dài mảng đang hiển thị — sau
            // khi phân trang, mảng đó chỉ còn 20 dòng.
            label: `Tồn kho theo lô (${summary?.batchCount ?? 0})`,
            children: (
              <>
                {!canEditBatches && (
                  <RoleRestrictionNotice requiredRole="PLANNER" action="thêm/sửa/xóa lô tồn kho" />
                )}
                <InventoryBatchTable
                  materials={materials}
                  canEdit={canEditBatches}
                  onChanged={() => void reload()}
                />
              </>
            ),
          },
          {
            key: 'materials',
            label: `Danh mục loại thanh nan (${materials.length})`,
            children: <SlatMaterialTable canEdit={canEditMaterials} onChanged={() => void reload()} />,
          },
        ]}
      />
    </div>
  )
}

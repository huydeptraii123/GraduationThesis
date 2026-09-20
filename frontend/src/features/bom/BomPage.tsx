import { Alert, Card, Space, Statistic, Tag, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { canEditBom } from '../auth/permissions'
import { listMaterialOptions } from '../inventory/inventoryApi'
import type { SlatMaterialResponse } from '../inventory/types'
import { BomItemTable } from './BomItemTable'
import { getBomSummary, listDoorProducts } from './bomApi'
import type { BomSummaryResponse, DoorProductResponse } from './types'

export function BomPage() {
  const { user } = useAuth()
  // Định mức BOM là dữ liệu nền tảng, chỉ ADMIN được ghi (khác 6.1 — tồn kho thuộc PLANNER).
  const canEdit = canEditBom(user)

  // Trang cha chỉ giữ thứ KHÔNG phân trang được: số liệu tổng hợp và 2 danh mục tra cứu. Dữ liệu
  // bảng do chính bảng tự tải theo từng trang.
  const [summary, setSummary] = useState<BomSummaryResponse | null>(null)
  const [doorProducts, setDoorProducts] = useState<DoorProductResponse[]>([])
  const [slatMaterials, setSlatMaterials] = useState<SlatMaterialResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Đánh số từng lượt nạp: hai lượt chồng nhau (vd bấm xóa liên tiếp) mà lượt cũ về sau
  // sẽ ghi đè kết quả mới hơn và tắt cờ loading quá sớm nếu không bỏ qua kết quả cũ.
  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const [loadedSummary, loadedDoorProducts, loadedSlatMaterials] = await Promise.all([
        getBomSummary(),
        listDoorProducts(),
        listMaterialOptions(),
      ])
      if (loadId !== latestLoadId.current) {
        return
      }
      setSummary(loadedSummary)
      setDoorProducts(loadedDoorProducts)
      setSlatMaterials(loadedSlatMaterials)
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được dữ liệu định mức BOM.'))
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
      <Space align="center" style={{ marginBottom: 4 }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Định mức BOM (Công thức cắt nan)
        </Typography.Title>
        <Tag color="gold">Admin Only</Tag>
      </Space>
      <Typography.Text type="secondary">
        Thiết lập tham số offset kích thước và thông số tính số lượng nan cho từng mẫu cửa.
      </Typography.Text>

      {!canEdit && <RoleRestrictionNotice requiredRole="ADMIN" action="thêm/sửa/xóa định mức BOM" />}

      {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

      <Space size={16} style={{ margin: '16px 0' }} wrap>
        <Card size="small" style={{ width: 220 }}>
          <Statistic title="Tổng định mức BOM" value={summary?.totalItems ?? 0} loading={loading} />
        </Card>
        <Card size="small" style={{ width: 220 }}>
          <Statistic title="Nhóm thanh nan đang dùng" value={summary?.groupCount ?? 0} loading={loading} />
        </Card>
        <Card size="small" style={{ width: 220 }}>
          <Statistic title="Mẫu cửa đang có định mức" value={summary?.doorProductCount ?? 0} loading={loading} />
        </Card>
      </Space>

      <BomItemTable
        doorProducts={doorProducts}
        slatMaterials={slatMaterials}
        canEdit={canEdit}
        onChanged={() => void reload()}
      />
    </div>
  )
}

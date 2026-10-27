import { Table } from 'antd'
import { SLAT_GROUP_LABEL } from '../inventory/constants'
import type { SlatGroup } from '../inventory/types'
import { formatMeters } from '../../utils/formatMeters'
import type { ShortageByMaterial } from './demandAggregates'

/**
 * Vật tư còn thiếu gộp theo loại thanh nan. Trang chủ và màn báo cáo thiếu vật tư toàn hệ thống
 * dùng chung đúng bảng này, để cùng một lần tính không bao giờ hiện ra hai con số khác nhau.
 */
export function ShortageByMaterialTable({ shortages }: { shortages: ShortageByMaterial[] }) {
  return (
    <Table<ShortageByMaterial>
      size="small"
      rowKey="slatMaterialCode"
      pagination={false}
      dataSource={shortages}
      locale={{ emptyText: 'Tồn kho đáp ứng đủ mọi bộ cửa đang chờ' }}
      columns={[
        { title: 'Mã vật tư', dataIndex: 'slatMaterialCode' },
        { title: 'Mô tả vật tư', dataIndex: 'slatMaterialName' },
        {
          title: 'Nhóm',
          dataIndex: 'slatGroup',
          render: (value: SlatGroup) => SLAT_GROUP_LABEL[value] ?? value,
        },
        {
          title: 'SL thanh thiếu',
          dataIndex: 'missingSticks',
          align: 'right',
          render: (value: number) => value.toLocaleString('vi-VN'),
        },
        {
          title: 'Tổng độ dài thiếu',
          dataIndex: 'missingLengthMm',
          align: 'right',
          // Đúng tới milimet như file Excel và màn báo thiếu vật tư — doanh nghiệp lập kế hoạch
          // sản xuất bù theo chính con số này.
          render: (value: number) => `${formatMeters(value)} m`,
        },
        { title: 'Số bộ cửa ảnh hưởng', dataIndex: 'affectedDoorSetCount', align: 'right' },
      ]}
    />
  )
}

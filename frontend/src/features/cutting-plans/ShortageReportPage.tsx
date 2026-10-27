import { CalculatorOutlined, DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Col, Empty, Input, Row, Select, Space, Spin, Statistic, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { extractBlobErrorMessage } from '../../api/apiError'
import { downloadFile } from '../../api/downloadFile'
import { localPagination } from '../../api/pagination'
import { formatMeters } from '../../utils/formatMeters'
import { shortagesByMaterial, toDoorSets } from '../dashboard/demandAggregates'
import { ShortageByMaterialTable } from '../dashboard/ShortageByMaterialTable'
import { DOOR_SET_SHORT } from '../dashboard/simulationSeries'
import { useSimulation } from '../dashboard/useSimulation'
import { exportSystemShortageReport } from './cuttingPlansApi'
import type { CuttingPlanDemandView } from './types'

function shortageKey(row: CuttingPlanDemandView): string {
  return `${row.ycsx}#${row.item}#${row.slatMaterialCode}`
}

/**
 * Báo cáo thiếu vật tư của TOÀN BỘ sổ đơn chưa duyệt — chức năng riêng, tách khỏi "Duyệt phương án
 * cắt", không giới hạn ngày giao hay số đơn như đợt duyệt. Dùng để lập kế hoạch sản xuất thanh nan
 * bù cho mọi đơn đang tồn, không chỉ cho vài ngày tới.
 *
 * Số liệu lấy từ đúng lần tính của trang chủ (dùng chung bộ nhớ lần tính), nên hai màn không bao giờ
 * nói hai con số khác nhau về cùng một thời điểm tính.
 */
export function ShortageReportPage() {
  const { message } = App.useApp()
  const { simulation, computing, loadError, compute } = useSimulation()
  const [exporting, setExporting] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [materialFilter, setMaterialFilter] = useState<number | null>(null)

  // Giữ nguyên thứ tự máy chủ trả về — gom theo loại thanh nan, trong mỗi loại theo thứ tự ưu tiên —
  // chính là thứ tự dòng của file Excel. "TT ưu tiên" đánh số lại từ 1 trong từng loại thanh nan nên
  // sắp lại theo cột đó sẽ trộn các loại vào nhau.
  const shortageRows = useMemo(() => (simulation?.demands ?? []).filter((row) => row.quantityMissing > 0), [simulation])
  const byMaterial = useMemo(() => shortagesByMaterial(simulation?.demands ?? []), [simulation])

  const shortDoorSetCount = useMemo(
    () => toDoorSets(simulation?.demands ?? []).filter((doorSet) => doorSet.status === DOOR_SET_SHORT).length,
    [simulation],
  )
  const totalMissingSticks = byMaterial.reduce((sum, row) => sum + row.missingSticks, 0)
  const totalMissingLengthMm = byMaterial.reduce((sum, row) => sum + row.missingLengthMm, 0)

  const materialOptions = useMemo(
    () => byMaterial.map((row) => ({ value: row.slatMaterialCode, label: `${row.slatMaterialName} (${row.slatMaterialCode})` })),
    [byMaterial],
  )
  // Sau một lần tính lại, loại thanh nan đang chọn có thể không còn thiếu nữa: bỏ lọc thay vì giữ một
  // giá trị không còn trong danh sách (ô chọn hiện mã trần, bảng rỗng như thể không còn gì thiếu).
  const activeMaterial = materialOptions.some((option) => option.value === materialFilter) ? materialFilter : null

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase()
    return shortageRows
      .filter((row) => activeMaterial == null || row.slatMaterialCode === activeMaterial)
      .filter((row) => !needle || row.ycsx.toLowerCase().includes(needle) || row.customerName.toLowerCase().includes(needle))
  }, [shortageRows, keyword, activeMaterial])

  async function handleExport() {
    setExporting(true)
    try {
      const blob = await exportSystemShortageReport()
      downloadFile(blob, `bao-cao-thieu-vat-tu-toan-he-thong-${dayjs().format('YYYYMMDD-HHmmss')}.xlsx`)
    } catch (error) {
      message.error(await extractBlobErrorMessage(error, 'Không xuất được báo cáo thiếu vật tư.'))
    } finally {
      setExporting(false)
    }
  }

  return (
    <div>
      <Card>
        <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              Báo cáo thiếu vật tư toàn hệ thống
            </Typography.Title>
            <Typography.Text type="secondary">
              Toàn bộ đơn hàng chưa thuộc phương án nào được duyệt — không giới hạn ngày giao hay số đơn như đợt
              duyệt. Căn cứ lập kế hoạch sản xuất thanh nan bù.
            </Typography.Text>
          </div>
          <Space>
            <Button type="primary" icon={<CalculatorOutlined />} loading={computing} onClick={() => void compute()}>
              Tính thiếu vật tư
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting}
              disabled={simulation == null || computing}
              onClick={() => void handleExport()}
            >
              Xuất Excel thiếu vật tư
            </Button>
          </Space>
        </Space>
        {simulation != null && (
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
            Tính lúc {dayjs(simulation.computedAt).format('HH:mm:ss DD/MM/YYYY')} (dùng chung với lần tính ở trang chủ).
            File Excel được máy chủ tính lại ngay lúc tải, nên nếu đơn hàng hoặc tồn kho vừa thay đổi thì số liệu trong
            file có thể khác màn hình.
          </Typography.Text>
        )}
      </Card>

      {simulation != null && simulation.blockedOrderCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 16 }}
          title={`${simulation.blockedOrderCount} đơn không có trong báo cáo này vì định mức BOM của mẫu cửa chưa đầy đủ.`}
          description={
            <span>
              Nhu cầu vật tư của những đơn này chưa tính được, nên số thiếu thật có thể lớn hơn số bên dưới. Cần tài
              khoản Quản trị (ADMIN) bổ sung định mức tại <Link to="/bom">Định mức BOM</Link>.
            </span>
          }
        />
      )}

      {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

      <Spin spinning={computing}>
        {simulation == null ? (
          <Card size="small" style={{ marginTop: 16 }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <Space direction="vertical" size={4}>
                  <Typography.Text strong>Chưa có số liệu</Typography.Text>
                  <Typography.Text type="secondary">
                    Bấm <b>Tính thiếu vật tư</b> để chạy thuật toán trên toàn bộ đơn hàng đang chờ. Thao tác này chỉ
                    đọc dữ liệu — không trừ tồn kho và không đánh dấu đơn hàng nào.
                  </Typography.Text>
                </Space>
              }
            />
          </Card>
        ) : (
          <>
            <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
              <Col xs={24} md={12} xl={6}>
                <Card size="small">
                  <Statistic title="Bộ cửa thiếu vật tư" value={shortDoorSetCount} groupSeparator="." suffix="bộ" />
                </Card>
              </Col>
              <Col xs={24} md={12} xl={6}>
                <Card size="small">
                  <Statistic title="Loại thanh nan thiếu" value={byMaterial.length} groupSeparator="." suffix="loại" />
                </Card>
              </Col>
              <Col xs={24} md={12} xl={6}>
                <Card size="small">
                  <Statistic title="Tổng số thanh thiếu" value={totalMissingSticks} groupSeparator="." suffix="thanh" />
                </Card>
              </Col>
              <Col xs={24} md={12} xl={6}>
                <Card size="small">
                  {/* formatter thay cho value số: Statistic tự chèn dấu phân cách theo kiểu của nó, lệch
                      với cách ghi mét chính xác tới milimet ở mọi màn còn lại. */}
                  <Statistic title="Tổng độ dài thiếu" formatter={() => `${formatMeters(totalMissingLengthMm)} m`} />
                </Card>
              </Col>
            </Row>

            <Card size="small" style={{ marginTop: 16 }} title="Gộp theo loại thanh nan">
              <ShortageByMaterialTable shortages={byMaterial} />
            </Card>

            <Card size="small" style={{ marginTop: 16 }} title="Chi tiết theo bộ cửa">
              <Space style={{ marginBottom: 16 }} wrap>
                <Input
                  allowClear
                  placeholder="Tìm theo lệnh sản xuất, khách hàng"
                  prefix={<SearchOutlined />}
                  style={{ width: 280 }}
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                />
                <Select<number | null>
                  allowClear
                  placeholder="Loại thanh nan: Tất cả"
                  style={{ width: 260 }}
                  value={activeMaterial}
                  onChange={(value) => setMaterialFilter(value ?? null)}
                  options={materialOptions}
                />
              </Space>
              <Table<CuttingPlanDemandView>
                size="small"
                rowKey={shortageKey}
                dataSource={filtered}
                scroll={{ x: 'max-content' }}
                locale={{ emptyText: 'Không có bộ cửa nào thiếu vật tư' }}
                pagination={localPagination((total) => `${total} dòng thiếu vật tư`)}
                columns={[
                  { title: 'TT ưu tiên', dataIndex: 'priorityRank', align: 'right' },
                  {
                    title: 'Lệnh SX / Bộ cửa',
                    render: (_, row) => (
                      <div>
                        <div style={{ fontWeight: 600 }}>{row.ycsx}</div>
                        <div style={{ color: '#8c8c8c', fontSize: 12 }}>Bộ cửa #{row.item}</div>
                      </div>
                    ),
                  },
                  { title: 'Khách hàng', dataIndex: 'customerName' },
                  {
                    title: 'Ngày giao yêu cầu',
                    dataIndex: 'reqdDeliveryDate',
                    render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
                  },
                  {
                    title: 'Loại thanh nan',
                    render: (_, row) => (
                      <div>
                        <div>{row.slatMaterialName}</div>
                        <div style={{ color: '#8c8c8c', fontSize: 12 }}>{row.slatMaterialCode}</div>
                      </div>
                    ),
                  },
                  {
                    title: 'Độ dài đoạn',
                    dataIndex: 'cutLengthMm',
                    align: 'right',
                    render: (value: number) => `${formatMeters(value)} m`,
                  },
                  { title: 'SL cần', dataIndex: 'quantityNeeded', align: 'right' },
                  { title: 'SL thiếu', dataIndex: 'quantityMissing', align: 'right' },
                  {
                    title: 'Độ dài thiếu',
                    align: 'right',
                    render: (_, row) => `${formatMeters(row.quantityMissing * row.cutLengthMm)} m`,
                  },
                ]}
              />
            </Card>
          </>
        )}
      </Spin>
    </div>
  )
}

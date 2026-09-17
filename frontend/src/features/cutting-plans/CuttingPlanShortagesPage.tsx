import { ArrowLeftOutlined, DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, App, Button, Input, Select, Space, Spin, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { extractBlobErrorMessage, extractErrorMessage } from '../../api/apiError'
import { downloadFile } from '../../api/downloadFile'
import { exportShortageReport, getCuttingPlan } from './cuttingPlansApi'
import type { CuttingPlanResponse } from './types'

/**
 * Màn phụ riêng (docs/requirements-functional.md dòng 19) — tách khỏi trang chi tiết chính, chỉ
 * phục vụ chốt danh sách vật tư cần sản xuất bù, không cần nhìn tới chi tiết cách cắt từng phôi.
 */
export function CuttingPlanShortagesPage() {
  const { id } = useParams<{ id: string }>()
  const planId = Number(id)
  const { message } = App.useApp()

  const [plan, setPlan] = useState<CuttingPlanResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [exporting, setExporting] = useState(false)

  const [keyword, setKeyword] = useState('')
  const [materialFilter, setMaterialFilter] = useState<number | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const loaded = await getCuttingPlan(planId)
      setPlan(loaded)
      setLoadError(null)
    } catch (error) {
      setLoadError(extractErrorMessage(error, 'Không tải được danh sách đơn thiếu vật tư.'))
    } finally {
      setLoading(false)
    }
  }, [planId])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  const materialOptions = useMemo(() => {
    if (!plan) {
      return []
    }
    const seen = new Map<number, string>()
    plan.shortages.forEach((s) => seen.set(s.slatMaterialId, s.slatMaterialName))
    return Array.from(seen.entries()).map(([value, label]) => ({ value, label }))
  }, [plan])

  const filtered = useMemo(() => {
    if (!plan) {
      return []
    }
    const needle = keyword.trim().toLowerCase()
    return plan.shortages
      .filter((s) => !materialFilter || s.slatMaterialId === materialFilter)
      .filter((s) => !needle || s.ycsx.toLowerCase().includes(needle) || s.customerName.toLowerCase().includes(needle))
      .sort((a, b) => a.reqdDeliveryDate.localeCompare(b.reqdDeliveryDate))
  }, [plan, keyword, materialFilter])

  async function handleExport() {
    if (!plan) {
      return
    }
    setExporting(true)
    try {
      const blob = await exportShortageReport(plan.id)
      downloadFile(blob, `bao-cao-thieu-vat-tu-${plan.id}.xlsx`)
    } catch (error) {
      message.error(await extractBlobErrorMessage(error, 'Không xuất được báo cáo.'))
    } finally {
      setExporting(false)
    }
  }

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
      <Link to={`/cutting-plans/${plan.id}`}>
        <ArrowLeftOutlined /> Quay lại chi tiết phương án cắt
      </Link>

      <Space style={{ width: '100%', justifyContent: 'space-between', marginTop: 12 }} align="start">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Đơn thiếu vật tư — #CP-{plan.id}
          </Typography.Title>
          <Typography.Text type="secondary">
            Căn cứ lập lệnh sản xuất thanh nan bù nhập vào tồn kho.
          </Typography.Text>
        </div>
        <Button type="primary" icon={<DownloadOutlined />} loading={exporting} onClick={() => void handleExport()}>
          Xuất báo cáo
        </Button>
      </Space>

      {loadError && <Alert type="error" showIcon style={{ margin: '16px 0' }} title={loadError} />}

      <Space style={{ margin: '16px 0' }} wrap>
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
          style={{ width: 220 }}
          value={materialFilter}
          onChange={(value) => setMaterialFilter(value ?? null)}
          options={materialOptions}
        />
      </Space>

      <Table
        rowKey="id"
        dataSource={filtered}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `${total} đơn thiếu vật tư` }}
        columns={[
          {
            title: 'Lệnh SX / Bộ cửa',
            render: (_, s) => (
              <div>
                <div style={{ fontWeight: 600 }}>{s.ycsx}</div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>Bộ cửa #{s.item}</div>
              </div>
            ),
          },
          { title: 'Khách hàng', dataIndex: 'customerName' },
          { title: 'Loại thanh nan', dataIndex: 'slatMaterialName' },
          { title: 'SL thiếu', dataIndex: 'missingQuantity', align: 'right' },
          {
            title: 'Độ dài thiếu',
            align: 'right',
            render: (_, s) => `${s.missingLengthM.toFixed(2)} m`,
          },
          {
            title: 'Ngày giao yêu cầu',
            dataIndex: 'reqdDeliveryDate',
            render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
          },
        ]}
      />
    </div>
  )
}

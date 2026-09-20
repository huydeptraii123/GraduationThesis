import { ThunderboltOutlined } from '@ant-design/icons'
import { Alert, Button, DatePicker, Input, Select, Space, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { tablePagination, type PageParams } from '../../api/pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { usePagedList } from '../../hooks/usePagedList'
import { useAuth } from '../auth/AuthContext'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { canGenerateCuttingPlan } from '../auth/permissions'
import { GenerateCuttingPlanModal } from './GenerateCuttingPlanModal'
import { listCuttingPlans } from './cuttingPlansApi'
import type { CuttingPlanStatus } from './types'

/** Id không bao giờ tồn tại (khóa chính luôn dương) — dùng để ép bộ lọc trả về rỗng. */
const NO_MATCH_PLAN_ID = -1

const STATUS_LABEL: Record<CuttingPlanStatus, { text: string; color: string }> = {
  COMPLETED: { text: 'Hoàn tất', color: 'success' },
  FAILED: { text: 'Thất bại', color: 'error' },
}

export function CuttingPlanListPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  // Chỉ PLANNER chạy thuật toán sinh phương án cắt, khớp @PreAuthorize của POST /generate.
  const canGenerate = canGenerateCuttingPlan(user)

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<CuttingPlanStatus | null>(null)
  const [runRange, setRunRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [generateOpen, setGenerateOpen] = useState(false)

  const debouncedKeyword = useDebouncedValue(keyword)
  // Ô tìm kiếm nay chỉ tra mã lần chạy: lấy phần số trong "#CP-12" / "cp-12" / "12". Việc lọc theo
  // thời gian chuyển hẳn sang bộ chọn khoảng ngày bên cạnh — chuỗi ngày đã định dạng (DD/MM/YYYY)
  // không phải thứ CSDL so khớp được.
  const planIdFilter = (() => {
    const typed = debouncedKeyword.trim()
    if (!typed) {
      return null
    }
    const digits = typed.replace(/\D/g, '')
    // Không có chữ số nào, hoặc dài hơn mọi id có thật: đây là từ khóa không thể khớp mã nào. Trả
    // NO_MATCH để bảng báo "không có dữ liệu" — bỏ lọc trong trường hợp này sẽ hiện ra TOÀN BỘ lần
    // chạy, đúng cái người dùng vừa cố loại đi. (Id quá 15 chữ số còn làm backend trả 400 vì không
    // ép được sang kiểu số của Java.)
    if (!digits || digits.length > 15) {
      return NO_MATCH_PLAN_ID
    }
    return Number(digits)
  })()
  const runFrom = runRange ? runRange[0].format('YYYY-MM-DD') : null
  const runTo = runRange ? runRange[1].format('YYYY-MM-DD') : null

  const load = useCallback(
    (params: PageParams) =>
      listCuttingPlans({ ...params, planId: planIdFilter, status: statusFilter, runFrom, runTo }),
    [planIdFilter, statusFilter, runFrom, runTo],
  )
  const { data, loading, error: loadError, current, pageSize, handleTableChange, reload } = usePagedList(
    load,
    [planIdFilter, statusFilter, runFrom, runTo],
    { initialPageSize: 10, errorMessage: 'Không tải được danh sách phương án cắt.' },
  )

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
          placeholder="Tìm theo mã phương án #CP-"
          style={{ width: 240 }}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <DatePicker.RangePicker
          placeholder={['Chạy từ ngày', 'đến ngày']}
          value={runRange}
          onChange={(value) => setRunRange(value && value[0] && value[1] ? [value[0], value[1]] : null)}
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
        dataSource={data.content}
        pagination={tablePagination({ current, pageSize, total: data.totalElements }, (total) => `${total} lần chạy`)}
        onChange={handleTableChange}
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
          reload()
          navigate(`/cutting-plans/${newPlanId}`)
        }}
      />
    </div>
  )
}

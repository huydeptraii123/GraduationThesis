import { InboxOutlined } from '@ant-design/icons'
import { Alert, Button, Modal, Table, Typography, Upload } from 'antd'
import { useState } from 'react'
import { extractErrorMessage, extractImportRowErrors, type ImportRowError } from '../../api/apiError'
import { importInventoryExcel } from './inventoryApi'
import type { InventoryImportResult } from './types'

/** Khớp với spring.servlet.multipart.max-file-size của backend và client_max_body_size của nginx. */
const MAX_FILE_BYTES = 10 * 1024 * 1024

interface Props {
  open: boolean
  onClose: () => void
  onImported: () => void
}

export function ImportInventoryModal({ open, onClose, onImported }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [importing, setImporting] = useState(false)
  const [result, setResult] = useState<InventoryImportResult | null>(null)
  const [rowErrors, setRowErrors] = useState<ImportRowError[] | null>(null)
  const [errorText, setErrorText] = useState<string | null>(null)

  function resetOutcome() {
    setResult(null)
    setRowErrors(null)
    setErrorText(null)
  }

  function handleClose() {
    setFile(null)
    resetOutcome()
    onClose()
  }

  async function handleImport() {
    if (!file) {
      return
    }
    setImporting(true)
    resetOutcome()
    try {
      const imported = await importInventoryExcel(file)
      setResult(imported)
      onImported()
    } catch (error) {
      const errors = extractImportRowErrors(error)
      if (errors) {
        setRowErrors(errors)
      } else {
        setErrorText(extractErrorMessage(error, 'Không nhập được file tồn kho.'))
      }
    } finally {
      setImporting(false)
    }
  }

  return (
    <Modal
      title="Nhập tồn kho từ file Excel"
      open={open}
      onCancel={handleClose}
      width={560}
      footer={[
        <Button key="close" onClick={handleClose}>
          Đóng
        </Button>,
        <Button key="import" type="primary" disabled={!file} loading={importing} onClick={handleImport}>
          Nhập dữ liệu
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        title="Mỗi lần nhập là một ảnh chụp tồn kho tại thời điểm xuất file"
        description="Các tổ hợp loại thanh nan và chiều dài không còn xuất hiện trong file sẽ được đưa về 0 thanh, không phải cộng dồn vào tồn kho hiện có."
      />

      <Upload.Dragger
        accept=".xlsx,.xls"
        maxCount={1}
        fileList={file ? [{ uid: '1', name: file.name }] : []}
        beforeUpload={(selected) => {
          const picked = selected as unknown as File
          resetOutcome()
          // Chặn tại chỗ thay vì đẩy cả file lên rồi mới nhận lỗi từ proxy/backend.
          if (picked.size > MAX_FILE_BYTES) {
            setFile(null)
            setErrorText('File vượt quá 10MB, vui lòng xuất lại file gọn hơn từ hệ thống nguồn.')
            return false
          }
          setFile(picked)
          return false
        }}
        onRemove={() => {
          setFile(null)
          resetOutcome()
        }}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p>Kéo thả file vào đây hoặc bấm để chọn file</p>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Định dạng .xlsx hoặc .xls, tối đa 10MB
        </Typography.Text>
      </Upload.Dragger>

      {result && (
        <Alert
          style={{ marginTop: 16 }}
          type="success"
          showIcon
          title="Nhập tồn kho thành công"
          description={`Đã ghi nhận ${result.totalRowsImported.toLocaleString('vi-VN')} dòng; ${result.zeroedOutCount.toLocaleString('vi-VN')} tổ hợp không còn trong file đã được đưa về 0 thanh.`}
        />
      )}

      {errorText && <Alert style={{ marginTop: 16 }} type="error" showIcon title={errorText} />}

      {rowErrors && (
        <>
          <Alert
            style={{ marginTop: 16 }}
            type="error"
            showIcon
            title={`File có ${rowErrors.length} dòng dữ liệu không hợp lệ`}
            description="Không bản ghi nào được lưu — toàn bộ lượt nhập bị hủy nếu còn bất kỳ dòng lỗi nào. Sửa file rồi chọn lại."
          />
          <Table
            style={{ marginTop: 12 }}
            size="small"
            rowKey={(row) => `${row.rowNumber}-${row.message}`}
            dataSource={rowErrors}
            pagination={rowErrors.length > 10 ? { pageSize: 10 } : false}
            scroll={{ y: 240 }}
            columns={[
              { title: 'Dòng', dataIndex: 'rowNumber', width: 80 },
              { title: 'Lỗi', dataIndex: 'message' },
            ]}
          />
        </>
      )}
    </Modal>
  )
}

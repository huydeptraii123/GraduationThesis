import { DownloadOutlined } from '@ant-design/icons'
import { App, Button } from 'antd'
import { useState } from 'react'
import { extractBlobErrorMessage } from '../api/apiError'
import { downloadFile } from '../api/downloadFile'

interface Props {
  /** Gọi endpoint `…/import/template` của luồng nhập tương ứng. */
  download: () => Promise<Blob>
  filename: string
}

/**
 * Nút "Tải file mẫu" đặt trong hộp thoại nhập Excel: tải file mẫu hợp lệ của đúng luồng nhập đó để
 * người dùng điền dữ liệu vào rồi nhập lại. Tên file đặt ở đây (không đọc Content-Disposition) cho
 * giống các nút xuất Excel khác của ứng dụng.
 */
export function DownloadTemplateButton({ download, filename }: Props) {
  const { message } = App.useApp()
  const [downloading, setDownloading] = useState(false)

  async function handleClick() {
    setDownloading(true)
    try {
      downloadFile(await download(), filename)
    } catch (error) {
      message.error(await extractBlobErrorMessage(error, 'Không tải được file mẫu.'))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <Button icon={<DownloadOutlined />} loading={downloading} onClick={handleClick}>
      Tải file mẫu
    </Button>
  )
}

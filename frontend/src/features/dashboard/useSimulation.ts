import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import type { CuttingPlanPreviewResponse } from '../cutting-plans/types'
import { clearLastSimulation, getLastSimulation, getPendingSimulation, runSimulation } from './simulationStore'

/**
 * Trạng thái lần tính phương án cắt cho một màn hình — trang chủ và màn báo cáo thiếu vật tư toàn
 * hệ thống dùng chung, nên cả hai đọc cùng một kết quả trong {@link getLastSimulation}: tính ở màn
 * này thì sang màn kia thấy ngay, không chạy lại thuật toán. Cố ý KHÔNG tự tính khi mở màn.
 */
export function useSimulation() {
  const [simulation, setSimulation] = useState<CuttingPlanPreviewResponse | null>(() => getLastSimulation())
  const [computing, setComputing] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const latestRunId = useRef(0)

  const compute = useCallback(async () => {
    const runId = ++latestRunId.current
    setComputing(true)
    try {
      const computed = await runSimulation()
      if (runId !== latestRunId.current) {
        return
      }
      setSimulation(computed)
      setLoadError(null)
    } catch (error) {
      if (runId !== latestRunId.current) {
        return
      }
      // Xóa hẳn kết quả cũ: giữ lại nghĩa là màn hình hiện số liệu của một lần tính khác trong khi
      // nhãn lỗi nói lần này thất bại — người đọc không có cách nào biết mình đang nhìn cái nào.
      clearLastSimulation()
      setSimulation(null)
      setLoadError(extractErrorMessage(error, 'Không tính được phương án cắt.'))
    } finally {
      if (runId === latestRunId.current) {
        setComputing(false)
      }
    }
  }, [])

  // Nối lại vào lượt tính đang chạy dở khi người dùng rời màn giữa chừng rồi quay lại. Không có
  // bước này thì màn hình hiện trạng thái trống trong lúc máy chủ vẫn đang chạy, và người dùng bấm
  // tính thêm một lượt nữa cho cùng một câu hỏi. Cố ý KHÔNG tự chạy lượt mới ở đây.
  useEffect(() => {
    if (getPendingSimulation() == null) {
      return
    }
    // Đây đúng là trường hợp effect dùng để đồng bộ với một nguồn bên ngoài React (bộ nhớ module
    // đang giữ lượt tính dở), nên việc đặt state ngay trong effect là có chủ đích.
    // oxlint-disable-next-line react/set-state-in-effect
    void compute()
  }, [compute])

  return { simulation, computing, loadError, compute }
}

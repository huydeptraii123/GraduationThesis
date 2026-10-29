package com.slatcut.cutting.service;

import com.slatcut.cutting.config.UnprocessableRequestException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStockSnapshot;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.dto.DataResetResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đặt lại dữ liệu vận hành theo yêu cầu của doanh nghiệp: xóa sạch đơn hàng, khách hàng, tồn kho và
 * toàn bộ lịch sử duyệt phương án cắt, để nhập lại từ đầu khi đơn hàng đã đổi (khách đổi kích thước
 * cửa sau khi đã nhập). Danh mục dùng chung — định mức, mẫu cửa, loại thanh nan — cùng tài khoản và
 * vai trò được GIỮ NGUYÊN: chúng không đổi theo đơn hàng, và nhập lại định mức là việc của ADMIN với
 * một file khác hẳn.
 *
 * <p>Xóa bằng câu DELETE hàng loạt từng bảng theo đúng thứ tự khóa ngoại (bảng con trước bảng cha),
 * trong MỘT transaction: hỏng ở bảng nào thì cả lần đặt lại quay lui, không có trạng thái nửa vời
 * kiểu đơn hàng đã mất mà phương án đã duyệt vẫn còn trỏ tới. Không dùng TRUNCATE vì nó tự commit
 * và đòi tắt kiểm tra khóa ngoại — đúng hai thứ khiến thứ tự xóa không còn được bảo vệ.
 *
 * <p>Gom tại một service dùng {@link EntityManager} thay vì thêm một truy vấn xóa vào từng
 * repository: thao tác này nói về cả một nhóm bảng chứ không về một aggregate nào, và thứ tự giữa
 * tám câu lệnh mới là phần cần đọc được ở một chỗ.
 */
@Service
public class DataResetService {

    /**
     * Cụm người dùng phải gõ để xác nhận. Không dấu để không phụ thuộc bộ gõ tiếng Việt (gõ có dấu
     * có thể sinh dạng Unicode tổ hợp khác, so chuỗi sẽ lệch dù nhìn giống hệt). Giao diện giữ bản
     * sao đối ứng ở {@code DataResetPage}.
     */
    public static final String CONFIRMATION_PHRASE = "XOA DU LIEU";

    private static final Logger log = LoggerFactory.getLogger(DataResetService.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * @param confirmation cụm xác nhận người dùng đã gõ — phải trùng đúng {@link #CONFIRMATION_PHRASE}
     * @param requestedBy tên đăng nhập của người thao tác, chỉ để ghi log
     * @throws UnprocessableRequestException khi cụm xác nhận sai — không dòng nào bị xóa
     */
    @Transactional
    public DataResetResult resetInventoryAndOrders(String confirmation, String requestedBy) {
        if (!CONFIRMATION_PHRASE.equals(confirmation)) {
            throw new UnprocessableRequestException(
                    "Cụm xác nhận không đúng. Hãy gõ chính xác \"" + CONFIRMATION_PHRASE + "\" để đặt lại dữ liệu.");
        }
        // Đẩy mọi thay đổi đang chờ xuống trước, rồi sau cùng dọn persistence context: câu DELETE
        // hàng loạt đi thẳng xuống cơ sở dữ liệu, bỏ qua mọi entity đã nạp trong transaction này.
        entityManager.flush();
        int cuttingPlanDetailItems = deleteAll(CuttingPlanDetailItem.class);
        int shortageRecords = deleteAll(ShortageRecord.class);
        int stockSnapshots = deleteAll(CuttingPlanStockSnapshot.class);
        int cuttingPlanDetails = deleteAll(CuttingPlanDetail.class);
        // Đơn hàng trỏ tới phương án đã duyệt (approved_plan_id) nên phải đi trước CuttingPlan.
        int salesOrders = deleteAll(SalesOrder.class);
        int cuttingPlans = deleteAll(CuttingPlan.class);
        int customers = deleteAll(Customer.class);
        int inventoryBatches = deleteAll(InventoryBatch.class);
        entityManager.clear();

        DataResetResult result = new DataResetResult(
                salesOrders,
                customers,
                inventoryBatches,
                cuttingPlans,
                cuttingPlanDetails,
                cuttingPlanDetailItems,
                shortageRecords,
                stockSnapshots);
        log.warn("Người dùng {} đã đặt lại dữ liệu vận hành: {}", requestedBy, result);
        return result;
    }

    private int deleteAll(Class<?> entityClass) {
        String entityName = entityManager.getMetamodel().entity(entityClass).getName();
        return entityManager.createQuery("DELETE FROM " + entityName).executeUpdate();
    }
}

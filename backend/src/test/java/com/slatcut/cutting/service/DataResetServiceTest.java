package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.UnprocessableRequestException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.DataResetResult;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.CuttingPlanStockSnapshotRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.RoleRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Đặt lại dữ liệu vận hành: tám bảng vận hành về 0, danh mục dùng chung và tài khoản không đổi.
 *
 * <p>Dữ liệu dựng sao cho CẢ tám bảng đều có dòng trước khi đặt lại (khẳng định &gt; 0 trước khi gọi):
 * một đợt duyệt thật — một đơn cắt được, một đơn thiếu vật tư — sinh đủ phương án, chi tiết cắt,
 * dòng nối đơn hàng, dòng thiếu vật tư và ảnh chụp tồn kho; thêm một đơn giao xa nằm ngoài đợt duyệt
 * và một lô tồn kho của loại thanh nan khác. Thiếu bảng nào thì phép khẳng định "về 0" ở bảng đó
 * đúng một cách vô nghĩa.
 */
class DataResetServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DataResetService service;

    @Autowired
    private CuttingPlanService cuttingPlanService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private BomItemRepository bomItemRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private CuttingPlanRepository cuttingPlanRepository;

    @Autowired
    private CuttingPlanDetailRepository cuttingPlanDetailRepository;

    @Autowired
    private CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;

    @Autowired
    private ShortageRecordRepository shortageRecordRepository;

    @Autowired
    private CuttingPlanStockSnapshotRepository cuttingPlanStockSnapshotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private long counter = 0;

    private Customer persistCustomer() {
        Customer entity = new Customer();
        entity.setCustomer(90_000_000L + ++counter);
        entity.setCustomerName("Khách hàng " + counter);
        return customerRepository.save(entity);
    }

    private DoorProduct persistDoorProduct() {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(80_000_000L + ++counter);
        entity.setDoorMaterialName("Cửa cuốn " + counter);
        entity.setMauSac("#01");
        return doorProductRepository.save(entity);
    }

    private SlatMaterial persistSlatMaterial() {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Thanh nan " + counter);
        entity.setSlatGroup(SlatGroup.BOTTOM_BAR);
        return slatMaterialRepository.save(entity);
    }

    /** BOTTOM_BAR dùng thẳng chiều rộng cửa làm độ dài đoạn cắt, mỗi bộ cửa 1 đoạn. */
    private void persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        bomItemRepository.save(entity);
    }

    private void persistInventoryBatch(SlatMaterial slatMaterial, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        inventoryBatchRepository.save(entity);
    }

    private void persistSalesOrder(DoorProduct doorProduct, Customer customer, LocalDate reqdDeliveryDate) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx("HY9" + (counter + 1));
        entity.setItem((int) ++counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.500"));
        entity.setChieuRongDh(new BigDecimal("2.345"));
        entity.setReqdDeliveryDate(reqdDeliveryDate);
        salesOrderRepository.save(entity);
    }

    /** Một đợt duyệt thật + đơn ngoài đợt + tồn kho của loại khác — đủ để cả tám bảng có dòng. */
    private void persistOperationalData() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        // Một thanh 2.400mm: đơn đầu cắt được (dư 55mm, bỏ đi), đơn thứ hai thiếu vật tư.
        persistInventoryBatch(slatMaterial, 2400, 1);
        persistInventoryBatch(persistSlatMaterial(), 6000, 3);
        persistSalesOrder(doorProduct, customer, LocalDate.now());
        persistSalesOrder(doorProduct, customer, LocalDate.now());
        persistSalesOrder(doorProduct, persistCustomer(), LocalDate.now().plusDays(60));
        cuttingPlanService.approve(cuttingPlanService.approvalPreview().stateFingerprint());
    }

    private long[] operationalCounts() {
        return new long[] {
            salesOrderRepository.count(),
            customerRepository.count(),
            inventoryBatchRepository.count(),
            cuttingPlanRepository.count(),
            cuttingPlanDetailRepository.count(),
            cuttingPlanDetailItemRepository.count(),
            shortageRecordRepository.count(),
            cuttingPlanStockSnapshotRepository.count()
        };
    }

    private List<Long> masterDataCounts() {
        return List.of(
                bomItemRepository.count(),
                doorProductRepository.count(),
                slatMaterialRepository.count(),
                userRepository.count(),
                roleRepository.count());
    }

    @Test
    void reset_emptiesEveryOperationalTable_andKeepsMasterDataAndAccounts() {
        persistOperationalData();
        long[] before = operationalCounts();
        List<Long> masterBefore = masterDataCounts();
        assertThat(before).as("mọi bảng vận hành phải có dòng trước khi đặt lại").doesNotContain(0L);

        DataResetResult result = service.resetInventoryAndOrders(DataResetService.CONFIRMATION_PHRASE, "admin");

        assertThat(new long[] {
                    result.salesOrders(),
                    result.customers(),
                    result.inventoryBatches(),
                    result.cuttingPlans(),
                    result.cuttingPlanDetails(),
                    result.cuttingPlanDetailItems(),
                    result.shortageRecords(),
                    result.stockSnapshots()
                })
                .as("số dòng báo đã xóa phải đúng bằng số dòng có trước đó, từng bảng")
                .containsExactly(before);
        assertThat(operationalCounts()).containsOnly(0L);
        assertThat(masterDataCounts()).isEqualTo(masterBefore);
    }

    /**
     * Cụm xác nhận so khớp chính xác: viết thường, thừa khoảng trắng hay gõ có dấu đều bị từ chối, và
     * từ chối thì không dòng nào bị xóa.
     */
    @Test
    void reset_rejectsAnythingButTheExactConfirmationPhrase_andDeletesNothing() {
        persistOperationalData();
        long[] before = operationalCounts();

        for (String wrong : List.of("xoa du lieu", " XOA DU LIEU", "XÓA DỮ LIỆU", "XOA")) {
            assertThatThrownBy(() -> service.resetInventoryAndOrders(wrong, "admin"))
                    .isInstanceOf(UnprocessableRequestException.class);
        }

        assertThat(operationalCounts()).containsExactly(before);
    }

    /** Sau khi đặt lại, nhập dữ liệu mới và duyệt lại vẫn chạy bình thường trên các danh mục còn giữ. */
    @Test
    void reset_leavesMasterDataUsableForTheNextApproval() {
        persistOperationalData();
        service.resetInventoryAndOrders(DataResetService.CONFIRMATION_PHRASE, "admin");

        // Lấy cả mẫu cửa lẫn loại thanh nan từ CHÍNH dòng định mức còn giữ, không chọn rời từng bảng.
        BomItem keptBom = bomItemRepository.findAll().get(0);
        DoorProduct keptProduct = keptBom.getDoorProduct();
        SlatMaterial keptMaterial = keptBom.getSlatMaterial();
        persistInventoryBatch(keptMaterial, 2400, 1);
        persistSalesOrder(keptProduct, persistCustomer(), LocalDate.now());

        cuttingPlanService.approve(cuttingPlanService.approvalPreview().stateFingerprint());

        assertThat(cuttingPlanRepository.count()).isEqualTo(1);
        assertThat(cuttingPlanDetailRepository.count()).isEqualTo(1);
        assertThat(shortageRecordRepository.count()).isZero();
    }
}

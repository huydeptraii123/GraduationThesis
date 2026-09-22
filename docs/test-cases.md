# Kiểm thử hệ thống — kịch bản và kết quả

Tài liệu này là phần bằng chứng kiểm thử của khóa luận: mỗi luồng nghiệp vụ đã đặc tả ở các tài liệu thiết kế được quy về một hoặc vài **kịch bản kiểm thử** có thể chạy lại, kèm kết quả thực tế quan sát được.

## 1. Cách đọc bảng

Mỗi bảng có 5 cột: **Mã** · **Kịch bản** (gồm cả tiền điều kiện khi nó không hiển nhiên) · **Kết quả mong đợi** · **Kết quả** · **Bằng chứng**.

Cột **Bằng chứng** chỉ nhận đúng hai loại, không có loại thứ ba:

- **(A) Kiểm thử tự động** — chạy bằng lệnh kiểm thử của dự án, trên **MySQL thật** dựng bằng Testcontainers chứ không phải cơ sở dữ liệu trong bộ nhớ hay đối tượng giả lập. Ô bằng chứng ghi tên lớp kiểm thử và tên phương thức, tra ngược được trong mã nguồn.
- **(B) Chạy thật** — thao tác trên hệ thống đang chạy đầy đủ (cơ sở dữ liệu, máy chủ ứng dụng, giao diện web) với **bộ dữ liệu thật của doanh nghiệp**, kèm số liệu quan sát được ghi thẳng vào cột kết quả.

Kịch bản nào chưa gắn được vào (A) hoặc (B) thì không được ghi "Đạt" theo suy luận — nó phải được chạy để lấy kết quả, hoặc nêu thành hạn chế ở mục 7.

## 2. Môi trường và dữ liệu kiểm thử

| Thành phần | Cấu hình |
|---|---|
| Cơ sở dữ liệu | MySQL 8.0 (Docker), lược đồ do Flyway dựng từ 14 phiên bản di trú |
| Máy chủ ứng dụng | Spring Boot, JDK 23 |
| Giao diện | React + TypeScript + Ant Design, kiểm bằng trình duyệt thật điều khiển tự động |
| Kiểm thử tự động | **428 ca, 0 thất bại, 0 lỗi** trên 23 lớp kiểm thử |

Dữ liệu dùng cho nhóm (B) là bộ dữ liệu thật của doanh nghiệp, **nhập lại từ đầu trên một lược đồ trống** ngay trước đợt kiểm thử này, để mọi con số trong tài liệu thuộc về cùng một lần nhập và cùng một phiên bản mã nguồn:

| Nguồn | Nhập vào | Đối chiếu với file nguồn |
|---|---|---|
| Định mức vật tư | 1.860 dòng | 1.860 dòng dữ liệu — khớp 1:1 |
| Tồn kho thanh nan | 2.283 lô · 60.851 thanh | 2.283 dòng dữ liệu — khớp 1:1 |
| Đơn hàng cửa | **190 bộ cửa** (21 khách hàng) | 738 dòng dữ liệu = 548 dòng không phải cửa + **190 dòng cửa**; 190 khóa (lô sản xuất, số thứ tự bộ cửa) khác nhau, **0 dòng trùng khóa** |
| Mẫu cửa | 392 mẫu | sinh ra từ hai file trên |

Thứ tự nhập **định mức → tồn kho → đơn hàng** là bắt buộc và đã được tuân thủ (xem TC-IMP-07).

Trong đợt kiểm thử có **hai thời điểm đo** khác nhau, và mỗi ô kết quả đều nói rõ nó thuộc thời điểm nào: *trước đợt duyệt* (toàn bộ 182 bộ cửa còn trong phạm vi tính) và *sau đợt duyệt* (70 bộ cửa đã rời hàng chờ, còn lại 112). Bỏ qua khác biệt này thì hai con số cùng nói về "bản tính" sẽ mâu thuẫn nhau mà không rõ vì sao.

## 3. Tổng hợp kết quả

| Nhóm | Số ca | Đạt | Không đạt |
|---|---|---|---|
| Xác thực và phiên làm việc (TC-AUTH) | 6 | 6 | 0 |
| Phân quyền theo vai trò (TC-PERM) | 6 | 6 | 0 |
| Nhập dữ liệu từ Excel (TC-IMP) | 9 | 9 | 0 |
| Tồn kho và định mức (TC-DAT) | 7 | 7 | 0 |
| Đơn hàng (TC-SO) | 6 | 6 | 0 |
| Thuật toán cắt (TC-ALG) | 12 | 12 | 0 |
| Tính phương án cắt (TC-SIM) | 6 | 6 | 0 |
| Duyệt phương án cắt (TC-APR) | 9 | 9 | 0 |
| Báo cáo và xuất Excel (TC-RPT) | 8 | 8 | 0 |
| Quản lý tài khoản (TC-USR) | 6 | 6 | 0 |
| Yêu cầu phi chức năng (TC-NFR) | 8 | 8 | 0 |
| **Tổng** | **83** | **83** | **0** |

## 4. Kịch bản kiểm thử theo nhóm

### 4.1. Xác thực và phiên làm việc

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-AUTH-01 | Đăng nhập bằng tài khoản hợp lệ | Trả về thẻ truy cập kèm vai trò của tài khoản | Đạt | (A) `AuthServiceTest.login_returnsTokenAndRoleOfTheAccount` |
| TC-AUTH-02 | Gọi API nghiệp vụ khi chưa đăng nhập | Từ chối với mã 401, không phải 403 | Đạt | (A) `RolePermissionMatrixTest.endpoint_withoutToken_isUnauthorized` (chạy trên toàn bộ endpoint đã đăng ký trong ma trận, cả đọc lẫn ghi) |
| TC-AUTH-03 | Đổi mật khẩu của chính mình | Mật khẩu cũ hết hiệu lực, mật khẩu mới đăng nhập được | Đạt | (A) `AuthServiceTest.changePassword_replacesTheOldPasswordEverywhere` · (B) đổi và khôi phục trên giao diện thật, hệ thống báo "Đã đổi mật khẩu" |
| TC-AUTH-04 | Đổi mật khẩu nhưng nhập sai mật khẩu hiện tại | Từ chối, mật khẩu cũ vẫn dùng được | Đạt | (A) `AuthServiceTest.changePassword_rejectsAWrongCurrentPassword` |
| TC-AUTH-05 | Mật khẩu lưu trong cơ sở dữ liệu | Chỉ lưu bản băm, không bao giờ lưu bản rõ | Đạt | (A) `AuthServiceTest.changePassword_storesAHashNeverThePlainText` |
| TC-AUTH-06 | Tài khoản bị khóa nhưng vẫn giữ thẻ truy cập còn hạn | Bị từ chối **ngay lập tức**, không chờ thẻ hết hạn | Đạt | (A) `JwtAuthenticationFilterTest.tokenOfALockedAccount_isRejectedImmediately` |

### 4.2. Phân quyền theo vai trò

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-PERM-01 | Gọi mọi endpoint ghi bằng từng vai trò | Mỗi endpoint chỉ chấp nhận đúng vai trò đã đặc tả, vai trò còn lại nhận 403 | Đạt | (A) `RolePermissionMatrixTest.endpoint_enforcesDeclaredRole` — chạy tham số hóa trên toàn bộ ma trận |
| TC-PERM-02 | Thêm một endpoint ghi mới mà quên khai báo quyền | Bộ kiểm thử phải phát hiện được, không để lọt | Đạt | (A) `RolePermissionMatrixTest.matrix_coversEveryWriteEndpointRegisteredBySpring` và `everyWriteHandler_declaresPreAuthorize` |
| TC-PERM-03 | Tài khoản Kế hoạch mở màn định mức vật tư | Xem được, nhưng thấy lời giải thích vì sao không sửa được | Đạt | (B) hiện dải "Chỉ tài khoản Quản trị (ADMIN) mới thêm/sửa/xóa định mức BOM." |
| TC-PERM-04 | Tài khoản Quản trị mở màn tồn kho | Xem được, kèm lời giải thích tương ứng | Đạt | (B) hiện dải "Chỉ tài khoản Kế hoạch (PLANNER) mới thêm/sửa/xóa lô tồn kho." |
| TC-PERM-05 | Tài khoản Quản trị mở màn phương án cắt | Xem được, không duyệt được, có giải thích | Đạt | (B) hiện dải "Chỉ tài khoản Kế hoạch (PLANNER) mới duyệt phương án cắt." |
| TC-PERM-06 | Tài khoản Kế hoạch tìm mục quản lý tài khoản | Không thấy mục trên trình đơn; vào thẳng địa chỉ thì bị chặn kèm giải thích | Đạt | (B) số mục "Người dùng" thấy được = **0**; vào thẳng nhận thông báo chặn |

### 4.3. Nhập dữ liệu từ Excel

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-IMP-01 | Nhập file định mức thật của doanh nghiệp | Ghi nhận đúng số dòng của file, không lỗi | Đạt — **1.860 dòng**, bằng đúng số dòng dữ liệu của file nguồn | (B) — ca tự động `BomImportServiceTest.importFromExcel_importsTheRealBomFileConsistently` chỉ khẳng định "có nhập được" và tự bỏ qua khi máy không có file thật, nên không dùng làm bằng chứng cho vế "đúng số dòng" |
| TC-IMP-02 | Nhập file tồn kho thật | Ghi nhận đúng số dòng của file | Đạt — **2.283 lô / 60.851 thanh** | (B) |
| TC-IMP-03 | Nhập file đơn hàng thật (trộn lẫn dòng cửa và dòng vật tư khác) | Chỉ lấy dòng cửa, báo rõ số dòng đã bỏ qua | Đạt — **190 đơn, bỏ qua 548 dòng không phải cửa**; đối chiếu file nguồn: 190 + 548 = 738 dòng dữ liệu | (B) |
| TC-IMP-04 | Nhập lại cùng một file lần thứ hai | Cập nhật tại chỗ theo khóa nghiệp vụ, không nhân đôi bản ghi | Đạt | (A) `SalesOrderImportServiceTest.importFromExcel_upsertsExistingOrderByYcsxItemKeepingItsId`, `InventoryImportServiceTest.importFromExcel_updatesExistingBatchInPlaceKeepingItsId` |
| TC-IMP-05 | File nhập thiếu cột bắt buộc, hoặc không phải file Excel | Báo lỗi nêu đủ các cột còn thiếu, kèm **số dòng thật** trong file | Đạt | (A) `InventoryImportServiceTest.importFromExcel_reportsEveryMissingRequiredColumn`, `.importFromExcel_throwsWhenFileIsNotAnExcelWorkbook`, `.importFromExcel_reportsErrorsUsingRealExcelRowNumbers` |
| TC-IMP-06 | File nhập **thiếu cột tùy chọn** (lệnh sản xuất) | Bỏ qua cột đó, **không** xóa giá trị đã có | Đạt | (A) `SalesOrderImportServiceTest.importFromExcel_acceptsFileWithoutTheOptionalProductionOrderColumn` và `.importFromExcel_doesNotWipeProductionOrderWhenTheColumnIsMissing` |
| TC-IMP-07 | Nhập đúng thứ tự định mức → tồn kho → đơn hàng | Mã vật tư lạ do tồn kho tạo ra không làm nhu cầu cắt bị bỏ qua âm thầm | Đạt — nhóm vật tư trong cơ sở dữ liệu khớp file nguồn | (B) · (A) `InventoryImportServiceTest.importFromExcel_neverOverwritesNameOrGroupOfExistingMaterial` |
| TC-IMP-08 | Lô tồn kho có trong hệ thống nhưng không còn trong file | Đưa về 0 thanh thay vì giữ nguyên số cũ | Đạt | (A) `InventoryImportServiceTest.importFromExcel_zeroesOutBatchesMissingFromTheFile` |
| TC-IMP-09 | File nhập có một dòng không hợp lệ ở giữa | **Hủy toàn bộ lượt nhập**, không ghi một dòng nào — tránh trạng thái nhập dở dang | Đạt | (A) `InventoryImportServiceTest.importFromExcel_writesNothingWhenAnyRowIsInvalid` |

### 4.4. Tồn kho và định mức vật tư

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-DAT-01 | Thêm lô tồn kho trùng (loại thanh nan, độ dài) đã có | Từ chối, báo trùng khóa nghiệp vụ | Đạt | (A) `InventoryBatchServiceTest.create_throwsConflictWhenSameMaterialAndLengthAlreadyExists` |
| TC-DAT-02 | Sửa lô tồn kho nhưng giữ nguyên khóa của chính nó | Không bị coi là trùng | Đạt | (A) `InventoryBatchServiceTest.update_keepingOwnKeyIsNotTreatedAsDuplicate` |
| TC-DAT-03 | Ô thống kê đầu màn tồn kho khi danh sách đã phân trang | Cộng trên **toàn bộ** tồn kho, không chỉ trang đang xem | Đạt | (A) `InventoryBatchServiceTest.getSummary_sumsEveryBatchNotJustOnePage` |
| TC-DAT-04 | Lọc danh sách tồn kho theo mã vật tư và nhóm vật tư | Trả đúng tập kết quả, thứ tự ổn định giữa các trang | Đạt | (A) `InventoryBatchServiceTest.getPage_filtersByMaterialCodeAndBySlatGroup`, `.getPage_splitsResultAcrossPagesInStableOrder` |
| TC-DAT-05 | Định mức của mẫu cửa thiếu hệ số tính số lượng nan | Bỏ qua đúng dòng định mức đó và không ném lỗi | Đạt | (A) `CuttingDemandServiceTest.buildDemands_mainSlatMissingSlopeOrIntercept_skipsWithoutError`; các dòng khác vẫn sinh nhu cầu bình thường — xem `.buildDemands_doorProductWithMultipleBomItems_returnsOneDemandPerBomItem` |
| TC-DAT-06 | Định mức thuộc nhóm vật tư không có công thức cắt | Luôn bỏ qua | Đạt | (A) `CuttingDemandServiceTest.buildDemands_otherGroup_alwaysSkipped` |
| TC-DAT-07 | Mẫu cửa chưa khai báo định mức nào | Không sinh nhu cầu cắt và không báo lỗi | Đạt | (A) `CuttingDemandServiceTest.buildDemands_doorProductWithoutAnyBomItem_producesNoDemandsWithoutError` |

### 4.5. Đơn hàng

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-SO-01 | Tạo đơn trùng khóa (lô sản xuất, số thứ tự bộ cửa) | Từ chối, nêu rõ khóa bị trùng | Đạt | (A) `SalesOrderServiceTest.create_throwsConflictWhenYcsxItemAlreadyExists` |
| TC-SO-02 | Danh sách đơn hàng sắp theo thứ tự ưu tiên cắt | Trả về đúng thứ tự ngày giao tăng dần, ổn định khi chia trang | Đạt | (A) `SalesOrderServiceTest.getPage_splitsResultAcrossPagesInDeliveryPriorityOrder` — ca này truyền tiêu chí sắp vào, nên nó khóa **phép sắp**, không khóa việc đó là thứ tự *mặc định* của endpoint (xem hạn chế 6) |
| TC-SO-03 | Lọc đơn theo khách hàng và khoảng ngày giao | Trả đúng tập kết quả | Đạt | (A) `SalesOrderServiceTest.getPage_filtersByCustomerAndDeliveryRange` |
| TC-SO-04 | Nhập lại file đơn hàng chứa **đơn đã duyệt** | Không đặt lại trạng thái đã duyệt của đơn đó | Đạt | (A) `SalesOrderImportServiceTest.importFromExcel_doesNotResetApprovedPlanOfAlreadyApprovedOrder` |
| TC-SO-05 | Nhập lại file có **dữ liệu khác** cho một đơn đã duyệt | Giữ nguyên đơn và báo xung đột cho người dùng | Đạt | (A) `SalesOrderImportServiceTest.importFromExcel_doesNotOverwriteApprovedOrderAndReportsConflict` |
| TC-SO-06 | Nhập lại file có dữ liệu **y hệt** cho đơn đã duyệt | Bỏ qua im lặng, không báo xung đột giả | Đạt | (A) `SalesOrderImportServiceTest.importFromExcel_reportsNoConflictWhenApprovedOrderDataIsUnchanged` |

### 4.6. Thuật toán cắt

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-ALG-01 | Mức 1 — khớp gần đúng, phần dư dưới 30cm | Cắt, phần dư xếp loại bỏ đi | Đạt | (A) `BestFitDecreasingStrategyTest.computePlan_nearFitAcceptsSmallNonZeroRemainder` |
| TC-ALG-02 | Mức 2 — nhiều đoạn cùng độ dài dùng chung một thanh bội số | Một thanh phục vụ nhiều đoạn, không phát sinh phần dư | Đạt | (A) `.computePlan_multipleOfSameLength_cutsTwoUnitsFromOneStock` |
| TC-ALG-03 | Mức 3 — ghép hai đoạn khác độ dài của hai đơn lên một thanh | Ghép đúng **hai** đoạn, dừng ở đối tác khớp đầu tiên | Đạt | (A) `.computePlan_combinesTwoDifferentLengthsFromOneStock`, `.computePlan_combination_firstMatchInPriorityOrderWinsOverBetterLaterMatch` |
| TC-ALG-04 | Mức 4 — cắt để phần dư trên 3m quay lại kho | Phần dư xếp loại nhập lại kho | Đạt | (A) `.computePlan_remainderAboveThreeMeters_classifiedAsRestock` |
| TC-ALG-05 | Phần dư **đúng 3.000mm** (biên dưới của "trên 3m") | Từ chối, chuyển thành thiếu vật tư | Đạt | (A) `.computePlan_remainderExactlyAtRestockThreshold_rejectedByLevel4BecomesShortage` |
| TC-ALG-06 | Phần dư **3.001mm** (hơn biên đúng 1mm) | Chấp nhận, nhập lại kho | Đạt | (A) `.computePlan_remainderOneMillimetreAboveRestockThreshold_acceptedByLevel4` |
| TC-ALG-07 | Phần dư đúng **300mm** (biên của ngưỡng bỏ đi) | Từ chối ở mức khớp gần đúng | Đạt | (A) `.computePlan_remainderExactlyAtDiscardThreshold_rejectedByNearFitThenBecomesShortage` |
| TC-ALG-08 | Trong kho **còn thanh đủ dài** nhưng cắt ra sẽ để lại phần dư 30cm–3m | **Báo thiếu vật tư**, giữ nguyên thanh đó, không hạ chuẩn để cắt | Đạt | (A) `.computePlan_onlyStockLeavingWasteRemainder_recordsShortageAndLeavesStockUntouched`, `.computePlan_noCombinationPartnerAndStockWouldLeaveWaste_recordsShortage` |
| TC-ALG-09 | Hai đơn tranh cùng một loại thanh nan khan hiếm | Đơn có ngày giao sớm hơn được phục vụ trước, đơn còn lại thành thiếu vật tư | Đạt | (A) `.computePlan_scarceStock_higherPriorityOrderWinsLowerPriorityBecomesShortage` |
| TC-ALG-10 | Hai đơn **cùng ngày giao** | Phân định tiếp theo lô sản xuất | Đạt | (A) `.computePlan_tieBreakSameDeliveryDate_ordersByYcsx` — tầng phân định cuối cùng theo số thứ tự bộ cửa chưa có ca riêng (xem hạn chế 6) |
| TC-ALG-11 | Phần dư vừa nhập lại kho được dùng cho đoạn sau **trong cùng lần chạy** | Đoạn sau dùng lại đúng phôi đó, phần dư được phân loại đúng | Đạt | (A) `.computePlan_restocksRemainderAboveThreeMeters_reusedByLaterOrderInSameRun` — vế cân bằng vật liệu do TC-NFR-01 chứng minh |
| TC-ALG-12 | Hai loại thanh nan khác nhau nhưng **cùng độ dài tồn kho** | Tồn kho cách ly theo từng loại, không dùng nhầm của nhau | Đạt | (A) `.computePlan_stockIsolatedBetweenMaterialsWithSameStockLength`, `InventoryPoolTest.stockIsIsolatedPerMaterial_evenWithSameLength` |

### 4.7. Tính phương án cắt

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-SIM-01 | Bấm tính trên toàn bộ đơn chưa duyệt | Không ghi **một dòng nào** xuống cơ sở dữ liệu | Đạt | (A) `CuttingPlanServiceTest.simulate_doesNotWriteAnythingToDatabase` |
| TC-SIM-02 | Phạm vi của chức năng tính | Lấy cả đơn có ngày giao xa và vượt hạn mức 70 đơn của một đợt duyệt | Đạt | (A) `.simulate_coversOrdersBeyondDeliveryCutoffAndSeventyOrderLimit`, `.findUnapproved_ignoresDeliveryCutoffAndSeventyOrderLimit` |
| TC-SIM-03 | Tính trên toàn bộ sổ đơn thật | Trả về bức tranh đáp ứng vật tư của mọi đơn đang chờ | Đạt — **182 bộ cửa trong phạm vi** (48 đủ nan / 134 thiếu nan), 748 dòng nhu cầu cắt, 8 đơn bị chặn vì thiếu định mức | (B) |
| TC-SIM-04 | Đơn thuộc mẫu cửa chưa có định mức dùng được | Không lẫn vào phạm vi, được **đếm riêng và cảnh báo** trên giao diện | Đạt — **8 đơn**, hiển thị tường minh | (A) `.simulate_countsOrdersBlockedByMissingBomSeparately` · (B) |
| TC-SIM-05 | Mở trang chủ mà chưa bấm nút | Hiện màn trống kèm lời giải thích, **không** tự chạy thuật toán | Đạt | (B) |
| TC-SIM-06 | Các khối biểu đồ trên trang chủ | Tổng của biểu đồ theo ngày giao, theo model và biểu đồ tỷ trọng đều bằng đúng số bộ cửa trên ô chỉ số | Đạt — đo sau đợt duyệt: **112 = 112 = 112 = 112** (2 ngày giao · 3 model · 32 đủ nan + 80 thiếu nan) | (B) |

### 4.8. Duyệt phương án cắt

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-APR-01 | Phạm vi của chức năng duyệt | Chỉ đơn chưa duyệt, ngày giao trong vòng 3 ngày, tối đa 70 đơn | Đạt — **70 đơn**, hạn giao đến ngày thứ ba kể từ ngày chạy | (A) `.approvalPreview_keepsDeliveryCutoffAndSeventyOrderLimit`, `.generate_limitsScopeToSeventyOrders_excludesLowestPriorityOrder` · (B) |
| TC-APR-02 | Duyệt phương án với dấu vân trạng thái còn khớp | Cả ba việc cùng xảy ra: ghi phương án, trừ tồn kho, đánh dấu đơn đã duyệt | Đạt — phương án ghi 70 đơn | (A) `.approve_writesPlanWhenFingerprintStillMatches` · (B). Mặt còn lại — bị từ chối thì **không** việc nào xảy ra — xem TC-APR-03 và TC-APR-08 |
| TC-APR-03 | Tồn kho bị sửa sau khi phương án đã được trình ra | Từ chối duyệt, báo dữ liệu đã thay đổi, **không ghi dòng nào** | Đạt | (A) `.approve_rejectsWhenInventoryChangedSincePreview` |
| TC-APR-04 | Một đơn giao gấp vừa được nhập thêm | Từ chối duyệt vì đơn đó lẽ ra phải nằm trong phạm vi | Đạt | (A) `.approve_rejectsWhenNewOrderArrivedSincePreview` |
| TC-APR-05 | Định mức hoặc danh mục vật tư bị sửa giữa chừng | Cũng bị bắt, vì cả hai là đầu vào của thuật toán | Đạt | (A) `.approve_rejectsWhenBomChangedSincePreview`, `.approve_rejectsWhenSlatMaterialCatalogChangedSincePreview` |
| TC-APR-06 | Một đơn **ngoài phạm vi** được nhập thêm giữa chừng | **Không** chặn duyệt — phạm vi không đổi thì không có lý do từ chối | Đạt | (A) `.approve_toleratesNewOrderBeyondDeliveryCutoff` |
| TC-APR-07 | **Hai thẻ trình duyệt**: thẻ A đang xem phương án, thẻ B sửa một lô tồn kho, thẻ A bấm duyệt | Thẻ A báo dữ liệu đã thay đổi, tự tính lại trên trạng thái mới, không ghi gì | Đạt — cảnh báo hiện đúng, nhãn thời điểm tính đổi từ 16:52:40 sang 16:52:48, màn hình giữ nguyên | (B) |
| TC-APR-08 | Bấm duyệt khi **phạm vi không còn đơn nào** | Từ chối kèm thông báo, không ghi một phương án trắng | Đạt — duyệt hết phạm vi rồi gọi tiếp thì bị từ chối, số lượng phương án trong cơ sở dữ liệu không đổi. Phép thử (B) đi bằng lời gọi trực tiếp tới máy chủ, vì trên giao diện nút Duyệt đã bị vô hiệu khi phạm vi rỗng (xem hạn chế 7) | (A) `.approve_rejectsWhenScopeHasNoOrder`, `CuttingPlanControllerTest.approve_withEmptyScope_returnsUnprocessableAndSavesNothing` · (B) |
| TC-APR-09 | Đơn đã duyệt ở lần chạy trước | Không quay lại hàng chờ của bất kỳ lần chạy nào sau đó | Đạt | (A) `.generate_ordersAlreadyProcessed_areExcludedFromLaterRuns`, `.findUnapproved_excludesOrdersAlreadyApproved` |

### 4.9. Báo cáo và xuất Excel

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-RPT-01 | Xuất Excel của một phương án đã duyệt | Đúng **21 cột, đúng tên và đúng thứ tự** khuôn mẫu doanh nghiệp, một sheet | Đạt — 21 cột khớp tuyệt đối, sheet `Export`, 295 dòng | (A) `ExcelExportServiceTest.exportCuttingPlan_usesTheExactColumnNamesAndOrderOfTheCompanyTemplate` · (B) |
| TC-RPT-02 | Mức chi tiết của mỗi dòng | Một dòng = một bộ cửa × một loại vật tư cần cắt | Đạt | (A) `CuttingPlanReportServiceTest.buildFromPreview_writesOneRowPerMaterialOfEachDoorSet` |
| TC-RPT-03 | Ba cột định danh (lệnh sản xuất, số đơn bán, mã vật tư) | Ghi dạng **chữ**, không phải số | Đạt | (A) `ExcelExportServiceTest.exportCuttingPlan_writesIdentifierColumnsAsTextNotNumbers` |
| TC-RPT-04 | Dữ liệu chỉ có ở hệ thống nguồn của doanh nghiệp | **Hai cột** tương ứng vẫn giữ trong file nhưng để trống, để file ghép được vào công cụ báo cáo hiện có | Đạt | (A) `ExcelExportServiceTest.exportCuttingPlan_leavesTheOutOfScopeColumnsBlank`. Nhóm dữ liệu thứ ba nằm ngoài phạm vi không có cột riêng — nó chỉ là một biến thể câu trạng thái mà hệ thống không sinh ra |
| TC-RPT-05 | Báo cáo thiếu vật tư | Là **bản lọc** của cùng nguồn dữ liệu: chỉ dòng còn thiếu, tập cột rút gọn, giữ nguyên thứ tự cột của khuôn mẫu | Đạt — 13 cột (tập con đúng thứ tự), 117 dòng, khớp đúng 117 dòng thiếu vật tư trong cơ sở dữ liệu | (A) `ExcelExportServiceTest.exportShortageReport_keepsOnlyRowsStillMissingSticks_withTheReducedColumnSet` · (B) |
| TC-RPT-06 | Xuất Excel cho **bản tính chưa lưu** | Dùng đúng bộ cột và đúng cách gộp phôi của phương án đã duyệt | Đạt — 21 cột trùng khớp; đo **sau đợt duyệt**: 112 bộ cửa còn lại → 453 dòng, đúng bằng số dòng nhu cầu cắt của cùng thời điểm | (A) `CuttingPlanControllerTest.approvalPreviewAndApprovedPlan_groupSticksIdentically` khóa phép **gộp phôi** giống nhau giữa hai nhánh; phần bộ cột do (B) đối chiếu |
| TC-RPT-07 | So số liệu giữa bản chưa lưu và phương án sau khi duyệt | Hai đường vào cho ra cùng con số, kể cả dòng thiếu **toàn bộ** số thanh | Đạt | (A) `CuttingPlanReportServiceTest.approvedPlanAndPreview_produceTheSameNumbers`, `.approvedPlanAndPreview_agreeOnARowMissingEveryStick` |
| TC-RPT-08 | Cột mô tả cách cắt | Nêu đúng mức cắt đã dùng và đánh dấu phôi vốn là phần dư tái sử dụng | Đạt | (A) `CuttingPlanReportServiceTest.buildFromPreview_describesANearFitCutAsPa1`, `.buildFromPreview_describesAPairedCutAsPa3`, `.buildFromPreview_marksTheRestockedStickReusedLaterInTheSameRun` |

### 4.10. Quản lý tài khoản

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-USR-01 | Tạo tài khoản mới | Lưu mật khẩu đã băm, tài khoản đăng nhập được ngay | Đạt | (A) `UserServiceTest.create_storesHashedPasswordAndAllowsLoginWithIt` |
| TC-USR-02 | Tạo tài khoản trùng tên đăng nhập | Từ chối | Đạt | (A) `UserServiceTest.create_rejectsDuplicateUsername` |
| TC-USR-03 | Khóa tài khoản | Tài khoản đó không đăng nhập được nữa | Đạt | (A) `UserServiceTest.update_lockedAccountCannotLogInAnyMore` |
| TC-USR-04 | Tự khóa chính mình | Từ chối; trên giao diện nút tương ứng bị vô hiệu | Đạt | (A) `UserServiceTest.update_rejectsLockingYourself` · (B) nút khóa trên dòng của chính mình ở trạng thái vô hiệu |
| TC-USR-05 | Khóa hoặc hạ quyền **tài khoản quản trị cuối cùng** | Từ chối, để hệ thống không bao giờ còn 0 quản trị viên | Đạt | (A) `UserServiceTest.update_rejectsLockingTheLastActiveAdmin`, `.update_rejectsDemotingTheLastActiveAdmin` |
| TC-USR-06 | Khóa một quản trị viên khi vẫn còn quản trị viên khác | Cho phép | Đạt | (A) `UserServiceTest.update_allowsLockingAnAdminWhileAnotherActiveAdminRemains` |

### 4.11. Yêu cầu phi chức năng

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-NFR-01 | **Cân bằng vật liệu** của một đợt duyệt thật | Tổng độ dài đã cắt + tổng phần dư = tổng độ dài phôi xuất kho, **không sai lệch** | Đạt — 7.560.577mm + 497.863mm = 8.058.440mm, lệch **0** | (B) |
| TC-NFR-02 | Trừ tồn kho sau khi duyệt | Số thanh giảm đúng bằng số phôi đã dùng trừ số phần dư nhập lại kho | Đạt — 60.851 → 59.343 thanh (giảm 1.508 = 1.641 phôi xuất − 133 phôi nhập lại) | (B) · (A) `CuttingPlanServiceTest.generate_decrementsConsumedInventory` cho vế trừ phôi đã dùng, `.generate_remainderRestockedThenReusedInSameRunNetsOut` và `.generate_totalStockUsedM_excludesRemainderRestockedToInventory` cho vế phần dư nhập lại kho |
| TC-NFR-03 | Phần dư sinh ra sau một đợt duyệt | Chỉ có loại **bỏ đi** (dưới 30cm) và **nhập lại kho** (trên 3m); **không** có phần dư 30cm–3m | Đạt — nhập lại kho 415.810mm / bỏ đi 82.053mm / lãng phí **0** | (B) |
| TC-NFR-04 | Thời gian chức năng **tính** trên toàn bộ sổ đơn | Vài giây | Đạt — **0,25–0,63 giây** qua hai lần chạy độc lập (182 bộ cửa, 748 dòng nhu cầu, 582 KB dữ liệu trả về) | (B) |
| TC-NFR-05 | Thời gian chức năng **duyệt** một đợt | Vài giây | Đạt — xem phương án **0,33–0,45 giây**, ghi xuống **2,60–5,93 giây** (70 đơn) qua hai lần chạy độc lập | (B) |
| TC-NFR-06 | Thời gian xuất Excel | Vài giây | Đạt — **1,82–2,45 giây** cho bản tính trước đợt duyệt (182 bộ cửa, 65,9 KB); **1,21 giây** cho bản tính sau đợt duyệt (112 bộ cửa, 453 dòng, 39,8 KB) | (B) |
| TC-NFR-07 | Dựng lại hệ thống từ lược đồ trống | Toàn bộ phiên bản di trú chạy được từ đầu, không lỗi | Đạt — **14 phiên bản di trú, 1,28–2,07 giây** (hai lần dựng lại), tài khoản mặc định hoạt động lại | (B) |
| TC-NFR-08 | **Tính tái lập**: xóa sạch cơ sở dữ liệu, nhập lại cùng bộ dữ liệu rồi chạy lại cùng thao tác | Cho ra đúng cùng một kết quả | Đạt — hai lượt độc lập trùng khít: 182 bộ cửa (48 đủ / 134 thiếu), 748 dòng nhu cầu, phế 107,06m/9.030,21m; đợt duyệt 70 đơn, 82,05m/7.642,63m, 1.641 phôi (1.368/140/133), tồn kho còn 59.343 thanh | (B) |

## 5. Số liệu của lần chạy dùng làm bằng chứng

| Chỉ số | Giá trị |
|---|---|
| Đơn hàng trong hệ thống | 190 bộ cửa |
| Đơn nằm trong phạm vi chức năng tính | 182 (8 đơn bị chặn vì mẫu cửa thiếu định mức dùng được) |
| Kết quả tính toàn sổ đơn | 48 bộ cửa đủ nan · 134 bộ cửa thiếu nan · tỷ lệ phế **1,19 %** (107,06m phế / 9.030,21m tiêu hao) |
| Đợt duyệt đã ghi xuống | 70 đơn · tỷ lệ phế **1,07 %** (82,05m phế / 7.642,63m tiêu hao) |
| Phôi đã dùng trong đợt duyệt | 1.641 phôi — mức 1: 1.368 · mức 3: 140 · mức 4: 133 |
| Dòng thiếu vật tư của đợt duyệt | 117 dòng / 534 đoạn chưa cắt được |

Hai tỷ lệ phế trên **không so sánh trực tiếp được với nhau**: chức năng tính ghép trên toàn bộ sổ đơn nên có nhiều cơ hội ghép cặp hơn hẳn một đợt duyệt tối đa 70 đơn, vì vậy con số của nó phải đọc như **giới hạn dưới** của hao phí chứ không phải kết quả sẽ đạt được.

## 6. Cách chạy lại

- **Nhóm (A)**: chạy lệnh kiểm thử của mô-đun máy chủ. Lệnh luôn kèm bước dọn thư mục biên dịch — biên dịch tăng dần có thể giữ lại lớp cũ và làm kết quả không phản ánh mã nguồn hiện tại.
- **Nhóm (B)**: dựng cơ sở dữ liệu trống, khởi động máy chủ để các phiên bản di trú tự chạy, nhập ba file dữ liệu **đúng thứ tự định mức → tồn kho → đơn hàng**, rồi thao tác trên giao diện theo đúng kịch bản trong bảng.

## 7. Hạn chế đã biết của đợt kiểm thử

1. **8 đơn hàng không kiểm được luồng cắt** vì mẫu cửa tương ứng thiếu hệ số tính số lượng nan trong dữ liệu nguồn (13% số dòng định mức nhóm nan chính thiếu hệ số này). Đây là vấn đề dữ liệu nguồn của doanh nghiệp, không phải lỗi hệ thống; hệ thống đếm riêng và cảnh báo chúng đúng như đặc tả (TC-SIM-04).
2. **Mức 2 (cắt theo bội số) không xuất hiện trong đợt duyệt thật** vừa chạy — bộ dữ liệu này không có nhóm đoạn cùng độ dài đủ để kích hoạt. Mức đó được phủ bằng kiểm thử tự động (TC-ALG-02) với dữ liệu dựng riêng.
3. **Phần dư loại 30cm–3m không xuất hiện được** trong kết quả: thuật toán từ chối mọi nhánh cắt dẫn tới phần dư như vậy, nên không có kịch bản dương để quan sát — chỉ có kịch bản âm (TC-ALG-05, TC-ALG-08) khẳng định hệ thống báo thiếu vật tư thay vì tạo ra nó.
4. **Đổi mật khẩu không thu hồi thẻ truy cập đã phát**; cách vô hiệu hóa tức thì hiện nay là khóa tài khoản (TC-AUTH-06). Giới hạn này đã ghi nhận, chưa xử lý trong phạm vi khóa luận.
5. Giao diện chưa có bộ kiểm thử tự động riêng; các ca nhóm (B) được kiểm bằng trình duyệt thật điều khiển tự động, chạy lại được nhưng không chạy cùng mỗi lần biên dịch như nhóm (A).
6. **Hai tầng phân định ưu tiên chưa được phủ hết**: ca tự động chỉ chạm tới tầng lô sản xuất, chưa có ca riêng cho tầng số thứ tự bộ cửa (TC-ALG-10); và thứ tự sắp **mặc định** của endpoint danh sách đơn hàng chưa có ca khóa lại, mới chỉ khóa được phép sắp (TC-SO-02).
7. **Dấu vân trạng thái không chống được hai lượt duyệt chạy song song** — nó chỉ chặn việc duyệt một phương án đã lỗi thời. Hai lượt cùng đọc được dấu vân cũ đều vượt qua cửa này; chốt chặn thật nằm ở điều kiện cập nhật trạng thái đơn, làm cả giao dịch quay lui khi số dòng lệch. TC-APR-07 vì vậy **không** được đọc thành "đã chứng minh an toàn khi nhiều người duyệt cùng lúc".
8. **Dấu hiệu phôi tái sử dụng trong cột mô tả cách cắt là suy đoán**, dựa trên việc độ dài đó không có trong ảnh chụp tồn kho đầu lần chạy. Phần dư trùng đúng một độ dài vốn đã có trong kho thì không phân biệt được (liên quan TC-RPT-08).
9. **Báo cáo của phương án đã duyệt đọc sống** tên khách hàng, tên mẫu cửa và model, không lấy từ ảnh chụp. Sửa các trường này về sau sẽ làm file xuất lại khác file xuất lần đầu của cùng một phương án; đặc tả chỉ yêu cầu chụp lại số liệu tồn kho.

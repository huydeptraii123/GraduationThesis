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
| Yêu cầu phi chức năng (TC-NFR) | 9 | 9 | 0 |
| Triển khai đóng gói (TC-DEP) | 8 | 8 | 0 |
| Kiểm tra tự động khi đẩy mã (TC-CI) | 7 | 7 | 0 |
| **Tổng** | **99** | **99** | **0** |

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
| TC-APR-08 | Bấm duyệt khi **phạm vi không còn đơn nào** | Từ chối kèm thông báo, không ghi một phương án trắng | Đạt — duyệt hết phạm vi rồi gọi tiếp thì bị từ chối, số lượng phương án trong cơ sở dữ liệu không đổi. Phép thử (B) đi bằng lời gọi trực tiếp tới máy chủ, vì trên giao diện nút Duyệt đã bị vô hiệu khi phạm vi rỗng (xem hạn chế 11) | (A) `.approve_rejectsWhenScopeHasNoOrder`, `CuttingPlanControllerTest.approve_withEmptyScope_returnsUnprocessableAndSavesNothing` · (B) |
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
| TC-NFR-09 | Thời gian phản hồi của các màn hình danh sách, gồm cả khi lọc, đổi cột sắp xếp và nhảy tới trang cuối | Đủ nhanh để thao tác liên tục, và trang cuối không chậm hơn trang đầu một cách nhận thấy được ở quy mô 190 đơn | Đạt — trung vị **45–129 ms** trên 19 đường gọi; trang cuối của sổ đơn 64 ms so với trang đầu 80 ms, chênh lệch nhỏ hơn dao động giữa các lần đo. Số đo chi tiết ở mục 5.1 | (B) |

### 4.12. Triển khai đóng gói

Nhóm này kiểm bản triển khai đóng gói mô tả ở `docs/architecture.md`: ba dịch vụ — MySQL, máy chủ ứng dụng Spring Boot, và Nginx phục vụ giao diện đã dựng sẵn — chạy trong ba vùng chứa Docker tách biệt, dựng lên bằng một lệnh duy nhất từ `docker-compose.yml`. Đây mới là hình hài hệ thống khi đưa vào dùng thật; các nhóm trên chạy trực tiếp trên máy lập trình nên không đi qua Nginx và không kiểm được khâu đóng gói.

Điểm cần nhấn: **mọi phép thử trong nhóm này gọi qua cổng của Nginx**, không gọi thẳng máy chủ ứng dụng. Chỉ như vậy mới chứng minh được trọn đường đi *trình duyệt → Nginx → Spring Boot → MySQL*; gọi thẳng máy chủ ứng dụng thì bỏ qua đúng khâu đang cần kiểm.

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-DEP-01 | Dựng trọn bộ ba dịch vụ bằng một lệnh | Cả ba vùng chứa lên và ở trạng thái hoạt động | Đạt — cơ sở dữ liệu báo khỏe mạnh, máy chủ ứng dụng khởi động trong **7,6 giây**, Nginx phục vụ ngay sau đó | (B) |
| TC-DEP-02 | Máy chủ ứng dụng trong vùng chứa nối vào cơ sở dữ liệu đã có sẵn dữ liệu | Nhận ra lược đồ đã ở phiên bản mới nhất, **không** chạy lại phiên bản di trú nào | Đạt — xác nhận đủ **14 phiên bản di trú**, lược đồ ở phiên bản 14, ghi rõ "không cần di trú" | (B) |
| TC-DEP-03 | Nginx phục vụ giao diện tĩnh | Trả trang và gói mã giao diện, không cần máy chủ ứng dụng | Đạt — trang 200 trong **21 ms**; gói mã **1.806 KB** | (B) |
| TC-DEP-04 | Nginx chuyển tiếp lời gọi đăng nhập và lời gọi nghiệp vụ sang máy chủ ứng dụng | Cả hai nhóm đường dẫn tới đúng đích và đọc được cơ sở dữ liệu | Đạt — đăng nhập 200 (**673 ms**, gồm cả lần dựng kết nối đầu tiên); danh sách đơn hàng trả đúng **190 đơn** trong 161 ms | (B) |
| TC-DEP-05 | Gọi một đường dẫn nghiệp vụ khi chưa đăng nhập, đi qua Nginx | Vẫn bị chặn; lớp bảo mật không bị phép chuyển tiếp làm mất | Đạt — **401** | (B) |
| TC-DEP-06 | Gửi gói tin 2 MB qua Nginx (mặc định của Nginx chặn ở 1 MB) | Gói tin tới được máy chủ ứng dụng, tức phần nới giới hạn có hiệu lực | Đạt — **400** do nội dung không phải tệp bảng tính hợp lệ, **không** phải 413 của phép chặn; cơ sở dữ liệu không đổi | (B) |
| TC-DEP-07 | Chạy chức năng tính phương án bên trong vùng chứa | Ra đúng kết quả như khi chạy trực tiếp trên máy lập trình | Đạt — **112 bộ cửa · 453 dòng nhu cầu**, trùng với số liệu ở mục 5; tính **0,38 giây**, xuất tệp **1,10 giây** | (B) |
| TC-DEP-08 | Bản đóng gói cũ hơn lược đồ cơ sở dữ liệu | Bị chặn ngay lúc khởi động thay vì chạy tiếp rồi hỏng lúc dùng | Đạt — quan sát được trên một bản đóng gói cũ: máy chủ dừng khởi động với thông báo thiếu cột, đúng cột đã bị một phiên bản di trú về sau xóa đi | (B) |

Kết quả TC-DEP-07 đáng chú ý ở chỗ nó **đối chiếu chéo hai cách chạy khác nhau** trên cùng một cơ sở dữ liệu: bản đóng gói trong vùng chứa và bản chạy trực tiếp cho ra cùng số bộ cửa, cùng số dòng nhu cầu và cùng kích thước tệp xuất. Tệp xuất đi qua Nginx giữ nguyên **40.697 byte** như khi gọi thẳng máy chủ ứng dụng, nên phép chuyển tiếp không làm méo dữ liệu nhị phân.

Cả tám phép thử của nhóm này chạy trên cơ sở dữ liệu đang giữ kết quả đợt duyệt ở mục 5, và chín chỉ số của cơ sở dữ liệu **trùng khít trước và sau** — nhóm này không ghi gì xuống, kể cả phép thử gửi gói tin 2 MB.

### 4.13. Kiểm tra tự động khi đẩy mã

Các nhóm trên đều chạy trên máy của người làm khóa luận, với một bộ công cụ đã cài sẵn. Điều đó không phân biệt được *"mã nguồn đúng"* với *"máy này tình cờ cấu hình vừa vặn"*. Nhóm này kiểm điều còn lại: dựng lại toàn bộ từ con số không trên một máy sạch do GitHub cấp, mỗi lần đẩy mã lên nhánh chính.

Số liệu dưới đây lấy từ lượt chạy đầu tiên, trên đúng phiên bản mã nguồn đã đưa tệp mô tả quy trình vào kho.

| Mã | Kịch bản | Kết quả mong đợi | Kết quả | Bằng chứng |
|---|---|---|---|---|
| TC-CI-01 | Đẩy mã lên nhánh chính | Quy trình kiểm tự động chạy mà không cần thao tác tay | Đạt — lượt chạy số 1 kết luận **thành công** | (B) |
| TC-CI-02 | Biên dịch và chạy toàn bộ bộ kiểm thử trên máy sạch, dùng đúng phiên bản Java của bản triển khai | Chạy hết, không ca nào thất bại | Đạt — **428 ca: 0 thất bại, 0 lỗi, 3 bỏ qua**, trọn 1 phút 17 giây trên máy chưa hề được chuẩn bị gì | (B) |
| TC-CI-03 | Cài phụ thuộc giao diện theo tệp khóa phiên bản | Cài được, tức tệp khóa còn khớp với danh sách phụ thuộc | Đạt — **9 giây**; lệnh này cố ý thất bại nếu hai tệp lệch nhau | (B) |
| TC-CI-04 | Soát lỗi tĩnh và dựng bản phát hành giao diện | Cả hai qua; bước dựng phủ luôn kiểm kiểu | Đạt — soát lỗi tức thì, dựng **6 giây** | (B) |
| TC-CI-05 | Dựng ảnh triển khai của cả ba dịch vụ từ mã nguồn | Dựng được, tức tệp mô tả ảnh chưa mục so với mã nguồn | Đạt — **71 giây** | (B) |
| TC-CI-06 | Giữ lại báo cáo kiểm thử sau khi chạy | Tải về đọc được, kể cả khi bước kiểm thử hỏng | Đạt — gói báo cáo **127.460 byte** | (B) |
| TC-CI-07 | Ba nhóm việc chạy song song | Tổng thời gian chờ ngắn hơn tổng thời gian ba việc cộng lại | Đạt — ba việc tốn 92 + 21 + 75 = **188 giây**, nhưng người đẩy mã chỉ chờ **1 phút 37 giây** | (B) |

Điểm đáng nói của TC-CI-02: bộ kiểm thử này dựng cơ sở dữ liệu thật trong vùng chứa chứ không dùng cơ sở dữ liệu trong bộ nhớ, nên việc nó chạy được trên một máy chưa hề được chuẩn bị gì là bằng chứng rằng **toàn bộ phụ thuộc môi trường đã được khai báo trong mã nguồn**, không có bước cài đặt tay nào còn sót lại trong đầu người làm.

Một lưu ý khi đọc lại nhóm này: máy sạch **không có bộ dữ liệu của doanh nghiệp** — bộ dữ liệu đó không được đưa lên kho mã vì lý do bảo mật. Ba ca kiểm thử nhập liệu có gắn điều kiện tiên quyết vào tệp dữ liệu thật vì vậy tự bỏ qua, và **đúng 3 ca bỏ qua** là con số quan sát được. Chênh lệch giữa 428 ca ở mục 2 và 425 ca thật sự chạy ở đây hoàn toàn nằm ở ba ca đó, không phải ở đâu khác. Xem thêm mục 6.

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

### 5.1. Thời gian phản hồi của các màn hình danh sách

Mỗi đường gọi được làm nóng một lượt (bỏ kết quả) rồi đo năm lượt liên tiếp, trên cùng máy với máy chủ ứng dụng và cơ sở dữ liệu chạy cục bộ. Cột *quy mô* ghi khối lượng dữ liệu mà đường gọi đó thực sự phải xử lý; với các dòng có điều kiện lọc, nó ghi dạng *số bản ghi chọn ra / tổng số bản ghi* để thấy phép lọc có thu hẹp tập dữ liệu thật hay không.

| Màn hình / thao tác | Quy mô | Trung vị | Khoảng đo |
|---|---|---|---|
| Đơn hàng — trang đầu, 20 dòng | 190 đơn | 80 ms | 56–106 ms |
| Đơn hàng — nhảy tới trang cuối | 190 đơn | 64 ms | 62–88 ms |
| Đơn hàng — lọc theo từ khóa | 13 / 190 đơn | 71 ms | 43–83 ms |
| Đơn hàng — lọc theo khoảng ngày giao | 35 / 190 đơn | 101 ms | 91–111 ms |
| Đơn hàng — đổi cột sắp xếp | 190 đơn | 85 ms | 72–100 ms |
| Tồn kho — trang đầu, 20 dòng | 2.308 lô | 81 ms | 67–87 ms |
| Tồn kho — lọc theo nhóm nan chính | 1.246 / 2.308 lô | 68 ms | 49–79 ms |
| Tồn kho — ô thống kê tổng hợp | toàn bảng | 54 ms | 40–91 ms |
| Định mức — trang đầu, 20 dòng | 1.860 dòng | 100 ms | 94–117 ms |
| Định mức — lọc theo nhóm nan chính | 527 / 1.860 dòng | 85 ms | 81–88 ms |
| Định mức — ô thống kê tổng hợp | toàn bảng | 83 ms | 40–98 ms |
| Mẫu cửa — danh sách tra cứu cho màn Định mức và Đơn hàng, **không phân trang** | 392 mẫu · 39,7 KB | 80 ms | 55–110 ms |
| Khách hàng — danh sách tra cứu cho màn Đơn hàng, **không phân trang** | 21 khách hàng · 1,4 KB | 62 ms | 48–90 ms |
| Danh mục thanh nan — trang đầu, 20 dòng | 620 mã | 45 ms | 39–69 ms |
| Danh mục thanh nan — danh sách tra cứu, **không phân trang** | 620 mã · 64,9 KB | 95 ms | 80–96 ms |
| Phương án cắt — danh sách | 1 phương án | 71 ms | 61–98 ms |
| Phương án cắt — khối số liệu hao phí cộng dồn của cùng màn đó | gộp trên toàn bộ phương án | 121 ms | 100–141 ms |
| Tài khoản — trang đầu, 20 dòng | 2 tài khoản | 75 ms | 65–114 ms |
| Chi tiết một phương án, **không phân trang** | 144,6 KB | 129 ms | 106–167 ms |

Số lô tồn kho ở đây là **2.308**, nhiều hơn 2.283 lô đã nhập vào ở mục 2 đúng **25 lô**. Đây không phải số liệu mâu thuẫn mà là hai thời điểm khác nhau: 133 thanh dư trên 3m mà đợt duyệt nhập lại kho rơi vào 25 độ dài chưa từng có lô riêng nào, nên hệ thống tạo thêm đúng 25 dòng lô mới chứa trọn 133 thanh đó.

Ba điểm đáng chú ý khi đọc bảng này.

Thứ nhất, **trang cuối không chậm hơn trang đầu** ở quy mô dữ liệu này — điều kiện lọc và phép sắp xếp đều do cơ sở dữ liệu thực hiện, máy chủ chỉ nhận về đúng số dòng của một trang. Cần nói rõ giới hạn của quan sát: phép phân trang vẫn theo kiểu bỏ qua một số dòng đầu, nên về nguyên tắc chi phí có tăng theo số trang đã bỏ qua; với 190 đơn thì mức tăng đó nhỏ hơn dao động giữa các lần đo, và hai điểm đo (trang đầu, trang cuối) không đủ để dựng một xu hướng cho sổ đơn lớn hơn nhiều lần.

Thứ hai, **đường gọi chậm nhất là trang chi tiết một phương án** (129 ms), và đứng ngay sau là **khối số liệu hao phí cộng dồn** của màn danh sách phương án (121 ms). Cái đầu phải trả trọn bộ dòng cắt thì phần chú giải mới tổng hợp đúng; cái sau phải gộp trên toàn bộ phương án đã lưu chứ không chỉ trang đang xem.

Thứ ba, trong bảng có **bốn đường cố ý không phân trang** — danh sách tra cứu thanh nan, danh sách mẫu cửa, danh sách khách hàng và trang chi tiết phương án. Ba cái đầu là nguồn dữ liệu cho ô chọn và cho việc hiển thị tên trên màn khác, phân trang thì màn dùng chúng không tra cứu được. Chi phí của cả bốn tăng theo số bản ghi phải đọc và trả về; đợt đo này **không tách riêng** phần truy vấn với phần tuần tự hóa và truyền dữ liệu, nên không kết luận được khâu nào chiếm phần lớn. Điều khẳng định được là cả bốn vẫn nằm trong một phần nhỏ của giây.

## 6. Cách chạy lại

- **Nhóm (A)**: chạy lệnh kiểm thử của mô-đun máy chủ. Lệnh luôn kèm bước dọn thư mục biên dịch — biên dịch tăng dần có thể giữ lại lớp cũ và làm kết quả không phản ánh mã nguồn hiện tại.
- **Nhóm (B)**: dựng cơ sở dữ liệu trống, khởi động máy chủ để các phiên bản di trú tự chạy, nhập ba file dữ liệu **đúng thứ tự định mức → tồn kho → đơn hàng**, rồi thao tác trên giao diện theo đúng kịch bản trong bảng.
- **Nhóm (B) cho triển khai đóng gói (TC-DEP)**: dựng ba dịch vụ bằng một lệnh từ tệp mô tả trong mã nguồn, rồi gọi **qua cổng của Nginx**, không gọi thẳng máy chủ ứng dụng.
- **Nhóm (B) cho kiểm tra tự động (TC-CI)**: không cần thao tác gì — đẩy mã lên nhánh chính là quy trình tự chạy.

**Một điểm phải lưu ý khi chạy lại nhóm (A) ở nơi khác.** Bộ dữ liệu của doanh nghiệp không được đưa lên kho mã, nên trên máy không có sẵn bộ dữ liệu đó thì **ba ca kiểm thử nhập liệu tự bỏ qua** thay vì thất bại: chúng có điều kiện tiên quyết là sự tồn tại của tệp dữ liệu thật. Đây là cách cố ý — một ca không chạy được vì thiếu dữ liệu đầu vào thì báo "đã bỏ qua" trung thực hơn là báo thất bại. Người chạy lại vì vậy nên đối chiếu **cả số ca đạt lẫn số ca bỏ qua**, đừng chỉ nhìn tổng. Con số cụ thể: **425 ca đạt, 3 ca bỏ qua**, không ca nào thất bại. Con số này quan sát được ở **hai môi trường độc lập** — một vùng chứa sạch dựng tại chỗ và máy của dịch vụ kiểm tra tự động (TC-CI-02) — nên nó là hành vi của bộ kiểm thử khi thiếu dữ liệu đầu vào, không phải đặc thù của một máy.

## 7. Hạn chế đã biết của đợt kiểm thử

1. **8 đơn hàng không kiểm được luồng cắt** vì mẫu cửa tương ứng thiếu hệ số tính số lượng nan trong dữ liệu nguồn (13% số dòng định mức nhóm nan chính thiếu hệ số này). Đây là vấn đề dữ liệu nguồn của doanh nghiệp, không phải lỗi hệ thống; hệ thống đếm riêng và cảnh báo chúng đúng như đặc tả (TC-SIM-04).
2. **Mức 2 (cắt theo bội số) không xuất hiện trong đợt duyệt thật** vừa chạy — bộ dữ liệu này không có nhóm đoạn cùng độ dài đủ để kích hoạt. Mức đó được phủ bằng kiểm thử tự động (TC-ALG-02) với dữ liệu dựng riêng.
3. **Phần dư loại 30cm–3m không xuất hiện được** trong kết quả: thuật toán từ chối mọi nhánh cắt dẫn tới phần dư như vậy, nên không có kịch bản dương để quan sát — chỉ có kịch bản âm (TC-ALG-05, TC-ALG-08) khẳng định hệ thống báo thiếu vật tư thay vì tạo ra nó.
4. **Đổi mật khẩu không thu hồi thẻ truy cập đã phát**; cách vô hiệu hóa tức thì hiện nay là khóa tài khoản (TC-AUTH-06). Giới hạn này đã ghi nhận, chưa xử lý trong phạm vi khóa luận.
5. Giao diện chưa có bộ kiểm thử tự động riêng; các ca nhóm (B) được kiểm bằng trình duyệt thật điều khiển tự động, chạy lại được nhưng không chạy cùng mỗi lần biên dịch như nhóm (A).
6. **Hai tầng phân định ưu tiên chưa được phủ hết**: ca tự động chỉ chạm tới tầng lô sản xuất, chưa có ca riêng cho tầng số thứ tự bộ cửa (TC-ALG-10); và thứ tự sắp **mặc định** của endpoint danh sách đơn hàng chưa có ca khóa lại, mới chỉ khóa được phép sắp (TC-SO-02).
7. **Dấu vân trạng thái không chống được hai lượt duyệt chạy song song** — nó chỉ chặn việc duyệt một phương án đã lỗi thời. Hai lượt cùng đọc được dấu vân cũ đều vượt qua cửa này; chốt chặn thật nằm ở điều kiện cập nhật trạng thái đơn, làm cả giao dịch quay lui khi số dòng lệch. TC-APR-07 vì vậy **không** được đọc thành "đã chứng minh an toàn khi nhiều người duyệt cùng lúc".
8. **Dấu hiệu phôi tái sử dụng trong cột mô tả cách cắt là suy đoán**, dựa trên việc độ dài đó không có trong ảnh chụp tồn kho đầu lần chạy. Phần dư trùng đúng một độ dài vốn đã có trong kho thì không phân biệt được (liên quan TC-RPT-08).
9. **Mọi số đo thời gian đều là đo một người dùng**, trên một máy cá nhân với máy chủ ứng dụng và cơ sở dữ liệu chạy cùng chỗ, không qua mạng. Chúng cho biết hệ thống đủ nhanh để thao tác, **không** phải kết quả kiểm thử tải: chưa đo khi nhiều người dùng đồng thời, chưa đo ở quy mô dữ liệu lớn hơn bộ dữ liệu này.
10. **Báo cáo của phương án đã duyệt đọc sống** tên khách hàng, tên mẫu cửa và model, không lấy từ ảnh chụp. Sửa các trường này về sau sẽ làm file xuất lại khác file xuất lần đầu của cùng một phương án; đặc tả chỉ yêu cầu chụp lại số liệu tồn kho.
11. **Nhánh từ chối vì phạm vi rỗng không bấm tới được từ giao diện.** Nút Duyệt bị vô hiệu ngay khi phạm vi không còn đơn nào, và một phạm vi đang có đơn chỉ rỗng đi khi dữ liệu thay đổi — mà thay đổi đó làm lệch dấu vân trạng thái nên hệ thống từ chối vì lý do "dữ liệu đã đổi" trước. Nhánh này tồn tại cho lời gọi trực tiếp tới máy chủ và cho thẻ trình duyệt mở từ trước, nên TC-APR-08 chỉ kiểm được bằng lời gọi trực tiếp.
12. **Tệp bảng tính xuất ra không trùng nhau tới từng byte giữa hai lần chạy**, kể cả khi dữ liệu y hệt: khuôn dạng bảng tính nhúng mốc thời gian tạo tệp. Vì vậy mọi phép đối chiếu hai tệp xuất — trong TC-RPT-06 cũng như TC-DEP-07 — đều so trên **nội dung ô và số dòng**, không so trên byte. Tính tái lập khẳng định ở TC-NFR-08 cũng phải hiểu theo nghĩa nội dung.

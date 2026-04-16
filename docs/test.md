# Bảng Test Cases - Unit Test (Module 6, 8, 10)

**Người phụ trách:** Thành viên 4
**Ngày tạo:** 2026-04-16
**Tổng số test cases:** ~80

---

## Hướng dẫn đọc bảng

| Cột | Ý nghĩa |
|---|---|
| **Script Test** | Đường dẫn tương đối từ thư mục gốc dự án |
| **Input** | Dữ liệu đầu vào đủ để đi vào nhánh cần kiểm tra |
| **Expected Output** | Kết quả theo đặc tả nghiệp vụ (KHÔNG chép từ code) |
| **Minh chứng** | Cách CheckDB và cơ chế Rollback |

---

## Module 6: Vị trí tuyển dụng (JobPositionService)

**Script Test:** `services/job-service/src/test/java/com/example/job_service/service/JobPositionServiceTest.java`

| Tên chức năng | Function | Testcase ID | Test Objective | Input | Expected Output | Minh chứng | Notes |
|---|---|---|---|---|---|---|---|
| Tạo vị trí tuyển dụng | `create()` | JOB-TC01 | Kiểm tra hệ thống ưu tiên salary từ DTO khi DTO cung cấp đầy đủ | salaryMin=20M, salaryMax=30M trong DTO; RR có salary khác | Position được lưu với salary từ DTO (20M-30M), status=DRAFT theo quy trình ban đầu | CheckDB: `verify(jobPositionRepository, times(1)).save(any())`. Rollback: Không có DB thật, Mockito reset tự động. | CheckDB: status = DRAFT, salaryMin = 20M trong đối tượng saved |
| Tạo vị trí tuyển dụng | `create()` | JOB-TC02 | Kiểm tra fallback salary từ RecruitmentRequest khi DTO không cung cấp | salaryMin=null, salaryMax=null trong DTO; RR.salaryMin=15M, RR.salaryMax=25M | Position được lưu với salary của RR (15M-25M), đảm bảo nghiệp vụ không để trống salary | CheckDB: `ArgumentCaptor` bắt salary từ `save()`. Rollback: Mockito. | CheckDB: salaryMin = 15M, salaryMax = 25M trong đối tượng saved |
| Tạo vị trí tuyển dụng | `create()` | JOB-TC03 | Kiểm tra giá trị mặc định isRemote=false khi không chỉ định | isRemote=null trong DTO | Position.isRemote = false theo thiết lập mặc định của hệ thống | CheckDB: `ArgumentCaptor` kiểm tra `isRemote()=false` trước khi gọi `save()`. Rollback: Mockito. | CheckDB: isRemote = false trong đối tượng saved |
| Tạo vị trí tuyển dụng | `create()` | JOB-TC04 | Kiểm tra hệ thống cập nhật trạng thái RecruitmentRequest sau khi tạo JD | DTO hợp lệ, RR tồn tại | `recruitmentRequestService.changeStatus(10L, COMPLETED)` được gọi — đảm bảo luồng nghiệp vụ hoàn chỉnh | CheckDB: `verify(recruitmentRequestService).changeStatus(10L, COMPLETED)`. Rollback: Mockito. | CheckDB: changeStatus() gọi đúng tham số (10L, COMPLETED) |
| Tạo vị trí tuyển dụng | `create()` | JOB-TC05 | Kiểm tra exception lan truyền khi RecruitmentRequest không tồn tại | recruitmentRequestId=999 (không tồn tại) | Hệ thống báo lỗi `IdInvalidException`, không tạo JD, không gọi save() | CheckDB: `verify(jobPositionRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Tra cứu theo ID | `findById()` | JOB-TC06 | Kiểm tra truy xuất JD hợp lệ theo ID | id=1 (tồn tại trong hệ thống) | Trả về đúng JobPosition với id=1 và title="Software Engineer" | CheckDB: Không. Rollback: Mockito. | CheckDB: title = "Software Engineer" từ repository |
| Tra cứu theo ID | `findById()` | JOB-TC07 | Kiểm tra xử lý khi ID không tồn tại | id=999 (không tồn tại) | Hệ thống ném `IdInvalidException` với thông báo rõ ràng cho người dùng | CheckDB: Không. Rollback: Mockito. | CheckDB: không có operation nào trên repository |
| Lấy JD đã xuất bản | `getByIdWithPublished()` | JOB-TC08 | Kiểm tra cho phép truy cập JD đã PUBLISHED | status=PUBLISHED, id=1 | Trả về JobPosition (ứng viên hoặc hệ thống ngoài có thể xem được) | CheckDB: Không. Rollback: Mockito. | CheckDB: status = PUBLISHED, không gọi save() |
| Lấy JD đã xuất bản | `getByIdWithPublished()` | JOB-TC09 | Kiểm tra chặn truy cập JD đang ở DRAFT | status=DRAFT, id=1 | Hệ thống ném `IdInvalidException` — JD chưa sẵn sàng xuất bản cần được bảo vệ | CheckDB: Không. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Lấy JD đã xuất bản | `getByIdWithPublished()` | JOB-TC10 | Kiểm tra chặn truy cập JD đã CLOSED | status=CLOSED, id=1 | Hệ thống ném `IdInvalidException` — JD đã đóng không cho phép ứng tuyển | CheckDB: Không. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Lọc danh sách JD | `findAllWithFiltersSimple()` | JOB-TC11 | Kiểm tra nhánh tìm theo IDs cụ thể | ids="1,2,3" | Gọi `findByIdIn([1,2,3])`, không gọi `findByFilters()` — tối ưu query | CheckDB: `verify(jobPositionRepository).findByIdIn([1,2,3])`. Rollback: Mockito. | CheckDB: findByIdIn() gọi 1 lần, findByFilters() không được gọi |
| Lọc danh sách JD | `findAllWithFiltersSimple()` | JOB-TC12 | Kiểm tra nhánh lọc theo filter khi ids=null | ids=null | Gọi `findByFilters()`, không gọi `findByIdIn()` | CheckDB: `verify(jobPositionRepository).findByFilters(...)`. Rollback: Mockito. | CheckDB: findByFilters() gọi 1 lần, findByIdIn() không được gọi |
| Lọc danh sách JD | `findAllWithFiltersSimple()` | JOB-TC13 | Kiểm tra fallback khi ids là chuỗi trắng | ids="   " (khoảng trắng) | Fallback về `findByFilters()` — không throw exception, xử lý input đầu vào an toàn | CheckDB: `verify(jobPositionRepository).findByFilters(...)`. Rollback: Mockito. | CheckDB: findByFilters() gọi 1 lần sau khi bỏ qua ids trắng |
| Lọc danh sách JD | `findAllWithFiltersSimple()` | JOB-TC14 | Kiểm tra xử lý ids sai định dạng | ids="abc,def" | Fallback về `findByFilters()` sau khi bắt NumberFormatException, không throw lên caller | CheckDB: `verify(jobPositionRepository).findByFilters(...)`. Rollback: Mockito. | CheckDB: findByFilters() gọi 1 lần sau catch exception |
| Lọc JD có phân trang | `findAllWithFiltersSimplePaged()` | JOB-TC15 | Kiểm tra phân trang thủ công với IDs | ids="1,2,3,4", page=0, pageSize=2 | Content có 2 items, total=4, meta chính xác theo nghiệp vụ phân trang | CheckDB: `verify(jobPositionRepository).findByIdIn(any())`. Rollback: Mockito. | CheckDB: content.size() = 2, meta.total = 4 |
| Lọc JD có phân trang | `findAllWithFiltersSimplePaged()` | JOB-TC16 | Kiểm tra phân trang qua repository khi ids=null | ids=null, pageable hợp lệ | Gọi `findByFilters()`, meta.total=1 đúng với dữ liệu mock | CheckDB: `verify(jobPositionRepository).findByFilters(...)`. Rollback: Mockito. | CheckDB: meta.total = 1, findByFilters() gọi 1 lần |
| Lọc JD có phân trang | `findAllWithFiltersSimplePaged()` | JOB-TC17 | Kiểm tra trang vượt quá giới hạn | ids="1,2", page=10 (offset>tổng) | Content là danh sách rỗng — xử lý edge case trang vượt quá giới hạn an toàn | CheckDB: `verify(jobPositionRepository).findByIdIn(any())`. Rollback: Mockito. | CheckDB: content rỗng, count không đổi |
| Cập nhật JD | `update()` | JOB-TC18 | Kiểm tra cập nhật đầy đủ khi DTO có tất cả trường | title="Senior Dev", description="New", salaryMin=30M | Position được cập nhật đúng tất cả trường không null | CheckDB: `verify(jobPositionRepository, times(1)).save(any())`. Rollback: Mockito. | CheckDB: title = "Senior Dev", salaryMin = 30M trong đối tượng saved |
| Cập nhật JD | `update()` | JOB-TC19 | Kiểm tra partial update (chỉ title) | DTO chỉ có title="New Title" | Chỉ title thay đổi, description và salary giữ nguyên — tránh ghi đè dữ liệu không mong muốn | CheckDB: `ArgumentCaptor` xác nhận description không đổi. Rollback: Mockito. | CheckDB: description = "Old description" (không đổi) sau save() |
| Cập nhật JD | `update()` | JOB-TC20 | Kiểm tra exception khi ID không tồn tại | id=999 | Hệ thống ném `IdInvalidException`, không gọi `save()` | CheckDB: `verify(jobPositionRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Xóa JD | `delete()` | JOB-TC21 | Kiểm tra xóa thành công | id=1 (tồn tại) | `repository.delete()` được gọi, trả về true | CheckDB: `verify(jobPositionRepository, times(1)).delete(position)`. Rollback: Mockito. | CheckDB: delete() gọi 1 lần với đúng đối tượng |
| Xóa JD | `delete()` | JOB-TC22 | Kiểm tra exception khi ID không tồn tại | id=999 | Hệ thống ném `IdInvalidException`, không gọi `delete()` | CheckDB: `verify(jobPositionRepository, never()).delete(any())`. Rollback: Mockito. | CheckDB: count không đổi, delete() không được gọi |
| Xuất bản JD | `publish()` | JOB-TC23 | Kiểm tra luồng publish hợp lệ từ DRAFT | status=DRAFT, id=1 | Status chuyển thành PUBLISHED, publishedAt được ghi nhận thời điểm xuất bản | CheckDB: `verify(jobPositionRepository, times(1)).save(any())`. Rollback: Mockito. | CheckDB: status = PUBLISHED, publishedAt != null trong đối tượng saved |
| Xuất bản JD | `publish()` | JOB-TC24 | Kiểm tra chặn publish khi đã PUBLISHED | status=PUBLISHED, id=1 | Hệ thống ném `IdInvalidException` — tránh xuất bản trùng lặp | CheckDB: `verify(jobPositionRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, status không đổi |
| Đóng JD | `close()` | JOB-TC25 | Kiểm tra đóng JD từ trạng thái PUBLISHED | status=PUBLISHED, id=1 | Status chuyển thành CLOSED — ngừng nhận ứng viên | CheckDB: `verify(jobPositionRepository, times(1)).save(any())`. Rollback: Mockito. | CheckDB: status = CLOSED trong đối tượng saved |
| Đóng JD | `close()` | JOB-TC26 | Kiểm tra chặn đóng JD ở trạng thái DRAFT | status=DRAFT, id=1 | Hệ thống ném `IdInvalidException` — JD chưa publish không thể đóng | CheckDB: Không. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Mở lại JD | `reopen()` | JOB-TC27 | Kiểm tra mở lại JD từ CLOSED | status=CLOSED, id=1 | Status chuyển thành PUBLISHED — cho phép nhận ứng viên trở lại | CheckDB: `verify(jobPositionRepository, times(1)).save(any())`. Rollback: Mockito. | CheckDB: status = PUBLISHED trong đối tượng saved |

---

## Module 8: Offer (OfferService)

**Script Test:** `services/job-service/src/test/java/com/example/job_service/service/OfferServiceTest.java`

| Tên chức năng | Function | Testcase ID | Test Objective | Input | Expected Output | Minh chứng | Notes |
|---|---|---|---|---|---|---|---|
| Tạo Offer | `create()` | OFF-TC01 | Kiểm tra tạo Offer mới khởi đầu đúng trạng thái ban đầu | candidateId=100, basicSalary=20M, workflowId=50 | Offer được lưu với status=DRAFT, isActive=true theo quy trình tuyển dụng | CheckDB: `ArgumentCaptor` xác nhận status=DRAFT, isActive=true trong `save()`. Rollback: Mockito. | CheckDB: status = DRAFT, isActive = true trong đối tượng saved |
| Cập nhật Offer | `update()` | OFF-TC02 | Kiểm tra partial update khi Offer đang DRAFT | Offer status=DRAFT; DTO: basicSalary=25M, notes="Updated" | Salary và notes cập nhật, candidateId giữ nguyên | CheckDB: `verify(offerRepository, times(1)).save(any())`. Rollback: Mockito. | CheckDB: basicSalary = 25M trong saved; candidateId không đổi |
| Cập nhật Offer | `update()` | OFF-TC03 | Kiểm tra bảo vệ trạng thái — không cho sửa Offer đang chờ duyệt | Offer status=PENDING | Hệ thống ném `IllegalStateException` — Offer đang trong workflow không được phép sửa | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Cập nhật Offer | `update()` | OFF-TC04 | Kiểm tra bảo vệ trạng thái — không cho sửa Offer đã duyệt | Offer status=APPROVED | Hệ thống ném `IllegalStateException` — Offer đã chốt không cho phép thay đổi | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, status không đổi |
| Nộp Offer | `submit()` | OFF-TC05 | Kiểm tra luồng submit thành công từ DRAFT | Offer status=DRAFT, workflowId=50; actorId=10 | Status→PENDING, submittedAt được ghi nhận, WorkflowProducer nhận sự kiện | CheckDB: `verify(offerRepository).save(any())`, `verify(workflowProducer).publishEvent(any())`. Rollback: Mockito. | CheckDB: status = PENDING, submittedAt != null trong saved |
| Nộp Offer | `submit()` | OFF-TC06 | Kiểm tra tự động gán requesterId từ actorId khi chưa có | Offer.requesterId=null; actorId=99 | requesterId được gán bằng actorId=99 — đảm bảo luôn biết ai đã nộp | CheckDB: `ArgumentCaptor` xác nhận requesterId=99 trong `save()`. Rollback: Mockito. | CheckDB: requesterId = 99 trong đối tượng saved |
| Nộp Offer | `submit()` | OFF-TC07 | Kiểm tra chặn submit lại Offer đang PENDING | Offer status=PENDING | Hệ thống ném `IllegalStateException` — tránh submit trùng lặp | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, status giữ nguyên PENDING |
| Nộp Offer | `submit()` | OFF-TC08 | Kiểm tra chặn submit khi thiếu workflowId | Offer status=DRAFT, workflowId=null | Hệ thống ném `IllegalStateException` với thông báo yêu cầu workflowId | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Phê duyệt Offer | `approveStep()` | OFF-TC09 | Kiểm tra luồng phê duyệt hợp lệ từ PENDING | Offer status=PENDING; actorId=50 | save() được gọi, WorkflowProducer nhận sự kiện REQUEST_APPROVED | CheckDB: `verify(offerRepository).save(any())`, `verify(workflowProducer).publishEvent(any())`. Rollback: Mockito. | CheckDB: save() gọi 1 lần, publishEvent() gọi 1 lần |
| Phê duyệt Offer | `approveStep()` | OFF-TC10 | Kiểm tra chặn phê duyệt Offer không ở PENDING | Offer status=DRAFT | Hệ thống ném `IllegalStateException` — chỉ được duyệt từ trạng thái chờ duyệt | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Từ chối Offer | `rejectStep()` | OFF-TC11 | Kiểm tra từ chối Offer và ghi nhận lý do | Offer status=PENDING, reason="Budget exceeded" | Status→REJECTED, WorkflowProducer nhận sự kiện từ chối | CheckDB: `verify(offerRepository).save(any())`. Rollback: Mockito. | CheckDB: status = REJECTED trong đối tượng saved |
| Từ chối Offer | `rejectStep()` | OFF-TC12 | Kiểm tra chặn từ chối Offer không ở PENDING | Offer status=DRAFT | Hệ thống ném `IllegalStateException` | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Trả về Offer | `returnOffer()` | OFF-TC13 | Kiểm tra business rule: workflow mới không hỗ trợ return | Bất kỳ offer | Luôn ném `IllegalStateException` — tính năng đã bị loại bỏ theo thiết kế hệ thống mới | CheckDB: `verify(offerRepository, never()).findById(any())`. Rollback: Mockito. | CheckDB: findById() không được gọi, count không đổi |
| Hủy Offer | `cancel()` | OFF-TC14 | Kiểm tra idempotent — gọi cancel lần 2 không gây tác dụng phụ | Offer status=CANCELLED | Trả về ngay với status CANCELLED, `save()` KHÔNG được gọi | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, status vẫn CANCELLED |
| Hủy Offer | `cancel()` | OFF-TC15 | Kiểm tra hủy Offer từ trạng thái PENDING | Offer status=PENDING | Status→CANCELLED, WorkflowProducer nhận sự kiện hủy | CheckDB: `verify(offerRepository).save(any())`. Rollback: Mockito. | CheckDB: status = CANCELLED trong đối tượng saved |
| Hủy Offer | `cancel()` | OFF-TC16 | Kiểm tra chặn hủy Offer đã được phê duyệt | Offer status=APPROVED | Hệ thống ném `IllegalStateException` — Offer đã chốt không thể hủy | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, status không đổi |
| Rút Offer | `withdraw()` | OFF-TC17 | Kiểm tra rút Offer bởi owner hợp lệ | Offer status=PENDING, ownerUserId=20; actorId=20 | Status→WITHDRAWN, WorkflowProducer nhận sự kiện | CheckDB: `verify(offerRepository).save(any())`. Rollback: Mockito. | CheckDB: status = WITHDRAWN trong đối tượng saved |
| Rút Offer | `withdraw()` | OFF-TC18 | Kiểm tra rút Offer bởi requester (khác owner) | Offer.requesterId=30, ownerUserId=20; actorId=30 | Status→WITHDRAWN — nghiệp vụ cho phép cả requester rút lại | CheckDB: `verify(offerRepository).save(any())`. Rollback: Mockito. | CheckDB: status = WITHDRAWN dù actorId khác ownerUserId |
| Rút Offer | `withdraw()` | OFF-TC19 | Kiểm tra chặn rút Offer không ở PENDING | Offer status=DRAFT | Hệ thống ném `IllegalStateException` | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Rút Offer | `withdraw()` | OFF-TC20 | Kiểm tra bảo mật — người lạ không được rút Offer | actorId=99 (không phải owner/requester) | Hệ thống ném `IllegalStateException` — phân quyền chặt chẽ | CheckDB: `verify(offerRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Xóa mềm Offer | `delete()` | OFF-TC21 | Kiểm tra soft delete — không xóa vật lý khỏi DB | Offer id=1 (tồn tại) | isActive=false, `repository.delete()` KHÔNG được gọi — dữ liệu vẫn còn để audit | CheckDB: `ArgumentCaptor` xác nhận isActive=false trong `save()`, `verify(never()).delete(any())`. Rollback: Mockito. | CheckDB: isActive = false, delete() không được gọi, count không đổi |

---

## Module 10 - Phần 3a: Thông báo (NotificationService)

**Script Test:** `services/notification-service/src/test/java/com/example/notification_service/service/NotificationServiceTest.java`

| Tên chức năng | Function | Testcase ID | Test Objective | Input | Expected Output | Minh chứng | Notes |
|---|---|---|---|---|---|---|---|
| Tạo thông báo | `createNotification()` | UTIL-NT01 | Kiểm tra tạo thông báo với đầy đủ metadata | recipientId=100, title="New Notification", message="Content" | deliveryStatus="SENT", sentAt được ghi nhận, SocketIO nhận sự kiện | CheckDB: `verify(notificationRepository, times(1)).save(any())`. `verify(socketIOBroadcastService).pushNotification(any())`. Rollback: Mockito. | CheckDB: deliveryStatus = "SENT", sentAt != null trong saved |
| Đánh dấu đã đọc | `markAsRead()` | UTIL-NT02 | Kiểm tra đánh dấu thông báo chưa đọc | notificationId=1 (isRead=false) | isRead=true, readAt được ghi nhận, unreadCount được broadcast | CheckDB: `ArgumentCaptor` xác nhận isRead=true trong `save()`. Rollback: Mockito. | CheckDB: isRead = true, readAt != null trong saved |
| Đánh dấu đã đọc | `markAsRead()` | UTIL-NT03 | Kiểm tra idempotent — thông báo đã đọc không cần ghi lại | notificationId=2 (isRead=true) | `save()` và `publishUnreadCount()` KHÔNG được gọi — tránh DB write thừa | CheckDB: `verify(notificationRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Đánh dấu đã đọc | `markAsRead()` | UTIL-NT04 | Kiểm tra xử lý thông báo không tồn tại | notificationId=999 | Hệ thống ném `NotificationNotFoundException` | CheckDB: `verify(notificationRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Đánh dấu tất cả đã đọc | `markAllAsRead()` | UTIL-NT05 | Kiểm tra đánh dấu hàng loạt và cập nhật unreadCount | recipientId=100, có 3 thông báo chưa đọc | Trả về count=3, broadcast unreadCount=0 | CheckDB: `verify(socketIOBroadcastService).publishUnreadCount(100L, 0L)`. Rollback: Mockito. | CheckDB: publishUnreadCount(100L, 0L) — unreadCount bằng 0 sau khi đánh dấu |
| Đánh dấu tất cả đã đọc | `markAllAsRead()` | UTIL-NT06 | Kiểm tra không broadcast khi không có gì thay đổi | recipientId=100, không có thông báo chưa đọc | Trả về 0, KHÔNG broadcast — tránh gửi WebSocket không cần thiết | CheckDB: `verify(socketIOBroadcastService, never()).publishUnreadCount(any(), any())`. Rollback: Mockito. | CheckDB: count không đổi, publishUnreadCount() không được gọi |
| Lọc thông báo | `getAllNotificationsWithFilters()` | UTIL-NT07 | Kiểm tra nhánh lọc theo recipientId | recipientId=100, status=null | Gọi `findByRecipientId(100, pageable)`, không gọi các nhánh khác | CheckDB: `verify(notificationRepository).findByRecipientId(100L, pageable)`. Rollback: Mockito. | CheckDB: findByRecipientId() gọi 1 lần, findAll() không được gọi |
| Lọc thông báo | `getAllNotificationsWithFilters()` | UTIL-NT08 | Kiểm tra nhánh lọc theo status | recipientId=null, status="SENT" | Gọi `findByDeliveryStatus("SENT", pageable)` | CheckDB: `verify(notificationRepository).findByDeliveryStatus("SENT", pageable)`. Rollback: Mockito. | CheckDB: findByDeliveryStatus() gọi 1 lần, findAll() không được gọi |
| Lọc thông báo | `getAllNotificationsWithFilters()` | UTIL-NT09 | Kiểm tra nhánh lấy tất cả khi không có filter | recipientId=null, status=null | Gọi `findAll(pageable)` | CheckDB: `verify(notificationRepository).findAll(pageable)`. Rollback: Mockito. | CheckDB: findAll() gọi 1 lần |
| Thống kê thông báo | `getNotificationStats()` | UTIL-NT10 | Kiểm tra thống kê cho người dùng cụ thể | recipientId=100 | Trả về unreadCount=3 của recipient=100, KHÔNG query toàn hệ thống | CheckDB: `verify(notificationRepository, never()).countByIsReadFalse()`. Rollback: Mockito. | CheckDB: countByIsReadFalse() không được gọi, unreadCount = 3 |
| Thống kê thông báo | `getNotificationStats()` | UTIL-NT11 | Kiểm tra thống kê toàn hệ thống | recipientId=null | Gọi `countByIsReadFalse()` toàn hệ thống, KHÔNG query theo recipient | CheckDB: `verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(any())`. Rollback: Mockito. | CheckDB: countByRecipientId() không được gọi, unreadCount = 15 |
| Xử lý sự kiện thông báo | `processNotificationEvent()` | UTIL-NT12 | Kiểm tra tạo thông báo cho 1 recipient | event.recipientId=100 | `createNotification()` gọi đúng 1 lần cho recipient=100 | CheckDB: `verify(notificationRepository, times(1)).save(any())`. Rollback: Mockito. | CheckDB: save() gọi đúng 1 lần |
| Xử lý sự kiện thông báo | `processNotificationEvent()` | UTIL-NT13 | Kiểm tra không tạo thông báo khi không có recipient | event không có recipientId, recipientIds, includeAllEmployees | `save()` KHÔNG được gọi — tránh tạo bản ghi rỗng | CheckDB: `verify(notificationRepository, never()).save(any())`. Rollback: Mockito. | CheckDB: count không đổi, save() không được gọi |
| Gửi thông báo hàng loạt | `createBulkNotificationsByConditions()` | UTIL-NT14 | Kiểm tra gửi cho tất cả nhân viên | includeAllEmployees=true, userService trả về [1L,2L,3L] | Tạo 3 thông báo, `save()` gọi 3 lần, `getAllEmployeeIds()` được gọi | CheckDB: `verify(notificationRepository, times(3)).save(any())`. `verify(userService).getAllEmployeeIds(any())`. Rollback: Mockito + MockedStatic. | CheckDB: save() gọi 3 lần, count tăng thêm 3 |
| Gửi thông báo hàng loạt | `createBulkNotificationsByConditions()` | UTIL-NT15 | Kiểm tra gửi cho danh sách cụ thể | recipientIds=[1L, 2L] | Tạo đúng 2 thông báo, trả về count=2 | CheckDB: `verify(notificationRepository, times(2)).save(any())`. Rollback: Mockito + MockedStatic. | CheckDB: save() gọi 2 lần, count tăng thêm 2 |

---

## Module 10 - Phần 3b: Upload file (CloudinaryService)

**Script Test:** `services/upload-service/src/test/java/com/example/upload_service/service/CloudinaryServiceTest.java`

| Tên chức năng | Function | Testcase ID | Test Objective | Input | Expected Output | Minh chứng | Notes |
|---|---|---|---|---|---|---|---|
| Upload file (raw) | `upload()` | UTIL-CL01 | Kiểm tra upload thành công và trả về Map kết quả của Cloudinary | MultipartFile hợp lệ (có bytes) | Trả về Map chứa "secure_url" từ Cloudinary | CheckDB: `verify(uploader, times(1)).upload(bytes, options)`. Rollback: Không có DB. Mockito. | CheckDB: uploader.upload() gọi 1 lần với đúng bytes |
| Upload file (URL) | `uploadFile()` | UTIL-CL02 | Kiểm tra trích xuất secure_url sau upload thành công | MultipartFile hợp lệ | Trả về chuỗi URL HTTPS an toàn từ Cloudinary | CheckDB: `verify(uploader, times(1)).upload(any(), any())`. Rollback: Không có DB. Mockito. | CheckDB: uploader.upload() gọi 1 lần, URL chứa "https://" |
| Upload file (URL) | `uploadFile()` | UTIL-CL03 | Kiểm tra xử lý lỗi IOException từ Cloudinary | Cloudinary throw IOException | Hệ thống ném `RuntimeException` với message rõ ràng cho người dùng | CheckDB: Không `save()` nào gọi. Rollback: Không có DB. Mockito. | CheckDB: count không đổi, IOException được wrap thành RuntimeException |
| Upload file (URL) | `uploadFile()` | UTIL-CL04 | Kiểm tra tính an toàn với file trống (0 bytes) | file.getBytes() = new byte[0] | Cloudinary vẫn được gọi, không ném NullPointerException | CheckDB: `verify(uploader, times(1)).upload(any(), any())`. Rollback: Không có DB. Mockito. | CheckDB: uploader.upload() vẫn được gọi với byte[0] |

---

## Module 10 - Phần 3c: Thống kê (StatisticsService)

**Script Test:** `services/statistics-service/src/test/java/com/example/statistics_service/service/StatisticsServiceTest.java`

| Tên chức năng | Function | Testcase ID | Test Objective | Input | Expected Output | Minh chứng | Notes |
|---|---|---|---|---|---|---|---|
| Thống kê tổng hợp | `getSummaryStatistics()` | UTIL-ST01 | Kiểm tra bộ lọc ngày tháng chỉ đếm đúng khoảng | [04/10, 04/20 trong khoảng], [03/15 ngoài khoảng]; startDate=04/01, endDate=04/30 | applications=2, hired=1 — chỉ đếm dữ liệu trong khoảng ngày được chỉ định | CheckDB: HTTP client, không có DB. Rollback: MockedStatic + Mockito. | CheckDB: applications = 2, hired = 1 (bỏ qua record ngoài khoảng) |
| Thống kê tổng hợp | `getSummaryStatistics()` | UTIL-ST02 | Kiểm tra giá trị mặc định ngày khi không chỉ định | startDate=null, endDate=null | Client được gọi với startDate=today, endDate=today+7 theo thiết kế hệ thống | CheckDB: `verify(candidateServiceClient).getApplicationsForStatistics(any(), null, today.toString(), (today+7).toString(), any(), any())`. Rollback: MockedStatic + Mockito. | CheckDB: client gọi với startDate = today, endDate = today+7 |
| Thống kê tổng hợp | `getSummaryStatistics()` | UTIL-ST03 | Kiểm tra đếm riêng từng trạng thái ứng viên | 1 HIRED, 2 REJECTED, 1 PENDING trong khoảng | hired=1, rejected=2, applications=4 — số liệu chính xác theo từng trạng thái | CheckDB: HTTP client. Rollback: MockedStatic + Mockito. | CheckDB: hired = 1, rejected = 2, applications = 4 |
| Thống kê tổng hợp | `getSummaryStatistics()` | UTIL-ST04 | Kiểm tra xử lý khi không có dữ liệu | Tất cả client trả về danh sách rỗng | Tất cả count = 0, không ném exception | CheckDB: HTTP client. Rollback: MockedStatic + Mockito. | CheckDB: applications = 0, hired = 0, count không đổi |
| Danh sách JD mở | `getJobOpenings()` | UTIL-ST05 | Kiểm tra phân loại vị trí làm việc từ xa | JobPosition.isRemote=true | workLocation="Remote" theo phân loại hệ thống | CheckDB: `verify(jobServiceClient).getJobPositions(...)`. Rollback: MockedStatic + Mockito. | CheckDB: workLocation = "Remote" trong kết quả |
| Danh sách JD mở | `getJobOpenings()` | UTIL-ST06 | Kiểm tra phân loại vị trí Hybrid | isRemote=false, location="HCM - Hybrid" | workLocation="Hybrid" khi location chứa từ khóa Hybrid | CheckDB: `verify(jobServiceClient).getJobPositions(...)`. Rollback: MockedStatic + Mockito. | CheckDB: workLocation = "Hybrid" trong kết quả |
| Danh sách JD mở | `getJobOpenings()` | UTIL-ST07 | Kiểm tra phân loại vị trí tại văn phòng | isRemote=false, location="Ha Noi" | workLocation="On-site" — giá trị mặc định khi không có keyword đặc biệt | CheckDB: `verify(jobServiceClient).getJobPositions(...)`. Rollback: MockedStatic + Mockito. | CheckDB: workLocation = "On-site" trong kết quả |
| Danh sách JD mở | `getJobOpenings()` | UTIL-ST08 | Kiểm tra định dạng hiển thị lương khi có đủ 2 mức | salaryMin=10M, salaryMax=20M | salaryDisplay chứa "trieu" và dấu " - " phân cách | CheckDB: Không. Rollback: MockedStatic + Mockito. | CheckDB: salaryDisplay chứa " - " và "trieu" |
| Danh sách JD mở | `getJobOpenings()` | UTIL-ST09 | Kiểm tra an toàn khi service trả về null | jobServiceClient trả về null | Trả về danh sách rỗng, không ném NullPointerException | CheckDB: Không. Rollback: MockedStatic + Mockito. | CheckDB: result.size() = 0, không NPE |
| Danh sách JD mở | `getJobOpenings()` | UTIL-ST10 | Kiểm tra hiển thị lương khi cả hai null | salaryMin=null, salaryMax=null | salaryDisplay="" (chuỗi rỗng) — giao diện không hiển thị thông tin sai | CheckDB: Không. Rollback: MockedStatic + Mockito. | CheckDB: salaryDisplay = "" (chuỗi rỗng) |
| Lịch phỏng vấn sắp tới | `getUpcomingSchedules()` | UTIL-ST11 | Kiểm tra trích xuất tên ứng viên từ participants | participants có 1 mục type=CANDIDATE, name="Nguyen Van A" | candidateName="Nguyen Van A" | CheckDB: `verify(communicationServiceClient).getUpcomingSchedules(...)`. Rollback: MockedStatic + Mockito. | CheckDB: candidateName = "Nguyen Van A" trong kết quả |
| Lịch phỏng vấn sắp tới | `getUpcomingSchedules()` | UTIL-ST12 | Kiểm tra khi không có participant CANDIDATE | participants rỗng | candidateName="" — tránh NullPointerException, hiển thị an toàn | CheckDB: Không. Rollback: MockedStatic + Mockito. | CheckDB: candidateName = "" (chuỗi rỗng), không NPE |
| Phân quyền thống kê | `getDepartmentIdForStatistics()` | UTIL-ST13 | Kiểm tra CEO xem tất cả phòng ban | SecurityUtil.extractUserRole()="CEO" | candidateServiceClient được gọi với departmentId=null (không lọc phòng ban) | CheckDB: `verify(candidateServiceClient).getApplicationsForStatistics(any(), null, any(), any(), any(), isNull())`. Rollback: MockedStatic + Mockito. | CheckDB: getApplicationsForStatistics() gọi với departmentId = null |

---

## Tổng kết

| Phần | Service | Class | Số Test Cases | File Test |
|---|---|---|---|---|
| 1 — Vị trí tuyển dụng | job-service | `JobPositionService` | 27 | JobPositionServiceTest.java |
| 2 — Offer | job-service | `OfferService` | 21 | OfferServiceTest.java |
| 3a — Thông báo | notification-service | `NotificationService` | 15 | NotificationServiceTest.java |
| 3b — Upload file | upload-service | `CloudinaryService` | 4 | CloudinaryServiceTest.java |
| 3c — Thống kê | statistics-service | `StatisticsService` | 13 | StatisticsServiceTest.java |
| **Tổng** | **4 services** | **5 classes** | **80 test cases** | |

## Chiến lược Minh chứng tổng quát

| Kỹ thuật | Mô tả | Sử dụng khi |
|---|---|---|
| `verify(repo, times(1)).save(any())` | Xác nhận DB được gọi đúng 1 lần | Luôn dùng cho Happy Path có ghi dữ liệu |
| `verify(repo, never()).save(any())` | Xác nhận DB KHÔNG bị gọi | Exception path, Idempotent case |
| `ArgumentCaptor<T>.getValue()` | Bắt và inspect đối tượng truyền vào save() | Kiểm tra field cụ thể (status, isActive, ...) |
| `MockedStatic<T>` | Mock phương thức static (SecurityUtil) | Test StatisticsService, NotificationService |
| Mockito reset tự động | Mỗi `@Test` được cách ly, không dùng `@Transactional` | Đảm bảo Rollback tự động, test độc lập |

---

## Hướng dẫn chạy Unit Test

### Yêu cầu môi trường

#### Chạy trực tiếp (Maven local)

| Công cụ | Phiên bản tối thiểu | Ghi chú |
|---|---|---|
| Java JDK | 17 | Kiểm tra: `java -version` |
| Apache Maven | 3.8+ | Kiểm tra: `mvn -version` |
| Kết nối internet | Lần đầu | Để Maven tải dependencies về |

> Các test sử dụng **Mockito** (đã có sẵn trong `spring-boot-starter-test`). Không cần cài thêm gì ngoài JDK và Maven.

#### Chạy bằng Docker

| Công cụ | Phiên bản tối thiểu | Ghi chú |
|---|---|---|
| Docker Engine | 24.0+ | Kiểm tra: `docker --version` |
| Docker Compose | 2.20+ (plugin) | Kiểm tra: `docker compose version` |
| Kết nối internet | Lần đầu | Để pull image Maven/OpenJDK |

> Ưu điểm khi dùng Docker: **không cần cài JDK hay Maven trên máy**, môi trường chạy test nhất quán giữa các máy trong nhóm.

---

### Cách 5: Chạy test bằng Docker (không cần cài JDK/Maven)

#### Nguyên lý hoạt động

Docker sẽ khởi động một container tạm thời dùng image `maven:3.9-eclipse-temurin-17`, mount source code vào bên trong, chạy `mvn test`, rồi in kết quả ra terminal và tự hủy container sau khi xong.

```
Host machine
└── docker run (maven image)
    └── /app  <-- source code được mount vào đây
        └── mvn test  <-- chạy trong container, không cần JDK trên máy
```

---

#### 5a. Chạy toàn bộ test của một service

**job-service** (JobPositionServiceTest + OfferServiceTest):
```bash
docker run --rm \
  -v "$(pwd)/recruitment-backend/services/job-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dmaven.repo.local=/app/.m2
```

**notification-service** (NotificationServiceTest):
```bash
docker run --rm \
  -v "$(pwd)/recruitment-backend/services/notification-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dmaven.repo.local=/app/.m2
```

**upload-service** (CloudinaryServiceTest):
```bash
docker run --rm \
  -v "$(pwd)/recruitment-backend/services/upload-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dmaven.repo.local=/app/.m2
```

**statistics-service** (StatisticsServiceTest):
```bash
docker run --rm \
  -v "$(pwd)/recruitment-backend/services/statistics-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dmaven.repo.local=/app/.m2
```

> **Lưu ý Windows (PowerShell):** Thay `$(pwd)` bằng `${PWD}` hoặc dùng đường dẫn tuyệt đối:
> ```powershell
> docker run --rm `
>   -v "${PWD}/recruitment-backend/services/job-service:/app" `
>   -w /app `
>   maven:3.9-eclipse-temurin-17 `
>   mvn test -Dmaven.repo.local=/app/.m2
> ```

---

#### 5b. Chạy một file test cụ thể bằng Docker

Thêm tham số `-Dtest=<TênClass>` vào cuối lệnh:

```bash
# Chạy chỉ JobPositionServiceTest
docker run --rm \
  -v "${PWD}/recruitment-backend/services/job-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=JobPositionServiceTest -Dmaven.repo.local=/app/.m2

# Chạy chỉ OfferServiceTest
docker run --rm \
  -v "${PWD}/recruitment-backend/services/job-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=OfferServiceTest -Dmaven.repo.local=/app/.m2

# Chạy chỉ NotificationServiceTest
docker run --rm \
  -v "${PWD}/recruitment-backend/services/notification-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=NotificationServiceTest -Dmaven.repo.local=/app/.m2

# Chạy chỉ CloudinaryServiceTest
docker run --rm \
  -v "${PWD}/recruitment-backend/services/upload-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=CloudinaryServiceTest -Dmaven.repo.local=/app/.m2

# Chạy chỉ StatisticsServiceTest
docker run --rm \
  -v "${PWD}/recruitment-backend/services/statistics-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest=StatisticsServiceTest -Dmaven.repo.local=/app/.m2
```

---

#### 5c. Chạy một test case cụ thể bằng Docker

```bash
# Chạy test case JOB-TC01
docker run --rm \
  -v "${PWD}/recruitment-backend/services/job-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest="JobPositionServiceTest#create_ValidDtoWithSalary_ShouldSaveWithDtoSalaryAndSetDraftStatus" \
  -Dmaven.repo.local=/app/.m2

# Chạy test case OFF-TC21 (soft delete)
docker run --rm \
  -v "${PWD}/recruitment-backend/services/job-service:/app" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dtest="OfferServiceTest#delete_ExistingOffer_ShouldSetIsActiveFalseAndReturnTrue" \
  -Dmaven.repo.local=/app/.m2
```

---

#### 5d. Dùng Docker Compose để chạy tất cả services cùng lúc

Tạo file `docker-compose.test.yml` tại thư mục gốc dự án với nội dung:

```yaml
version: "3.8"
services:
  test-job-service:
    image: maven:3.9-eclipse-temurin-17
    volumes:
      - ./recruitment-backend/services/job-service:/app
      - job-service-m2:/app/.m2
    working_dir: /app
    command: mvn test -Dmaven.repo.local=/app/.m2

  test-notification-service:
    image: maven:3.9-eclipse-temurin-17
    volumes:
      - ./recruitment-backend/services/notification-service:/app
      - notification-m2:/app/.m2
    working_dir: /app
    command: mvn test -Dmaven.repo.local=/app/.m2

  test-upload-service:
    image: maven:3.9-eclipse-temurin-17
    volumes:
      - ./recruitment-backend/services/upload-service:/app
      - upload-m2:/app/.m2
    working_dir: /app
    command: mvn test -Dmaven.repo.local=/app/.m2

  test-statistics-service:
    image: maven:3.9-eclipse-temurin-17
    volumes:
      - ./recruitment-backend/services/statistics-service:/app
      - statistics-m2:/app/.m2
    working_dir: /app
    command: mvn test -Dmaven.repo.local=/app/.m2

volumes:
  job-service-m2:
  notification-m2:
  upload-m2:
  statistics-m2:
```

Sau đó chạy:

```bash
# Chạy tất cả 4 service song song
docker compose -f docker-compose.test.yml up --abort-on-container-failure

# Xem log riêng từng service
docker compose -f docker-compose.test.yml logs test-job-service
docker compose -f docker-compose.test.yml logs test-notification-service

# Dọn dẹp sau khi xong
docker compose -f docker-compose.test.yml down -v
```

---

#### Đọc kết quả từ Docker

Kết quả hiển thị giống hệt Maven local trong terminal Docker:

```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0  <- JobPositionServiceTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0  <- OfferServiceTest
[INFO] BUILD SUCCESS
```

Báo cáo HTML Surefire được tạo **bên trong container**, nếu muốn lưu ra máy host thêm volume:

```bash
docker run --rm \
  -v "${PWD}/recruitment-backend/services/job-service:/app" \
  -v "${PWD}/test-reports/job-service:/app/target/surefire-reports" \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn test -Dmaven.repo.local=/app/.m2
# Báo cáo được lưu tại: ./test-reports/job-service/
```

---

#### So sánh hai cách chạy

| Tiêu chí | Maven local | Docker |
|---|---|---|
| Yêu cầu cài đặt | JDK 17 + Maven | Docker Engine |
| Tốc độ lần đầu | Nhanh | Chậm hơn (pull image ~500MB) |
| Tốc độ lần sau | Nhanh | Nhanh (image được cache) |
| Tính nhất quán | Phụ thuộc máy | Giống nhau mọi máy |
| Chạy song song nhiều service | Phức tạp | Dễ dàng với docker compose |
| Phù hợp | Lập trình viên cá nhân | CI/CD, demo nhóm |

---

### Cách 1: Chạy toàn bộ test của một service

Vào thư mục của service cần chạy, sau đó chạy lệnh Maven:

**job-service** (chứa JobPositionServiceTest + OfferServiceTest):
```bash
cd recruitment-backend/services/job-service
mvn test
```

**notification-service** (chứa NotificationServiceTest):
```bash
cd recruitment-backend/services/notification-service
mvn test
```

**upload-service** (chứa CloudinaryServiceTest):
```bash
cd recruitment-backend/services/upload-service
mvn test
```

**statistics-service** (chứa StatisticsServiceTest):
```bash
cd recruitment-backend/services/statistics-service
mvn test
```

---

### Cách 2: Chạy một file test cụ thể

Dùng tham số `-Dtest=<TênClass>` để chạy đúng một class test:

```bash
# Chạy chỉ JobPositionServiceTest
cd recruitment-backend/services/job-service
mvn test -Dtest=JobPositionServiceTest

# Chạy chỉ OfferServiceTest
mvn test -Dtest=OfferServiceTest

# Chạy chỉ NotificationServiceTest
cd recruitment-backend/services/notification-service
mvn test -Dtest=NotificationServiceTest

# Chạy chỉ CloudinaryServiceTest
cd recruitment-backend/services/upload-service
mvn test -Dtest=CloudinaryServiceTest

# Chạy chỉ StatisticsServiceTest
cd recruitment-backend/services/statistics-service
mvn test -Dtest=StatisticsServiceTest
```

---

### Cách 3: Chạy một test case cụ thể theo tên method

Dùng tham số `-Dtest=<TênClass>#<tênMethod>` để chạy đúng một test case:

```bash
# Chạy test case JOB-TC01 (create với salary từ DTO)
cd recruitment-backend/services/job-service
mvn test -Dtest="JobPositionServiceTest#create_ValidDtoWithSalary_ShouldSaveWithDtoSalaryAndSetDraftStatus"

# Chạy test case OFF-TC13 (returnOffer luôn throw)
mvn test -Dtest="OfferServiceTest#returnOffer_AnyOffer_ShouldAlwaysThrowIllegalStateException"

# Chạy test case UTIL-CL03 (uploadFile IOException)
cd recruitment-backend/services/upload-service
mvn test -Dtest="CloudinaryServiceTest#uploadFile_CloudinaryThrowsIOException_ShouldThrowRuntimeException"
```

---

### Cách 4: Chạy nhiều class cùng lúc (job-service)

```bash
cd recruitment-backend/services/job-service
mvn test -Dtest="JobPositionServiceTest,OfferServiceTest"
```

---

### Đọc kết quả sau khi chạy

Sau khi chạy, Maven in ra kết quả dạng:

```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0  <- JobPositionServiceTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0  <- OfferServiceTest
[INFO] BUILD SUCCESS
```

| Ký hiệu | Ý nghĩa |
|---|---|
| `Tests run: N` | Số test đã chạy |
| `Failures: 0` | Số test sai kết quả (assertion fail) |
| `Errors: 0` | Số test bị lỗi runtime (exception không mong muốn) |
| `Skipped: 0` | Số test bị bỏ qua (`@Disabled`) |
| `BUILD SUCCESS` | Tất cả test pass |
| `BUILD FAILURE` | Có ít nhất 1 test fail hoặc error |

Báo cáo chi tiết dạng HTML được lưu tại:
```
target/surefire-reports/index.html
```

---

### Mẹo khi test bị lỗi

| Tình huống | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `UnnecessaryStubbingException` | Mock được khai báo nhưng không được gọi trong test | Xóa `when(...)` không dùng hoặc thêm `@MockitoSettings(strictness = Strictness.LENIENT)` |
| `NullPointerException` trong test | Quên mock một dependency | Kiểm tra `@Mock` đã đủ chưa, xem stack trace để xác định method nào trả về null |
| `Cannot mock final class` | Mockito mặc định không mock được class final | Thêm file `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` với nội dung `mock-maker-inline` |
| Test pass local nhưng fail CI | Phụ thuộc vào thứ tự chạy test | Đảm bảo mỗi test độc lập, không dùng biến static dùng chung |


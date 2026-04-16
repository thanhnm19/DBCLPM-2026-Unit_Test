# Kế Hoạch Kiểm Thử Đơn Vị (Unit Test Plan)

**Người phụ trách:** Thành viên 4
**Ngày lên kịch bản:** 2026-04-16
**Phạm vi:** job-service (Offer/JobPosition), statistics-service, upload-service, notification-service

---

## Mục lục

- [Phần 1: Vị trí tuyển dụng (JobPositionService)](#phan-1-vi-tri-tuyen-dung)
- [Phần 2: Offer (OfferService)](#phan-2-offer)
- [Phần 3: Bổ trợ tiện ích (NotificationService, CloudinaryService, StatisticsService)](#phan-3-bo-tro-tien-ich)

---

# PHẦN 1: VỊ TRÍ TUYỂN DỤNG

**Class:** `JobPositionService`
**Service:** `job-service`
**Module:** 6 — Quản lý vị trí tuyển dụng (Kho JD, yêu cầu kỹ năng)

---

## 1. Bảng Scope of Testing

| File / Class / Function | Test / Không Test | Lý do chi tiết |
|---|---|---|
| `JobPositionService` | Test | Chứa Business logic cốt lõi: kiểm tra trạng thái (publish, close, reopen), fallback salary từ RecruitmentRequest, phân trang tùy chỉnh với IDs. |
| `JobPositionService.create()` | Test | Logic chọn salary (DTO vs RecruitmentRequest), set default isRemote=false, set status=DRAFT, gọi recruitmentRequestService.changeStatus(). |
| `JobPositionService.findById()` | Test | Trường hợp tìm thấy và không tìm thấy (throw IdInvalidException). |
| `JobPositionService.getByIdWithPublished()` | Test | Kiểm tra status guard: PUBLISHED thì trả về; các status khác throw exception. |
| `JobPositionService.findAllWithFiltersSimple()` | Test | Phân nhánh: có ids -> parse và query; không ids -> query theo filter. Edge case: ids rỗng, ids sai định dạng. |
| `JobPositionService.findAllWithFiltersSimplePaged()` | Test | Logic phân trang thủ công với IDs: tính start/end, PageImpl. Edge case: page vượt quá tổng số phần tử. |
| `JobPositionService.update()` | Test | Chỉ cập nhật các trường không null (partial update). Kiểm tra trường hợp ID không tồn tại. |
| `JobPositionService.delete()` | Test | Gọi delete trên repository; exception khi ID không tồn tại. |
| `JobPositionService.publish()` | Test | Chỉ cho phép khi status = DRAFT, set PUBLISHED + publishedAt. Exception khi sai trạng thái. |
| `JobPositionService.close()` | Test | Chỉ cho phép khi status = PUBLISHED, set CLOSED. Exception khi sai trạng thái. |
| `JobPositionService.reopen()` | Test | Chỉ cho phép khi status = CLOSED, set PUBLISHED. Exception khi sai trạng thái. |
| `JobPositionService.getByIdWithDepartmentName()` | Test | Gọi userService và candidateClient (đều phải mock). Kiểm tra set DepartmentName và ApplicationCount. |
| `JobPositionService.getByIdsWithDepartmentName()` | Test | Phân nhánh ids null/rỗng, ids có giá trị. Mock userService.getDepartmentsByIds(). |
| `JobPositionService.findAllWithFilters()` | Test | departmentId == 1 được chuyển thành null trước khi query. Gọi userService + candidateClient (mock). |
| `JobPositionRepository` | Không Test | Spring Data JPA repository, không có business logic riêng. Được kiểm tra gián tiếp qua service test. |
| `UserClient` (FeignClient) | Không Test | HTTP client gọi sang user-service — bắt buộc Mock, không test logic service khác. |
| `CandidateClient` (FeignClient) | Không Test | HTTP client gọi sang candidate-service — bắt buộc Mock. |
| `RecruitmentRequestService` | Không Test | Phạm vi của Thành viên 2. Được mock trong test của JobPositionService. |
| `CreateJobPositionDTO`, `UpdateJobPositionDTO`, `JobPositionResponseDTO` | Không Test | DTO/data container (Lombok), không có logic. Được kiểm tra gián tiếp qua input/output service test. |
| `JobPosition` (Entity) | Không Test | JPA Entity thuần túy, không có business logic. |
| `JobPositionStatus` (Enum) | Không Test | Enum đơn giản, không có logic tính toán. |

---

## 2. Danh sách Kịch bản (Test Cases)

### Hàm `create(CreateJobPositionDTO dto)` — Độ phức tạp: 2 nhánh if (salary fallback, isRemote default)

- [ ] [Happy Path] - `create_ValidDtoWithSalary_ShouldSaveWithDtoSalaryAndSetDraftStatus`: DTO có salaryMin/salaryMax đầy đủ, kiểm tra position được lưu với salary từ DTO (không lấy từ RR), status = DRAFT.
- [ ] [Happy Path] - `create_DtoWithNullSalary_ShouldFallbackToRecruitmentRequestSalary`: DTO có salaryMin/salaryMax = null, kiểm tra position được lưu với salary lấy từ RecruitmentRequest.
- [ ] [Happy Path] - `create_DtoWithNullIsRemote_ShouldDefaultToFalse`: isRemote = null trong DTO, kiểm tra position.isRemote = false (giá trị mặc định).
- [ ] [Happy Path] - `create_ValidDto_ShouldChangeRecruitmentRequestStatusToCompleted`: Kiểm tra recruitmentRequestService.changeStatus() được gọi với status = COMPLETED.
- [ ] [Exception] - `create_WithNonExistentRecruitmentRequestId_ShouldThrowIdInvalidException`: recruitmentRequestService.findById() throw exception, kiểm tra exception lan truyền lên.

### Hàm `findById(Long id)` — Độ phức tạp: 1 nhánh

- [ ] [Happy Path] - `findById_ExistingId_ShouldReturnJobPosition`: ID tồn tại, trả về JobPosition đúng.
- [ ] [Exception] - `findById_NonExistentId_ShouldThrowIdInvalidException`: ID không tồn tại, kiểm tra throw IdInvalidException với message phù hợp.

### Hàm `getByIdWithPublished(Long id)` — Độ phức tạp: 2 nhánh (status check)

- [ ] [Happy Path] - `getByIdWithPublished_PublishedPosition_ShouldReturnPosition`: status = PUBLISHED, kết quả trả về chính Position đó.
- [ ] [Negative] - `getByIdWithPublished_DraftPosition_ShouldThrowIdInvalidException`: status = DRAFT, kiểm tra throw IdInvalidException.
- [ ] [Negative] - `getByIdWithPublished_ClosedPosition_ShouldThrowIdInvalidException`: status = CLOSED, kiểm tra throw IdInvalidException.

### Hàm `findAllWithFiltersSimple(...)` — Độ phức tạp: 3 nhánh (ids null, ids hợp lệ, ids lỗi định dạng)

- [ ] [Happy Path] - `findAllWithFiltersSimple_WithValidIds_ShouldQueryByIdIn`: ids = "1,2,3", kiểm tra gọi repository.findByIdIn() với list [1, 2, 3].
- [ ] [Happy Path] - `findAllWithFiltersSimple_WithNullIds_ShouldQueryByFilters`: ids = null, kiểm tra gọi repository.findByFilters().
- [ ] [Edge Case] - `findAllWithFiltersSimple_WithBlankIds_ShouldQueryByFilters`: ids = "   " (chuỗi trắng), kiểm tra fallback về queryByFilters.
- [ ] [Negative] - `findAllWithFiltersSimple_WithInvalidIdFormat_ShouldNotThrowAndFallbackToFilters`: ids chứa ký tự không hợp lệ ("abc,1"), kiểm tra không throw exception, fallback về findByFilters().

### Hàm `findAllWithFiltersSimplePaged(...)` — Độ phức tạp: 4 nhánh

- [ ] [Happy Path] - `findAllWithFiltersSimplePaged_WithIds_ShouldReturnCorrectPageAndMeta`: ids hợp lệ, page 1, pageSize 2. Kiểm tra content và meta (page, pageSize, total).
- [ ] [Happy Path] - `findAllWithFiltersSimplePaged_WithNullIds_ShouldQueryByFilters`: ids = null, kiểm tra gọi repository.findByFilters().
- [ ] [Edge Case] - `findAllWithFiltersSimplePaged_WithIds_PageBeyondTotal_ShouldReturnEmptyContent`: số page vượt quá tổng phần tử, content phải rỗng.
- [ ] [Edge Case] - `findAllWithFiltersSimplePaged_WithEmptyResultFromIds_ShouldReturnEmptyPage`: idList hợp lệ nhưng repository trả về danh sách rỗng, total = 0.

### Hàm `update(Long id, UpdateJobPositionDTO dto)` — Độ phức tạp: 10+ nhánh null-check

- [ ] [Happy Path] - `update_AllFieldsProvided_ShouldUpdateAllFields`: DTO có tất cả trường khác null, kiểm tra tất cả trường được cập nhật.
- [ ] [Happy Path] - `update_OnlyTitleProvided_ShouldUpdateOnlyTitle`: Chỉ có title trong DTO, kiểm tra chỉ title thay đổi, các trường khác giữ nguyên.
- [ ] [Exception] - `update_NonExistentId_ShouldThrowIdInvalidException`: ID không tồn tại, kiểm tra throw IdInvalidException.

### Hàm `delete(Long id)` — Độ phức tạp: 1 nhánh

- [ ] [Happy Path] - `delete_ExistingId_ShouldCallRepositoryDeleteAndReturnTrue`: Xóa thành công, repository.delete() được gọi, hàm trả về true.
- [ ] [Exception] - `delete_NonExistentId_ShouldThrowIdInvalidException`: ID không tồn tại, kiểm tra exception.

### Hàm `publish(Long id)` — Độ phức tạp: 2 nhánh (status guard)

- [ ] [Happy Path] - `publish_DraftPosition_ShouldSetPublishedStatusAndPublishedAt`: status = DRAFT, kiểm tra status chuyển thành PUBLISHED và publishedAt != null.
- [ ] [Negative] - `publish_PublishedPosition_ShouldThrowIdInvalidException`: status = PUBLISHED (không phải DRAFT), throw IdInvalidException.
- [ ] [Negative] - `publish_ClosedPosition_ShouldThrowIdInvalidException`: status = CLOSED, throw IdInvalidException.

### Hàm `close(Long id)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `close_PublishedPosition_ShouldSetClosedStatus`: status = PUBLISHED, kiểm tra status chuyển thành CLOSED.
- [ ] [Negative] - `close_DraftPosition_ShouldThrowIdInvalidException`: status = DRAFT, throw IdInvalidException.

### Hàm `reopen(Long id)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `reopen_ClosedPosition_ShouldSetPublishedStatus`: status = CLOSED, kiểm tra status chuyển thành PUBLISHED.
- [ ] [Negative] - `reopen_PublishedPosition_ShouldThrowIdInvalidException`: status = PUBLISHED, throw IdInvalidException.

### Hàm `findAllWithFilters(...)` — Độ phức tạp: 2 nhánh (departmentId == 1 -> null)

- [ ] [Happy Path] - `findAllWithFilters_DepartmentIdIsOne_ShouldBeNullifiedBeforeQueryRepository`: departmentId = 1 chuyển thành null trước khi gọi repository. Mock userService và candidateClient.
- [ ] [Happy Path] - `findAllWithFilters_NormalDepartmentId_ShouldPassThroughToRepository`: departmentId = 5, giữ nguyên khi gọi repository.

---

# PHẦN 2: OFFER

**Class:** `OfferService`
**Service:** `job-service`
**Module:** 8 — Offer (Hợp đồng, lương thưởng, thời hạn offer)

---

## 1. Bảng Scope of Testing

| File / Class / Function | Test / Không Test | Lý do chi tiết |
|---|---|---|
| `OfferService` | Test | Chứa Business logic cốt lõi: State Machine (DRAFT -> PENDING -> APPROVED/REJECTED), kiểm tra quyền withdraw, logic cancel idempotent. |
| `OfferService.create()` | Test | Tạo Offer mới với status = DRAFT, isActive = true, map các trường từ DTO. |
| `OfferService.update()` | Test | Guard: chỉ được update khi status = DRAFT. Partial update các trường không null. |
| `OfferService.submit()` | Test | Guard: chỉ từ DRAFT; kiểm tra workflowId != null; chuyển sang PENDING; gọi workflowProducer (mock). |
| `OfferService.approveStep()` | Test | Guard: chỉ từ PENDING; gọi workflowProducer (mock) với event "REQUEST_APPROVED". |
| `OfferService.rejectStep()` | Test | Guard: chỉ từ PENDING; chuyển sang REJECTED; gọi workflowProducer (mock). |
| `OfferService.returnOffer()` | Test | Business rule mới: luôn throw IllegalStateException (workflow không hỗ trợ return). |
| `OfferService.cancel()` | Test | 3 nhánh: đã CANCELLED trả về luôn; APPROVED/REJECTED throw; các trạng thái khác -> CANCELLED. |
| `OfferService.withdraw()` | Test | 2 guard: status phải PENDING; actorId phải là owner hoặc requester. |
| `OfferService.findById()` | Test | Tìm thấy và không tìm thấy (throw IdInvalidException). |
| `OfferService.delete()` | Test | Soft delete: set isActive = false, không xóa khỏi DB. |
| `OfferService.getAllWithFilters()` | Test | Parse status string (hợp lệ, sai enum, null/rỗng) -> query repository. |
| `OfferRepository` | Không Test | Spring Data JPA, không có logic riêng. |
| `UserClient`, `WorkflowClient`, `CandidateClient` | Không Test | FeignClient gọi sang service khác — bắt buộc Mock. |
| `OfferWorkflowProducer` | Không Test | Kafka producer — bắt buộc Mock, không test message broker. |
| `CreateOfferDTO`, `UpdateOfferDTO`, v.v. | Không Test | DTO Lombok, không có logic. |
| `Offer` (Entity) | Không Test | JPA Entity thuần túy. |
| `OfferStatus` (Enum) | Không Test | Enum đơn giản. |

---

## 2. Danh sách Kịch bản (Test Cases)

### Hàm `create(CreateOfferDTO dto)` — Độ phức tạp: 1 nhánh

- [ ] [Happy Path] - `create_ValidDto_ShouldSaveOfferWithDraftStatusAndIsActiveTrue`: DTO hợp lệ, kiểm tra offer được lưu với status=DRAFT, isActive=true, các trường khác map đúng.

### Hàm `update(Long id, UpdateOfferDTO dto)` — Độ phức tạp: 2 nhánh (status guard + null-check)

- [ ] [Happy Path] - `update_DraftOfferWithValidFields_ShouldUpdateNonNullFields`: Offer ở DRAFT, DTO có một số trường, kiểm tra chỉ cập nhật trường không null.
- [ ] [Negative] - `update_PendingOffer_ShouldThrowIllegalStateException`: Offer ở PENDING, throw IllegalStateException.
- [ ] [Negative] - `update_ApprovedOffer_ShouldThrowIllegalStateException`: Offer ở APPROVED, throw IllegalStateException.

### Hàm `submit(Long id, Long actorId, String token)` — Độ phức tạp: 3 nhánh

- [ ] [Happy Path] - `submit_DraftOfferWithWorkflowId_ShouldChangeStatusToPendingAndPublishEvent`: Offer ở DRAFT có workflowId, kiểm tra status = PENDING, submittedAt != null, workflowProducer được gọi.
- [ ] [Happy Path] - `submit_DraftOfferWithNullRequesterId_ShouldSetRequesterIdFromActor`: requesterId = null, kiểm tra được set bằng actorId sau submit.
- [ ] [Negative] - `submit_PendingOffer_ShouldThrowIllegalStateException`: Offer đã PENDING, throw IllegalStateException.
- [ ] [Negative] - `submit_DraftOfferWithNullWorkflowId_ShouldThrowIllegalStateException`: workflowId = null, throw IllegalStateException.

### Hàm `approveStep(Long id, ApproveOfferDTO dto, Long actorId, String token)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `approveStep_PendingOffer_ShouldSaveAndPublishApprovalEvent`: Offer ở PENDING, workflowProducer được gọi với eventType = "REQUEST_APPROVED".
- [ ] [Negative] - `approveStep_DraftOffer_ShouldThrowIllegalStateException`: Offer ở DRAFT, throw IllegalStateException.

### Hàm `rejectStep(Long id, RejectOfferDTO dto, Long actorId, String token)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `rejectStep_PendingOffer_ShouldSetRejectedStatusAndPublishEvent`: Offer ở PENDING, status chuyển sang REJECTED, workflowProducer được gọi.
- [ ] [Negative] - `rejectStep_DraftOffer_ShouldThrowIllegalStateException`: Offer ở DRAFT, throw IllegalStateException.

### Hàm `returnOffer(...)` — Độ phức tạp: 0 nhánh (always throw)

- [ ] [Exception] - `returnOffer_AnyOffer_ShouldAlwaysThrowIllegalStateException`: Gọi hàm này với bất kỳ offer nào, luôn throw IllegalStateException (business rule mới: workflow không hỗ trợ return).

### Hàm `cancel(Long id, CancelOfferDTO dto, Long actorId, String token)` — Độ phức tạp: 3 nhánh

- [ ] [Happy Path] - `cancel_AlreadyCancelledOffer_ShouldReturnImmediatelyWithoutSaving`: Offer đã CANCELLED, hàm trả về ngay, repository.save() KHÔNG được gọi (idempotent).
- [ ] [Happy Path] - `cancel_PendingOffer_ShouldSetCancelledStatusAndPublishEvent`: Offer ở PENDING, status -> CANCELLED, workflowProducer được gọi.
- [ ] [Negative] - `cancel_ApprovedOffer_ShouldThrowIllegalStateException`: Offer ở APPROVED, throw IllegalStateException.
- [ ] [Negative] - `cancel_RejectedOffer_ShouldThrowIllegalStateException`: Offer ở REJECTED, throw IllegalStateException.

### Hàm `withdraw(Long id, WithdrawOfferDTO dto, Long actorId, String token)` — Độ phức tạp: 3 nhánh

- [ ] [Happy Path] - `withdraw_PendingOfferByOwner_ShouldSetWithdrawnStatusAndPublishEvent`: actorId == ownerUserId, status PENDING -> WITHDRAWN.
- [ ] [Happy Path] - `withdraw_PendingOfferByRequester_ShouldSetWithdrawnStatus`: actorId == requesterId (khác owner), vẫn được phép withdraw.
- [ ] [Negative] - `withdraw_DraftOffer_ShouldThrowIllegalStateException`: Offer ở DRAFT, throw IllegalStateException.
- [ ] [Negative] - `withdraw_PendingOfferByUnauthorizedUser_ShouldThrowIllegalStateException`: actorId không phải owner lẫn requester, throw IllegalStateException.

### Hàm `delete(Long id)` — soft delete — Độ phức tạp: 1 nhánh

- [ ] [Happy Path] - `delete_ExistingOffer_ShouldSetIsActiveFalseAndReturnTrue`: isActive = false sau khi gọi, không gọi repository.delete() (xóa vật lý).
- [ ] [Exception] - `delete_NonExistentOffer_ShouldThrowIdInvalidException`: ID không tồn tại, throw IdInvalidException.

### Hàm `getAllWithFilters(...)` — Độ phức tạp: 3 nhánh (parse status)

- [ ] [Happy Path] - `getAllWithFilters_ValidStatusString_ShouldParseToEnumAndQueryCorrectly`: status = "PENDING", repository được gọi với enum OfferStatus.PENDING.
- [ ] [Negative] - `getAllWithFilters_InvalidStatusString_ShouldIgnoreStatusAndQueryWithNullEnum`: status = "INVALID_XYZ", KHÔNG throw exception, query với statusEnum = null.
- [ ] [Edge Case] - `getAllWithFilters_NullStatus_ShouldQueryWithNullStatus`: status = null, query với statusEnum = null.

---

# PHẦN 3: BỔ TRỢ TIỆN ÍCH

**Module:** 10 — statistics-service, notification-service, upload-service

---

## 3a. NotificationService

**Class:** `NotificationService`
**Service:** `notification-service`

### 3a.1. Bảng Scope of Testing

| File / Class / Function | Test / Không Test | Lý do chi tiết |
|---|---|---|
| `NotificationService` | Test | Chứa Business logic: tạo thông báo, đánh dấu đã đọc (có guard), tính toán recipient (resolveRecipients có nhiều nhánh). |
| `NotificationService.createNotification()` | Test | Tạo entity, set sentAt, deliveryStatus="SENT", gọi pushNotification (mock SocketIO). |
| `NotificationService.markAsRead()` | Test | Guard: chỉ update khi chưa đọc (isRead=false); gọi socketIOBroadcastService (mock). |
| `NotificationService.markAllAsRead()` | Test | Gọi repository.markAllAsReadByRecipientId(); updatedCount > 0 thì broadcast (mock socketIO). |
| `NotificationService.getAllNotificationsWithFilters()` | Test | 3 nhánh: có recipientId, có status, không có gì -> findAll(). |
| `NotificationService.getNotificationStats()` | Test | 2 nhánh: recipientId != null và recipientId == null, trả khác nhau. |
| `NotificationService.processNotificationEvent()` | Test | Gọi resolveRecipients, sau đó gọi createNotification cho từng recipient. |
| `NotificationService.createBulkNotificationsByConditions()` | Test | 4 mức ưu tiên recipient: allEmployees, filter, recipientIds, recipientId. |
| `NotificationRepository` | Không Test | Spring Data JPA, không có logic riêng. |
| `UserService` (HTTP client) | Không Test | Gọi HTTP đến user-service — bắt buộc Mock. |
| `SocketIOBroadcastService` | Không Test | External infrastructure (WebSocket) — bắt buộc Mock. |
| `Notification` (Entity) | Không Test | JPA Entity thuần túy. |
| `NotificationEvent`, `NotificationPayload` | Không Test | DTO/event object, không có logic. |

### 3a.2. Danh sách Kịch bản (Test Cases)

#### Hàm `createNotification(Long recipientId, String title, String message)` — Độ phức tạp: 1 nhánh

- [ ] [Happy Path] - `createNotification_ValidInputs_ShouldSaveWithCorrectFieldsAndBroadcast`: notification được lưu với deliveryStatus="SENT", sentAt != null. CheckDB: repository.save() được gọi. socketIOBroadcastService.pushNotification() được gọi.

#### Hàm `markAsRead(Long notificationId)` — Độ phức tạp: 2 nhánh (isRead guard)

- [ ] [Happy Path] - `markAsRead_UnreadNotification_ShouldSetReadTrueAndBroadcastUnreadCount`: Notification chưa đọc (isRead=false), kiểm tra isRead=true, readAt != null, socketIOBroadcastService.publishUnreadCount() được gọi.
- [ ] [Edge Case] - `markAsRead_AlreadyReadNotification_ShouldNotSaveNorBroadcast`: Notification đã đọc (isRead=true), repository.save() và publishUnreadCount() KHÔNG được gọi.
- [ ] [Exception] - `markAsRead_NonExistentNotification_ShouldThrowNotificationNotFoundException`: ID không tồn tại, throw NotificationNotFoundException.

#### Hàm `markAllAsRead(Long recipientId)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `markAllAsRead_WithUnreadNotifications_ShouldReturnCountAndBroadcastZero`: repository trả về count > 0, socketIOBroadcastService.publishUnreadCount(recipientId, 0L) được gọi.
- [ ] [Edge Case] - `markAllAsRead_WithNoUnreadNotifications_ShouldReturnZeroAndNotBroadcast`: repository trả về 0, broadcast KHÔNG được gọi.

#### Hàm `getAllNotificationsWithFilters(...)` — Độ phức tạp: 3 nhánh

- [ ] [Happy Path] - `getAllNotificationsWithFilters_WithRecipientId_ShouldQueryByRecipientId`: recipientId != null, gọi repository.findByRecipientId(recipientId, pageable).
- [ ] [Happy Path] - `getAllNotificationsWithFilters_WithStatus_ShouldQueryByStatus`: recipientId = null + status != null, gọi repository.findByDeliveryStatus().
- [ ] [Happy Path] - `getAllNotificationsWithFilters_WithNullBoth_ShouldQueryAll`: cả hai null, gọi repository.findAll().
- [ ] [Happy Path] - `getAllNotificationsWithFilters_ShouldReturnCorrectPaginationMetadata`: Kiểm tra meta.page, meta.pageSize, meta.total, meta.pages set đúng.

#### Hàm `getNotificationStats(Long recipientId)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `getNotificationStats_WithRecipientId_ShouldReturnUnreadCountForRecipient`: gọi countByRecipientIdAndIsReadFalse(recipientId), KHÔNG gọi countByIsReadFalse() toàn hệ thống.
- [ ] [Happy Path] - `getNotificationStats_WithNullRecipientId_ShouldReturnGlobalUnreadCount`: gọi countByIsReadFalse() toàn hệ thống.

#### Hàm `processNotificationEvent(NotificationEvent event)` — Độ phức tạp: 2 nhánh

- [ ] [Happy Path] - `processNotificationEvent_WithSingleRecipient_ShouldCreateOneNotification`: event có recipientId = 1L, createNotification được gọi đúng 1 lần.
- [ ] [Edge Case] - `processNotificationEvent_WithEmptyRecipients_ShouldNotCreateAnyNotification`: Không có recipient, repository.save() KHÔNG được gọi.

#### Hàm `createBulkNotificationsByConditions(BulkNotificationRequest request)` — Độ phức tạp: 4 nhánh ưu tiên

- [ ] [Happy Path] - `createBulkNotifications_IncludeAllEmployees_ShouldCallGetAllEmployeeIdsAndCreateForEach`: includeAllEmployees = true, userService.getAllEmployeeIds() được gọi.
- [ ] [Happy Path] - `createBulkNotifications_WithSpecificRecipientIds_ShouldCreateForEachId`: recipientIds = [1L, 2L], createNotification được gọi 2 lần, trả về số lượng = 2.
- [ ] [Edge Case] - `createBulkNotifications_EmptyResolvedRecipients_ShouldReturnZeroAndNotSave`: không có recipient nào, trả về 0, repository.save() KHÔNG được gọi.

---

## 3b. CloudinaryService

**Class:** `CloudinaryService`
**Service:** `upload-service`

### 3b.1. Bảng Scope of Testing

| File / Class / Function | Test / Không Test | Lý do chi tiết |
|---|---|---|
| `CloudinaryService` | Test | Chứa logic xử lý file upload và exception handling. |
| `CloudinaryService.upload()` | Test | Truyền file.getBytes() vào cloudinary.uploader().upload(). Mock Cloudinary. |
| `CloudinaryService.uploadFile()` | Test | Lấy secure_url từ kết quả, xử lý IOException -> RuntimeException. |
| `Cloudinary` (third-party SDK) | Không Test | External infrastructure — bắt buộc Mock. |
| `UploadController` | Không Test | Controller layer, không có business logic. Hàm thuộc integration test. |
| `CloudinaryConfig` | Không Test | Configuration class, không có logic. |

### 3b.2. Danh sách Kịch bản (Test Cases)

#### Hàm `upload(MultipartFile file)` — Độ phức tạp: 1 nhánh

- [ ] [Happy Path] - `upload_ValidFile_ShouldCallCloudinaryUploaderAndReturnResultMap`: Mock cloudinary.uploader().upload() trả về map, kiểm tra kết quả được trả về đúng.

#### Hàm `uploadFile(MultipartFile file)` — Độ phức tạp: 2 nhánh (IOException handling)

- [ ] [Happy Path] - `uploadFile_ValidFile_ShouldReturnSecureUrl`: Mock cloudinary trả về map có key "secure_url", hàm trả về URL chuẩn.
- [ ] [Exception] - `uploadFile_CloudinaryThrowsIOException_ShouldThrowRuntimeException`: cloudinary.uploader().upload() throw IOException, kiểm tra RuntimeException với message "Không thể upload file".
- [ ] [Edge Case] - `uploadFile_EmptyFile_ShouldStillCallCloudinaryWithoutThrowingNpe`: file rỗng (0 bytes), vẫn gọi cloudinary và KHÔNG throw NullPointerException.

---

## 3c. StatisticsService

**Class:** `StatisticsService`
**Service:** `statistics-service`

### 3c.1. Bảng Scope of Testing

| File / Class / Function | Test / Không Test | Lý do chi tiết |
|---|---|---|
| `StatisticsService` | Test | Chứa Business logic tính toán: filter theo date range, getDepartmentIdForStatistics() theo role, formatSalary() với nhiều trường hợp. |
| `StatisticsService.getSummaryStatistics()` | Test | Default date logic (null -> today / today+7), đếm applications/hired/rejected/interviews. Mock clients. |
| `StatisticsService.getJobOpenings()` | Test | Map từ PaginationDTO sang JobOpeningDTO: tính workLocation (isRemote, Hybrid, On-site), formatSalary. Mock jobServiceClient. |
| `StatisticsService.getUpcomingSchedules()` | Test | Parse participants để lấy candidateName (participantType = "CANDIDATE"), parse datetime. Mock schedule client. |
| `StatisticsService.getDepartmentIdForStatistics()` | Test (gián tiếp) | Phân nhánh theo role: CEO/ADMIN -> null, STAFF-HR -> null, STAFF khác -> departmentId, MANAGER-HR -> null. Mock SecurityUtil. |
| `StatisticsService.formatSalary()` | Test (gián tiếp qua getJobOpenings) | 4 trường hợp: null/null, chỉ min, chỉ max, cả hai. |
| `StatisticsService.filterApplicationsByDateRange()` | Test (gián tiếp qua getSummary) | Filter đúng date range, bao gồm app nằm trong, ngoài khoảng, và sai định dạng ngày. |
| `CandidateServiceClient`, `JobServiceClient`, `ScheduleServiceClient` | Không Test | HTTP client gọi sang service khác — bắt buộc Mock. |
| `SecurityUtil` | Không Test (MockedStatic) | Utility đọc JWT từ Security context — Mock bằng MockedStatic để control role trong test. |
| `SummaryStatisticsDTO`, `JobOpeningDTO`, `UpcomingScheduleDTO` | Không Test | DTO Lombok, không có logic. |

### 3c.2. Danh sách Kịch bản (Test Cases)

#### Hàm `getSummaryStatistics(String token, LocalDate startDate, LocalDate endDate)` — Độ phức tạp: 2 nhánh (date default) + filter logic

- [ ] [Happy Path] - `getSummaryStatistics_WithExplicitDates_ShouldCountOnlyApplicationsInRange`: Chỉ đếm applications có appliedDate trong [startDate, endDate], bỏ qua ngoài khoảng.
- [ ] [Happy Path] - `getSummaryStatistics_WithNullDates_ShouldDefaultToTodayAndPlusSeven`: startDate = null, endDate = null, kiểm tra period = (today, today+7).
- [ ] [Happy Path] - `getSummaryStatistics_ShouldCountHiredAndRejectedSeparately`: applications có mix trạng thái, kiểm tra hired và rejected đếm đúng riêng biệt.
- [ ] [Edge Case] - `getSummaryStatistics_WithNoData_ShouldReturnAllZeros`: candidateClient trả về danh sách rỗng, tất cả counts = 0.

#### Hàm `getJobOpenings(String token, int page, int limit)` — Độ phức tạp: 3 nhánh (workLocation)

- [ ] [Happy Path] - `getJobOpenings_RemotePosition_ShouldSetWorkLocationToRemote`: isRemote = true, workLocation = "Remote".
- [ ] [Happy Path] - `getJobOpenings_HybridLocationPosition_ShouldSetWorkLocationToHybrid`: isRemote = false + location chứa "Hybrid", workLocation = "Hybrid".
- [ ] [Happy Path] - `getJobOpenings_OnsitePosition_ShouldSetWorkLocationToOnSite`: isRemote = false + location không chứa "Hybrid", workLocation = "On-site".
- [ ] [Happy Path] - `getJobOpenings_WithBothSalaries_ShouldFormatSalaryAsRange`: salaryMin và salaryMax đều có giá trị, salaryDisplay = "X - Y triệu".
- [ ] [Edge Case] - `getJobOpenings_NullResultFromJobService_ShouldReturnEmptyList`: jobServiceClient trả về null, trả về danh sách rỗng (không NPE).
- [ ] [Edge Case] - `getJobOpenings_NullBothSalaries_ShouldReturnEmptySalaryDisplay`: cả hai salary = null, salaryDisplay = "".

#### Hàm `getUpcomingSchedules(String token, int limit)` — Độ phức tạp: 2 nhánh (parse participant)

- [ ] [Happy Path] - `getUpcomingSchedules_WithCandidateParticipant_ShouldExtractCandidateName`: participants có participantType="CANDIDATE", candidateName được lấy từ "name".
- [ ] [Happy Path] - `getUpcomingSchedules_WithNoParticipants_ShouldReturnEmptyCandidateName`: participants rỗng hoặc không có CANDIDATE, candidateName = "".
- [ ] [Edge Case] - `getUpcomingSchedules_ShouldRespectLimitParameter`: client trả về 10 items nhưng limit = 3, chỉ trả về 3 items.
- [ ] [Negative] - `getUpcomingSchedules_InvalidDateTimeFormat_ShouldReturnEmptyTimeAndDate`: startTime sai định dạng, time và date trả về "" (không throw exception).

#### Hàm `getDepartmentIdForStatistics()` — kiểm tra gián tiếp qua getSummaryStatistics (MockedStatic)

- [ ] [Happy Path] - `getSummaryStatistics_CeoRole_ShouldPassNullDepartmentIdToClient`: Role = "CEO", candidateServiceClient được gọi với departmentId = null.
- [ ] [Happy Path] - `getSummaryStatistics_StaffHrRole_ShouldPassNullDepartmentIdToClient`: Role = "STAFF", departmentCode = "HR", được gọi với departmentId = null.
- [ ] [Happy Path] - `getSummaryStatistics_ManagerNonHrRole_ShouldPassOwnDepartmentIdToClient`: Role = "MANAGER", departmentCode = "IT", departmentId = 5L, được gọi với departmentId = 5L.

---

## Tổng Kết

| Phần | Service | Class | Số Test Cases |
|---|---|---|---|
| 1 — Vị trí tuyển dụng | job-service | `JobPositionService` | 27 |
| 2 — Offer | job-service | `OfferService` | 21 |
| 3a — Thông báo | notification-service | `NotificationService` | 15 |
| 3b — Upload file | upload-service | `CloudinaryService` | 4 |
| 3c — Thống kê | statistics-service | `StatisticsService` | 13 |
| **Tổng** | **4 services** | **5 classes** | **~80 test cases** |

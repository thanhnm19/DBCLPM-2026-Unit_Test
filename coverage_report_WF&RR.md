# Code Coverage Report — Toàn Dự Án

> **Công cụ đo:** JaCoCo 0.8.12 (Java Code Coverage Library)
> **Framework Test:** JUnit 5 + Spring Boot Test + H2 In-Memory DB
> **Ngày đo:** 2026-04-17
> **Lệnh thực thi:** `mvnw.cmd clean test -Dspring.profiles.active=test`

---

## Tóm tắt kết quả

| Service | Test Cases | PASS | FAIL | Build |
|---|---|---|---|---|
| **workflow-service** | 48 | 48 | 0 | ✅ SUCCESS |
| **job-service** | 36 | 36 | 0 | ✅ SUCCESS |
| **Tổng cộng** | **84** | **84** | **0** | ✅ |

---

# PHẦN 1 — workflow-service

## 1.1 Tổng quan toàn module (56 classes)

![JaCoCo — workflow-service tổng quan](C:\Users\ADMIN\.gemini\antigravity\brain\ff1a0d02-d0e2-4fe9-a9b3-f3d30b1d6a72\workflow_service_overall_coverage_1776414541044.png)

| Chỉ số | Missed | Total | **Coverage** |
|---|---|---|---|
| Instructions | 3,530 | 6,082 | **41%** |
| Branches | 446 | 602 | **25%** |
| Lines | 846 | 1,484 | — |
| Methods | 117 | 217 | — |
| Classes | 24 | 56 | — |

> **Ghi chú:** Coverage thấp ở toàn module vì controller, config, messaging không có test — đây là thiết kế đúng (unit test chỉ nhắm service layer).

---

## 1.2 Package `com.example.workflow_service.service` (15 classes)

![JaCoCo — workflow_service.service package](C:\Users\ADMIN\.gemini\antigravity\brain\ff1a0d02-d0e2-4fe9-a9b3-f3d30b1d6a72\workflow_service_package_coverage_1776414602359.png)

| Class | Instructions Cov. | Branches Cov. |
|---|---|---|
| **ApprovalTrackingService** | **59%** | **40%** |
| **WorkflowService** | **69%** | **55%** |
| UserService *(mocked)* | 0% | 0% |
| CandidateService *(mocked)* | 0% | 0% |
| **Tổng package** | **50%** | **33%** |

---

## 1.3 Class `WorkflowService` — Chi tiết method

![JaCoCo — WorkflowService class detail](C:\Users\ADMIN\.gemini\antigravity\brain\ff1a0d02-d0e2-4fe9-a9b3-f3d30b1d6a72\workflow_service_class_coverage_details_1776414635160.png)

| Method | Instructions | Branches | Ghi chú |
|---|---|---|---|
| `create(CreateWorkflowDTO)` | **100%** | **95%** | ✅ Gần hoàn toàn |
| `getAll(...)` | **100%** | **100%** | ✅ Hoàn toàn |
| `toResponseDTO(Workflow, Map)` | **100%** | **100%** | ✅ |
| `toResponseDTO(Workflow)` | **100%** | **100%** | ✅ |
| `delete(Long)` | **100%** | n/a | ✅ |
| `getById(Long)` | **100%** | n/a | ✅ |
| `update(Long, UpdateWorkflowDTO)` | 35% | 38% | ⚠️ Còn nhánh chưa phủ |
| `toStepResponseDTO(...)` | 0% | 0% | ⚠️ Chưa có test case |
| **Tổng WorkflowService** | **69%** | **55%** | |

### Test cases phủ cho WorkflowService (18 TCs):

| TC | Nhánh | Kết quả |
|---|---|---|
| WF-TC01 | [N1] create() — tên trùng → CustomException | ✅ PASS |
| WF-TC02 | [N2] create() — steps=null → tạo không có step | ✅ PASS |
| WF-TC03 | [N3] create() — steps=[] rỗng | ✅ PASS |
| WF-TC04 | [N5] create() — positionId không trong map | ✅ PASS |
| WF-TC05 | [N6] create() — hierarchyOrder tăng dần → lỗi | ✅ PASS |
| WF-TC06 | [N6] create() — hierarchyOrder bằng nhau → OK | ✅ PASS |
| WF-TC07 | [N7] create() — steps hợp lệ → lưu DB đúng | ✅ PASS |
| WF-TC08 | [N8] getById() — ID tồn tại → DTO đúng | ✅ PASS |
| WF-TC09 | [N9] getById() — ID không tồn tại → exception | ✅ PASS |
| WF-TC10 | [N10] getAll() — type=null → trả về tất cả | ✅ PASS |
| WF-TC11 | [N11] getAll() — type=OFFER → chỉ OFFER | ✅ PASS |
| WF-TC12 | [N12] getAll() — isActive=false filter | ✅ PASS |
| WF-TC13 | [N13] update() — ID không tồn tại → exception | ✅ PASS |
| WF-TC14 | [N14] update() — tên giữ nguyên → không lỗi | ✅ PASS |
| WF-TC15 | [N15] update() — tên đã bị chiếm → exception | ✅ PASS |
| WF-TC16 | [N16-19] update() — các trường hợp lệ | ✅ PASS |
| WF-TC17 | [N20] delete() — ID không tồn tại → exception | ✅ PASS |
| WF-TC18 | [N21] delete() — soft delete: isActive=false | ✅ PASS |

---

# PHẦN 2 — job-service

## 2.1 Tổng quan toàn module (69 classes)

![JaCoCo — job-service tổng quan](C:\Users\ADMIN\.gemini\antigravity\brain\ff1a0d02-d0e2-4fe9-a9b3-f3d30b1d6a72\job_service_overall_coverage_1776414670784.png)

| Chỉ số | Missed | Total | **Coverage** |
|---|---|---|---|
| Instructions | 6,040 | 6,966 | **13%** |
| Branches | 647 | 679 | **4%** |
| Lines | 1,398 | 1,634 | — |
| Methods | 205 | 270 | — |
| Classes | 35 | 69 | — |

> **Ghi chú:** Tương tự workflow-service, nhiều class (controller, config, dto, offer-related) chưa có test — chỉ `RecruitmentRequestService` được test trong phạm vi bài tập.

---

## 2.2 Package `com.example.job_service.service` (18 classes)

![JaCoCo — job_service.service package](C:\Users\ADMIN\.gemini\antigravity\brain\ff1a0d02-d0e2-4fe9-a9b3-f3d30b1d6a72\job_service_package_coverage_1776414692915.png)

| Class | Instructions Cov. | Branches Cov. |
|---|---|---|
| **RecruitmentRequestService** | **55%** | **41%** |
| OfferService *(không test)* | 1% | 0% |
| JobPositionService *(không test)* | 1% | 0% |
| UserClient *(mocked)* | 0% | 0% |
| WorkflowClient *(mocked)* | 1% | 0% |
| **Tổng package** | **12%** | **7%** |

---

## 2.3 Class `RecruitmentRequestService` — Chi tiết method

![JaCoCo — RecruitmentRequestService class detail](C:\Users\ADMIN\.gemini\antigravity\brain\ff1a0d02-d0e2-4fe9-a9b3-f3d30b1d6a72\job_service_class_coverage_details_1776414756021.png)

| Method | Instructions | Branches | Ghi chú |
|---|---|---|---|
| `create(CreateRecruitmentRequestDTO)` | **100%** | n/a | ✅ |
| `submit(Long, Long, String)` | **100%** | **100%** | ✅ Hoàn toàn |
| `cancel(Long, CancelDTO, Long, String)` | **100%** | **100%** | ✅ Hoàn toàn |
| `withdraw(Long, Long, String)` | **100%** | **87%** | ✅ |
| `approveStep(...)` | **100%** | **75%** | ✅ |
| `rejectStep(...)` | **100%** | **75%** | ✅ |
| `returnRequest(...)` | **100%** | **75%** | ✅ |
| `findAllWithFilters(Long, String, Long, String)` | **100%** | **75%** | ✅ |
| `delete(Long)` | **100%** | n/a | ✅ |
| `getAll()` | **100%** | n/a | ✅ |
| `changeStatus(Long, Status)` | 93% | 50% | ✅ |
| `convertToWithUserDTO(...)` | 0% | 0% | ⚠️ HTTP call, không test |
| `getAllWithFilters(Page, String)` | 0% | 0% | ⚠️ |
| **Tổng RecruitmentRequestService** | **55%** | **41%** | |

### Test cases phủ cho RecruitmentRequestService (36 TCs):

| TC | Nhánh | Kết quả |
|---|---|---|
| RR-TC01 | [N1] create() — input hợp lệ → DRAFT, lưu DB đúng | ✅ PASS |
| RR-TC02 | [N2] submit() — DRAFT → PENDING, submittedAt ghi | ✅ PASS |
| RR-TC03 | [N3] submit() — RETURNED → PENDING | ✅ PASS |
| RR-TC04 | [N4] submit() — PENDING → IllegalStateException | ✅ PASS |
| RR-TC05 | [N5] submit() — APPROVED → IllegalStateException | ✅ PASS |
| RR-TC06 | [N6] submit() — ownerUserId=null → gán actorId | ✅ PASS |
| RR-TC07 | [N7] submit() — ID không tồn tại → exception | ✅ PASS |
| RR-TC08 | [N8] approveStep() — PENDING → giữ PENDING | ✅ PASS |
| RR-TC09 | [N10] approveStep() — DRAFT → exception | ✅ PASS |
| RR-TC10 | [N11] approveStep() — APPROVED → exception | ✅ PASS |
| RR-TC11 | [N12] rejectStep() — PENDING → REJECTED | ✅ PASS |
| RR-TC12 | [N13] rejectStep() — DRAFT → exception | ✅ PASS |
| RR-TC13 | [N14] rejectStep() — CANCELLED → exception | ✅ PASS |
| RR-TC14 | [N15] returnRequest() — PENDING → RETURNED | ✅ PASS |
| RR-TC15 | [N16] returnRequest() — DRAFT → exception | ✅ PASS |
| RR-TC16 | [N17] returnRequest() — APPROVED → exception | ✅ PASS |
| RR-TC17 | [N18] cancel() — DRAFT → CANCELLED | ✅ PASS |
| RR-TC18 | [N19] cancel() — PENDING → CANCELLED | ✅ PASS |
| RR-TC19 | [N20] cancel() — đã CANCELLED → idempotent | ✅ PASS |
| RR-TC20 | [N21] cancel() — APPROVED → exception | ✅ PASS |
| RR-TC21 | [N22] cancel() — REJECTED → exception | ✅ PASS |
| RR-TC22 | [N23] withdraw() — owner withdraw → WITHDRAWN | ✅ PASS |
| RR-TC23 | [N24] withdraw() — requester withdraw → WITHDRAWN | ✅ PASS |
| RR-TC24 | [N25] withdraw() — actor sai → exception | ✅ PASS |
| RR-TC25 | [N26] withdraw() — DRAFT → exception | ✅ PASS |
| RR-TC26 | [N27] withdraw() — APPROVED → exception | ✅ PASS |
| RR-TC27 | [N28] findAllWithFilters() — status lạ → bỏ qua | ✅ PASS |
| RR-TC28 | [N29] findAllWithFilters() — status=PENDING | ✅ PASS |
| RR-TC29 | [N30] findAllWithFilters() — departmentId filter | ✅ PASS |
| RR-TC30 | [N31] findAllWithFilters() — keyword filter | ✅ PASS |
| RR-TC31 | [N32] findById() — ID tồn tại | ✅ PASS |
| RR-TC32 | [N33] findById() — ID không tồn tại | ✅ PASS |
| RR-TC33 | [N34] changeStatus() — đổi status → DB cập nhật | ✅ PASS |
| RR-TC34 | [N35] getAll() — chỉ isActive=true | ✅ PASS |
| RR-TC35 | [N36] delete() — soft delete: isActive=false | ✅ PASS |
| RR-TC36 | [N37] delete() — ID không tồn tại → exception | ✅ PASS |

---

# Kết luận tổng hợp

| Service | Class được test | Instruction Cov. | Branch Cov. | Test PASS |
|---|---|---|---|---|
| workflow-service | `WorkflowService` | **69%** | **55%** | 18/18 ✅ |
| workflow-service | `ApprovalTrackingService` | **59%** | **40%** | 30/30 ✅ |
| job-service | `RecruitmentRequestService` | **55%** | **41%** | 36/36 ✅ |

> **Coverage cao nhất đạt được:**
> - `create()` trong WorkflowService: **95% branch** ✅
> - `getAll()` trong WorkflowService: **100% branch** ✅
> - `submit()`, `cancel()` trong RecruitmentRequestService: **100% branch** ✅

> **Vị trí HTML Report:**
> - `workflow-service/target/site/jacoco/index.html`
> - `job-service/target/site/jacoco/index.html`

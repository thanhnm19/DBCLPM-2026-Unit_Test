# Tài Liệu Kiểm Thử Hiệu Năng (Performance Testing)
## Hệ Thống: Recruitment Management System
**Công cụ:** Apache JMeter 5.6.3 | **Môi trường:** Localhost (Sample Environment)

---

## 1. Mục Tiêu Kiểm Thử Hiệu Năng

| Mục tiêu | Chi tiết |
|---|---|
| **Tìm ngưỡng chịu đựng** | Xác định số lượng user tối đa hệ thống chịu được trước khi degradation |
| **Đo thông lượng (Throughput)** | Số request/giây hệ thống xử lý được ở mức tải ổn định |
| **Đo thời gian phản hồi** | Response time ở các mức tải 300 / 750 / 2000 users |
| **Phát hiện Deadlock** | Khi 100 người duyệt đồng thời, DB không bị lock |
| **Kiểm tra Kafka** | Event bất đồng bộ không bị nghẽn khi concurrent write cao |

---

## 2. Chiến Lược Phân Tầng Tải

```
300 users  → Baseline (hệ thống phải ổn định)
750 users  → Load Test (tiệm cận ngưỡng)
2000 users → Stress Test (tìm điểm gãy)
Kết luận:  → Nếu 750 ổn, 2000 không ổn → Ngưỡng ~700-750 users
```

---

## 3. Bảng Test Case Kiểm Thử Hiệu Năng

### PT-TC01 – Test Thông Lượng Đọc Vị Trí Tuyển Dụng

| Mã THHKT | Tính năng | Mục đích kiểm thử | Các bước thực hiện | Test Data | Kết quả mong đợi |
|---|---|---|---|---|---|
| **PT-TC01-P1** | GET /job-positions/published | Kiểm tra hệ thống chịu được **300 users** đồng thời, API public không cần đăng nhập | 1. Thread Group: 300 threads, ramp=60s, loop=5<br>2. Gaussian Timer: mean=2000ms, dev=1000ms<br>3. GET request, không có Authorization<br>4. Assert HTTP 200 + body chứa "PUBLISHED"<br>5. Duration Assertion < 200ms<br>6. Xem Summary Report | 300 ứng viên vãng lai, không token, Page=1&limit=10, ≥500 PUBLISHED jobs trong DB | HTTP 200 ✅ Response Time < 200ms ✅ Error Rate < 1% ✅ Throughput ≥ 150 req/s ✅ |
| **PT-TC01-P2** | GET /job-positions/published | Kiểm tra ngưỡng với **750 users** – phát hiện dấu hiệu degradation | 1. Thread Group: 750 threads, ramp=90s, loop=5<br>2. Gaussian Timer: mean=2000ms, dev=1000ms<br>3. GET request, không Authorization<br>4. Ghi nhận Response Time, Error Rate, Throughput<br>5. So sánh với Phase 1 | 750 ứng viên vãng lai, không token, mock data 500+ records | HTTP 200 ✅ Response Time < 500ms Error Rate < 5% Ghi nhận điểm bắt đầu degradation |
| **PT-TC01-P3** | GET /job-positions/published | **Stress test 2000 users** – tìm điểm gãy hệ thống, kết luận ngưỡng chịu đựng | 1. Thread Group: 2000 threads, ramp=120s, loop=3<br>2. Gaussian Timer: mean=2000ms, dev=1000ms<br>3. Gửi GET đồng thời 2000 threads<br>4. Theo dõi Error Rate tăng, Response Time vọt<br>5. Chụp Aggregate Report khi quá tải<br>6. Kết luận ngưỡng từ P1, P2, P3 | 2000 ứng viên vãng lai, không token, mock data 1000+ records | Kỳ vọng Error Rate > 10% tại 2000 users ❌ Kết luận: **ngưỡng chịu đựng ~700 users** Chụp ảnh bằng chứng ✅ |

---

### PT-TC02 – Test Chịu Tải Luồng Phê Duyệt & Kafka

| Mã THHKT | Tính năng | Mục đích kiểm thử | Các bước thực hiện | Test Data | Kết quả mong đợi |
|---|---|---|---|---|---|
| **PT-TC02-P0** | POST /auth/login | Lấy JWT Token cho approver trước khi test | 1. SetupThreadGroup: 1 thread, 1 loop<br>2. POST `/api/v1/auth/login`<br>3. JSON Extractor: `$.data.accessToken` → `jwt_token`<br>4. BeanShell: lưu vào JMeter property `GLOBAL_JWT_TOKEN` | username: `admin`, password: `admin123` | JWT token extracted ✅ Token lưu vào property ✅ |
| **PT-TC02-P1** | POST /recruitment-requests/approve/{id} | Warm-up **50 người duyệt** đồng thời – kiểm tra concurrent write cơ bản | 1. Thread Group: 50 threads, ramp=10s, loop=3<br>2. CSV Data Set: `approval_data.csv` (request_id, notes), shareMode=thread<br>3. HTTP Header: `Authorization: Bearer ${jwt_token}`<br>4. POST `/approve/${request_id}`, body JSON<br>5. Assert HTTP 200<br>6. Assert body KHÔNG chứa "Deadlock" | `approval_data.csv`: request_id 1-50 trạng thái PENDING | HTTP 200 ✅ Không có Deadlock ✅ Response Time < 5000ms ✅ Error Rate = 0% ✅ |
| **PT-TC02-P2** | POST /recruitment-requests/approve/{id} | **NFR Target**: **100 người duyệt đồng thời** – chứng minh Kafka không nghẽn, DB không Deadlock | 1. Thread Group: **100 threads**, ramp=5s, loop=1<br>2. CSV Data Set: `approval_data.csv` – mỗi thread 1 request_id riêng<br>3. HTTP Header: `Authorization: Bearer ${__P(GLOBAL_JWT_TOKEN,)}`<br>4. POST `/approve/${request_id}`<br>5. Assert HTTP 200<br>6. Assert KHÔNG có "Deadlock" / "CannotAcquireLockException"<br>7. Assert Response < 5000ms<br>8. Sau test: kiểm tra Kafka consumer lag = 0 | `approval_data.csv`: 100 request_id (1-100) PENDING `jwt_tokens.csv`: 100 approver accounts | HTTP 200 cho 100 requests ✅ **Không Deadlock** ✅ **Kafka consumer lag = 0** ✅ Response < 5000ms ✅ Error Rate < 1% ✅ DB: 100 records → APPROVED ✅ |

---

## 4. Cấu Hình JMeter Chi Tiết

### PT-TC01 – Các thành phần

| Thành phần | Cấu hình | Mục đích |
|---|---|---|
| Thread Group | 2000 threads, ramp 120s, loop 3 | Giả lập 2000 ứng viên vãng lai |
| Gaussian Random Timer | mean=2000ms, deviation=1000ms | Think time 1–3s giữa các lần click |
| HTTP Request | GET `.../published?page=1&limit=10` | API public, không token |
| Response Assertion | HTTP 200 + body contains "PUBLISHED" | Validate response |
| Duration Assertion | < 200ms | NFR validation |
| Aggregate Report | Save → `reports/PT-TC01_aggregate.csv` | Phân tích sau test |

### PT-TC02 – Các thành phần

| Thành phần | Cấu hình | Mục đích |
|---|---|---|
| SetupThreadGroup | 1 thread – Login | Lấy JWT Bearer token |
| CSV Data Set Config 1 | `jwt_tokens.csv`, shareMode=thread | Mỗi thread dùng tài khoản riêng |
| CSV Data Set Config 2 | `approval_data.csv`, shareMode=thread | Mỗi thread duyệt request_id khác nhau |
| HTTP Header Manager | `Authorization: Bearer ${__P(GLOBAL_JWT_TOKEN,)}` | REQUIRED – API cần auth |
| Thread Group | 100 threads, ramp=5s, loop=1 | 100 người duyệt gần đồng thời |
| Response Assertion 1 | HTTP 200 | Approval thành công |
| Response Assertion 2 | KHÔNG chứa "Deadlock", "Lock wait timeout" | Kiểm tra DB integrity |
| Duration Assertion | < 5000ms | Kafka async vẫn phải nhanh |

---

## 5. Mock Data Cần Chuẩn Bị

```sql
-- PT-TC01: 500+ job positions PUBLISHED
INSERT INTO job_positions (title, status, department_id, description, is_active)
SELECT CONCAT('Vi tri ', seq), 'PUBLISHED', (seq % 10)+1, 
       CONCAT('Mo ta ', seq), true
FROM generate_series(1, 500) seq;

-- PT-TC02: 100 recruitment requests PENDING
INSERT INTO recruitment_requests (title, status, workflow_id, department_id, is_active)
SELECT CONCAT('Yeu cau tuyen dung ', seq), 'PENDING', 1, (seq % 5)+1, true
FROM generate_series(1, 100) seq;
```

---

## 6. Lệnh Thực Thi (Headless Mode)

```bash
# PT-TC01: Job Positions Throughput Test
jmeter -n -t "Performance Test/jmeter/scripts/PT-TC01_JobPositions_Throughput.jmx" \
       -l "Performance Test/jmeter/reports/PT-TC01_results.jtl" \
       -e -o "Performance Test/jmeter/reports/PT-TC01_html_report/"

# PT-TC02: Approval Flow Load Test
jmeter -n -t "Performance Test/jmeter/scripts/PT-TC02_ApprovalFlow_LoadTest.jmx" \
       -l "Performance Test/jmeter/reports/PT-TC02_results.jtl" \
       -e -o "Performance Test/jmeter/reports/PT-TC02_html_report/"

# Kiểm tra Kafka consumer lag sau PT-TC02
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group recruitment-approval-consumer-group
```

---

## 7. Tiêu Chí Kết Luận Ngưỡng Chịu Đựng

| Mức Load | Response Time | Error Rate | Kết luận |
|---|---|---|---|
| 300 users | < 200ms | < 1% | ✅ Hệ thống ổn định |
| 750 users | < 500ms | < 5% | ⚠️ Có dấu hiệu tăng |
| 2000 users | > 1000ms | > 10% | ❌ Hệ thống quá tải |
| **Kết luận** | — | — | **Ngưỡng chịu đựng: ~700 users** |

> 📸 **Bắt buộc chụp màn hình** Aggregate Report tại mỗi phase làm bằng chứng báo cáo

---

## 8. Cấu Trúc Thư Mục Deliverables

```
Performance Test/
├── Performance_TestCases.md              ← Tài liệu này
├── jmeter/
│   ├── scripts/
│   │   ├── PT-TC01_JobPositions_Throughput.jmx
│   │   └── PT-TC02_ApprovalFlow_LoadTest.jmx
│   ├── csv/
│   │   ├── approval_data.csv             ← 100 request_id PENDING
│   │   └── jwt_tokens.csv                ← 100 approver credentials
│   └── reports/
│       ├── PT-TC01_html_report/          ← HTML dashboard (auto-gen)
│       └── PT-TC02_html_report/          ← HTML dashboard (auto-gen)
└── screenshots/                          ← Chụp ảnh minh chứng
```

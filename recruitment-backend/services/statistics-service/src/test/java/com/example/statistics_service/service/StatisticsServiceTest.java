package com.example.statistics_service.service;

import com.example.statistics_service.dto.PaginationDTO;
import com.example.statistics_service.dto.statistics.JobOpeningDTO;
import com.example.statistics_service.dto.statistics.SummaryStatisticsDTO;
import com.example.statistics_service.dto.statistics.UpcomingScheduleDTO;
import com.example.statistics_service.service.client.CandidateServiceClient;
import com.example.statistics_service.service.client.JobServiceClient;
import com.example.statistics_service.service.client.ScheduleServiceClient;
import com.example.statistics_service.utils.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho StatisticsService - Module 10 (Phần 3c): Thống kê.
 *
 * Chiến lược:
 * - Mock CandidateServiceClient, JobServiceClient, ScheduleServiceClient.
 * - Sử dụng MockedStatic để mock SecurityUtil.extractUserRole() (phương thức static).
 * - Tất cả kết quả từ client được mô phỏng bằng ObjectMapper.
 * - Minh chứng (CheckDB): Không có DB thành phần. Kiểm tra bằng verify() các client HTTP.
 * - Dọn dẹp (Rollback): Không có DB thật. Mockito tự động reset sau mỗi test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsService Unit Tests")
class StatisticsServiceTest {

    // -----------------------------------------------------------------------
    // Mock dependencies (HTTP clients)
    // -----------------------------------------------------------------------

    @Mock
    private JobServiceClient jobServiceClient;

    @Mock
    private CandidateServiceClient candidateServiceClient;

    @Mock
    private ScheduleServiceClient communicationServiceClient;

    // Service đang được kiểm tra (System Under Test)
    @InjectMocks
    private StatisticsService statisticsService;

    // Helper để tạo JsonNode trong test
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------------------
    // Helper: Tạo JsonNode ứng viên
    // -----------------------------------------------------------------------

    private JsonNode buildApplicationNode(String appliedDate, String status) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("appliedDate", appliedDate);
        node.put("status", status);
        return node;
    }

    // -----------------------------------------------------------------------
    // Helper: Tạo JsonNode lịch phỏng vấn
    // -----------------------------------------------------------------------

    private JsonNode buildScheduleNode(String startTime) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("startTime", startTime);
        node.put("title", "Interview");
        return node;
    }

    // -----------------------------------------------------------------------
    // Helper: Tạo JsonNode vị trí tuyển dụng (JobPosition)
    // -----------------------------------------------------------------------

    private JsonNode buildJobPositionNode(boolean isRemote, String location, String salaryMin, String salaryMax) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Developer");
        node.put("isRemote", isRemote);
        node.put("location", location != null ? location : "");
        node.put("employmentType", "Full-time");
        node.put("applicationCount", 5);
        if (salaryMin != null) node.put("salaryMin", salaryMin);
        else node.putNull("salaryMin");
        if (salaryMax != null) node.put("salaryMax", salaryMax);
        else node.putNull("salaryMax");
        return node;
    }

    // =======================================================================
    // PHẦN 1: Hàm getSummaryStatistics()
    // =======================================================================

    // Test Case ID: UTIL-ST01
    // Mục tiêu: Chỉ đếm ứng viên trong [startDate, endDate], bỏ qua ngoài khoảng
    @Test
    @DisplayName("UTIL-ST01: getSummaryStatistics - Chỉ đếm applications trong khoảng ngày")
    void getSummaryStatistics_WithExplicitDates_ShouldCountOnlyApplicationsInRange() {
        // Chuẩn bị: 3 ứng viên: 2 trong khoảng, 1 ngoài khoảng
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);

        List<JsonNode> applications = Arrays.asList(
                buildApplicationNode("2026-04-10", "PENDING"),  // Trong khoảng
                buildApplicationNode("2026-04-20", "HIRED"),    // Trong khoảng
                buildApplicationNode("2026-03-15", "PENDING")   // Ngoài khoảng (tháng 3)
        );

        // Mock SecurityUtil để trả về role CEO (xem tất cả phòng ban)
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(applications);
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", start, end);

            // Kiểm tra: Chỉ 2 ứng viên trong khoảng được đếm
            assertThat(result.getApplications()).isEqualTo(2L);
            // 1 người được HIRED trong khoảng
            assertThat(result.getHired()).isEqualTo(1L);
        }
    }

    // Test Case ID: UTIL-ST02
    // Mục tiêu: startDate=null, endDate=null -> phải sử dụng ngày hôm nay và hôm nay+7
    @Test
    @DisplayName("UTIL-ST02: getSummaryStatistics - startDate/endDate null, phải dùng today và today+7")
    void getSummaryStatistics_WithNullDates_ShouldDefaultToTodayAndPlusSeven() {
        // Chuẩn bị
        LocalDate today = LocalDate.now();

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi: Truyền null cho cả hai ngày
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", null, null);

            // Kiểm tra: Kết quả hợp lệ (không throw exception khi date null)
            assertThat(result).isNotNull();
            assertThat(result.getApplications()).isEqualTo(0L);

            // Minh chứng: candidateServiceClient được gọi với ngày đúng định dạng ISO
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(),
                    isNull(),
                    eq(today.toString()),               // periodStart = today
                    eq(today.plusDays(7).toString()),   // periodEnd = today + 7
                    any(),
                    any());
        }
    }

    // Test Case ID: UTIL-ST03
    // Mục tiêu: Ứng viên có mix trạng thái -> hired và rejected được đếm riêng biệt
    @Test
    @DisplayName("UTIL-ST03: getSummaryStatistics - Mix trạng thái, hired và rejected đếm riêng")
    void getSummaryStatistics_ShouldCountHiredAndRejectedSeparately() {
        // Chuẩn bị: 1 HIRED, 2 REJECTED, 1 PENDING trong khoảng
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        List<JsonNode> applications = Arrays.asList(
                buildApplicationNode("2026-03-01", "HIRED"),
                buildApplicationNode("2026-04-01", "REJECTED"),
                buildApplicationNode("2026-05-01", "REJECTED"),
                buildApplicationNode("2026-06-01", "PENDING")
        );

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(applications);
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", start, end);

            // Kiểm tra: chức năng đếm riêng từng số
            assertThat(result.getHired()).isEqualTo(1L);
            assertThat(result.getRejected()).isEqualTo(2L);
            assertThat(result.getApplications()).isEqualTo(4L);
        }
    }

    // Test Case ID: UTIL-ST04
    // Mục tiêu: Không có dữ liệu -> tất cả count = 0
    @Test
    @DisplayName("UTIL-ST04: getSummaryStatistics - Không có data, trả về tất cả số 0")
    void getSummaryStatistics_WithNoData_ShouldReturnAllZeros() {
        // Chuẩn bị: Tất cả client trả về danh sách rỗng
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics(
                    "token", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

            // Kiểm tra: Tất cả số lượng phải là 0
            assertThat(result.getApplications()).isEqualTo(0L);
            assertThat(result.getHired()).isEqualTo(0L);
            assertThat(result.getRejected()).isEqualTo(0L);
            assertThat(result.getInterviews()).isEqualTo(0L);
        }
    }

    // =======================================================================
    // PHẦN 2: Hàm getJobOpenings()
    // =======================================================================

    // Test Case ID: UTIL-ST05
    // Mục tiêu: isRemote=true -> workLocation phải là "Remote"
    @Test
    @DisplayName("UTIL-ST05: getJobOpenings - isRemote=true, workLocation phải là 'Remote'")
    void getJobOpenings_RemotePosition_ShouldSetWorkLocationToRemote() {
        // Chuẩn bị: Job Position với isRemote=true
        JsonNode remotePosition = buildJobPositionNode(true, null, "20000000", "30000000");

        PaginationDTO mockPagination = new PaginationDTO();
        mockPagination.setResult(Arrays.asList(remotePosition));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPagination);

            // Thực thi
            List<JobOpeningDTO> result = statisticsService.getJobOpenings("token", 1, 10);

            // Kiểm tra: workLocation phải là "Remote"
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getWorkLocation()).isEqualTo("Remote");
        }
    }

    // Test Case ID: UTIL-ST06
    // Mục tiêu: isRemote=false + location chứa "Hybrid" -> workLocation là "Hybrid"
    @Test
    @DisplayName("UTIL-ST06: getJobOpenings - Location chứa 'Hybrid', workLocation phải là 'Hybrid'")
    void getJobOpenings_HybridLocationPosition_ShouldSetWorkLocationToHybrid() {
        // Chuẩn bị: Không remote, location là "Ho Chi Minh - Hybrid"
        JsonNode hybridPosition = buildJobPositionNode(false, "Ho Chi Minh - Hybrid", "15000000", null);

        PaginationDTO mockPagination = new PaginationDTO();
        mockPagination.setResult(Arrays.asList(hybridPosition));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPagination);

            // Thực thi
            List<JobOpeningDTO> result = statisticsService.getJobOpenings("token", 1, 10);

            // Kiểm tra: workLocation phải là "Hybrid"
            assertThat(result.get(0).getWorkLocation()).isEqualTo("Hybrid");
        }
    }

    // Test Case ID: UTIL-ST07
    // Mục tiêu: isRemote=false + location không chứa "Hybrid" -> workLocation là "On-site"
    @Test
    @DisplayName("UTIL-ST07: getJobOpenings - Location thông thường, workLocation phải là 'On-site'")
    void getJobOpenings_OnsitePosition_ShouldSetWorkLocationToOnSite() {
        // Chuẩn bị: Không remote, location là "Ha Noi"
        JsonNode onsitePosition = buildJobPositionNode(false, "Ha Noi", "10000000", "20000000");

        PaginationDTO mockPagination = new PaginationDTO();
        mockPagination.setResult(Arrays.asList(onsitePosition));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPagination);

            // Thực thi
            List<JobOpeningDTO> result = statisticsService.getJobOpenings("token", 1, 10);

            // Kiểm tra: workLocation phải là "On-site"
            assertThat(result.get(0).getWorkLocation()).isEqualTo("On-site");
        }
    }

    // Test Case ID: UTIL-ST08
    // Mục tiêu: Cả hai salary có giá trị -> salaryDisplay là "X - Y triệu"
    @Test
    @DisplayName("UTIL-ST08: getJobOpenings - Cả hai salary, salaryDisplay là 'X - Y triệu'")
    void getJobOpenings_WithBothSalaries_ShouldFormatSalaryAsRange() {
        // Chuẩn bị: salaryMin=10,000,000 (10 triệu), salaryMax=20,000,000 (20 triệu)
        JsonNode positionWithSalary = buildJobPositionNode(false, "Ha Noi", "10000000", "20000000");

        PaginationDTO mockPagination = new PaginationDTO();
        mockPagination.setResult(Arrays.asList(positionWithSalary));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPagination);

            // Thực thi
            List<JobOpeningDTO> result = statisticsService.getJobOpenings("token", 1, 10);

            // Kiểm tra: salaryDisplay phải chứa "triệu" và có dấu " - " phân cách
            String salaryDisplay = result.get(0).getSalaryDisplay();
            assertThat(salaryDisplay).contains("triệu");
            assertThat(salaryDisplay).contains(" - ");
        }
    }

    // Test Case ID: UTIL-ST09
    // Mục tiêu: jobServiceClient trả về null -> phải trả về danh sách rỗng (không NPE)
    @Test
    @DisplayName("UTIL-ST09: getJobOpenings - jobServiceClient null, phải trả về danh sách rỗng")
    void getJobOpenings_NullResultFromJobService_ShouldReturnEmptyList() {
        // Chuẩn bị: jobServiceClient trả về null
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(null);

            // Thực thi: Không được throw NullPointerException
            List<JobOpeningDTO> result = statisticsService.getJobOpenings("token", 1, 10);

            // Kiểm tra: Danh sách rỗng (an toàn)
            assertThat(result).isEmpty();
        }
    }

    // Test Case ID: UTIL-ST10
    // Mục tiêu: Cả hai salary null -> salaryDisplay phải là "" (chuỗi rỗng)
    @Test
    @DisplayName("UTIL-ST10: getJobOpenings - Cả hai salary null, salaryDisplay phải là chuỗi rỗng")
    void getJobOpenings_NullBothSalaries_ShouldReturnEmptySalaryDisplay() {
        // Chuẩn bị: Cả hai salary null
        JsonNode positionNullSalary = buildJobPositionNode(false, "Ha Noi", null, null);

        PaginationDTO mockPagination = new PaginationDTO();
        mockPagination.setResult(Arrays.asList(positionNullSalary));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenReturn(mockPagination);

            // Thực thi
            List<JobOpeningDTO> result = statisticsService.getJobOpenings("token", 1, 10);

            // Kiểm tra: salaryDisplay phải là chuỗi rỗng
            assertThat(result.get(0).getSalaryDisplay()).isEqualTo("");
        }
    }

    // =======================================================================
    // PHẦN 3: Hàm getUpcomingSchedules()
    // =======================================================================

    // Test Case ID: UTIL-ST11
    // Mục tiêu: Participant có participantType="CANDIDATE" -> lấy candidateName từ "name"
    @Test
    @DisplayName("UTIL-ST11: getUpcomingSchedules - Có CANDIDATE participant, phải lấy candidateName")
    void getUpcomingSchedules_WithCandidateParticipant_ShouldExtractCandidateName() {
        // Chuẩn bị: Lịch phỏng vấn có participant CANDIDATE
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 1L);
        schedule.put("startTime", "2026-04-20T10:00:00");
        schedule.put("title", "Interview Round 1");
        schedule.put("meetingType", "ONLINE");
        schedule.put("status", "SCHEDULED");

        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.put("participantType", "CANDIDATE");
        candidate.put("name", "Nguyen Van A");

        schedule.putArray("participants").add(candidate);

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);

            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            // Thực thi
            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            // Kiểm tra: candidateName phải là "Nguyen Van A"
            assertThat(result.getSchedules()).hasSize(1);
            assertThat(result.getSchedules().get(0).getCandidateName()).isEqualTo("Nguyen Van A");
        }
    }

    // Test Case ID: UTIL-ST12
    // Mục tiêu: Participants rỗng -> candidateName phải là chuỗi rỗng
    @Test
    @DisplayName("UTIL-ST12: getUpcomingSchedules - Không có participant, candidateName là chuỗi rỗng")
    void getUpcomingSchedules_WithNoParticipants_ShouldReturnEmptyCandidateName() {
        // Chuẩn bị: Lịch không có participants
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 2L);
        schedule.put("startTime", "2026-04-25T14:00:00");
        schedule.put("title", "Interview Round 2");
        schedule.putArray("participants"); // Mảng rỗng

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);

            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            // Thực thi
            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            // Kiểm tra: candidateName là chuỗi rỗng
            assertThat(result.getSchedules().get(0).getCandidateName()).isEqualTo("");
        }
    }

    // =======================================================================
    // PHẦN 4: getDepartmentIdForStatistics() - Kiểm tra gián tiếp qua getSummaryStatistics
    // =======================================================================

    // Test Case ID: UTIL-ST13
    // Mục tiêu: Role=CEO -> departmentId phải là null khi gọi candidateServiceClient
    @Test
    @DisplayName("UTIL-ST13: getSummaryStatistics - Role CEO, phải truyền departmentId=null vào client")
    void getSummaryStatistics_CeoRole_ShouldPassNullDepartmentIdToClient() {
        // Chuẩn bị: Role là CEO
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi
            statisticsService.getSummaryStatistics("token", start, end);

            // Minh chứng (CheckDB): candidateServiceClient phải được gọi với departmentId=null (CEO xem tất cả)
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(),
                    isNull(), // departmentId = null
                    any(),
                    any(),
                    any(),
                    isNull());
        }
    }

    // Test Case ID: UTIL-ST14
    // Mục tiêu: formatSalary - min == null
    @Test
    @DisplayName("UTIL-ST14: formatSalary - min == null phải lấy max")
    void getJobOpenings_WithMinNull_ShouldFormatMax() {
        JsonNode pos = buildJobPositionNode(false, "Ha Noi", null, "20000000");
        PaginationDTO dto = new PaginationDTO(); dto.setResult(Arrays.asList(pos));
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);
            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res.get(0).getSalaryDisplay()).isEqualTo("20 triệu");
        }
    }

    // Test Case ID: UTIL-ST15
    // Mục tiêu: formatSalary - min bằng max
    @Test
    @DisplayName("UTIL-ST15: formatSalary - min bằng max")
    void getJobOpenings_WithMinEqualMax_ShouldFormatMin() {
        JsonNode pos = buildJobPositionNode(false, "Ha Noi", "10000000", "10000000");
        PaginationDTO dto = new PaginationDTO(); dto.setResult(Arrays.asList(pos));
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);
            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res.get(0).getSalaryDisplay()).isEqualTo("10 triệu");
        }
    }

    // Test Case ID: UTIL-ST16
    // Mục tiêu: formatVND - thousands (amount < 1_000_000)
    @Test
    @DisplayName("UTIL-ST16: formatSalary - amount < 1 triệu để rơi vào thousands")
    void getJobOpenings_WithLessThanOneMillion_ShouldFormatThousands() {
        JsonNode pos = buildJobPositionNode(false, "Ha Noi", "500000", null);
        PaginationDTO dto = new PaginationDTO(); dto.setResult(Arrays.asList(pos));
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);
            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res.get(0).getSalaryDisplay()).isEqualTo("500 triệu"); // Do logic chia 1000 rồi ghép " triệu"
        }
    }

    // Test Case ID: UTIL-ST17
    // Mục tiêu: formatVND - amount == null (bằng Reflection)
    @Test
    @DisplayName("UTIL-ST17: formatVND - lượng tiền null (Reflection)")
    void formatVND_WithNullAmount_ShouldReturnZero() throws Exception {
        java.lang.reflect.Method method = StatisticsService.class.getDeclaredMethod("formatVND", java.math.BigDecimal.class);
        method.setAccessible(true);
        String result = (String) method.invoke(statisticsService, (java.math.BigDecimal) null);
        assertThat(result).isEqualTo("0");
    }

    // Test Case ID: UTIL-ST18
    // Mục tiêu: getDepartmentIdForStatistics - role = null
    @Test
    @DisplayName("UTIL-ST18: getDepartmentIdForStatistics - role = null")
    void getDepartmentId_WithNullRole() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn(null);
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), isNull())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            statisticsService.getSummaryStatistics("token", null, null);
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(any(), isNull(), any(), any(), any(), isNull());
        }
    }

    // Test Case ID: UTIL-ST19
    // Mục tiêu: getDepartmentIdForStatistics - STAFF HR
    @Test
    @DisplayName("UTIL-ST19: getDepartmentIdForStatistics - STAFF phòng HR thì lấy tất cả")
    void getDepartmentId_StaffHr_ShouldBeNull() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("STAFF");
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn("HR");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), isNull())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            statisticsService.getSummaryStatistics("token", null, null);
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(any(), isNull(), any(), any(), any(), isNull());
        }
    }

    // Test Case ID: UTIL-ST20
    // Mục tiêu: getDepartmentIdForStatistics - STAFF IT
    @Test
    @DisplayName("UTIL-ST20: getDepartmentIdForStatistics - STAFF phòng IT thì lấy nội bộ")
    void getDepartmentId_StaffIT_ShouldReturnDepartmentId() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("STAFF");
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn("IT");
            secUtil.when(SecurityUtil::extractDepartmentId).thenReturn(2L);
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), eq(2L))).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            statisticsService.getSummaryStatistics("token", null, null);
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(any(), isNull(), any(), any(), any(), eq(2L));
        }
    }

    // Test Case ID: UTIL-ST21
    // Mục tiêu: getDepartmentIdForStatistics - MANAGER HR
    @Test
    @DisplayName("UTIL-ST21: getDepartmentIdForStatistics - MANAGER phòng HR thì lấy tất cả")
    void getDepartmentId_ManagerHr_ShouldBeNull() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("MANAGER");
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn("HR");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), isNull())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            statisticsService.getSummaryStatistics("token", null, null);
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(any(), isNull(), any(), any(), any(), isNull());
        }
    }

    // Test Case ID: UTIL-ST22
    // Mục tiêu: getDepartmentIdForStatistics - MANAGER IT
    @Test
    @DisplayName("UTIL-ST22: getDepartmentIdForStatistics - MANAGER phòng IT thì lấy nội bộ")
    void getDepartmentId_ManagerIT_ShouldReturnDepartmentId() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("MANAGER");
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn("IT");
            secUtil.when(SecurityUtil::extractDepartmentId).thenReturn(3L);
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), eq(3L))).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            statisticsService.getSummaryStatistics("token", null, null);
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(any(), isNull(), any(), any(), any(), eq(3L));
        }
    }

    // Test Case ID: UTIL-ST23
    // Mục tiêu: getDepartmentIdForStatistics - GUEST
    @Test
    @DisplayName("UTIL-ST23: getDepartmentIdForStatistics - Role GUEST không thuộc switch")
    void getDepartmentId_Guest_ShouldBeNull() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("GUEST");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), isNull())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            statisticsService.getSummaryStatistics("token", null, null);
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(any(), isNull(), any(), any(), any(), isNull());
        }
    }

    // Test Case ID: UTIL-ST24
    // Mục tiêu: filterApplications By DateRange - Exception 
    @Test
    @DisplayName("UTIL-ST24: filterApplications - Handle exception date parse ngầm định false")
    void filterApplications_ExceptionParsing() {
        List<JsonNode> applications = Arrays.asList(buildApplicationNode("INVALID_DATE", "PENDING"));
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), any())).thenReturn(applications);
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", LocalDate.now(), LocalDate.now().plusDays(1));
            assertThat(result.getApplications()).isEqualTo(0L);
        }
    }

    // Test Case ID: UTIL-ST25
    // Mục tiêu: filterApplications By DateRange And Status - Exception
    @Test
    @DisplayName("UTIL-ST25: filterApplicationsAndStatus - Handle exception date parse ngầm định false")
    void filterApplicationsAndStatus_ExceptionParsing() {
        List<JsonNode> applications = Arrays.asList(buildApplicationNode("INVALID_DATE", "HIRED"));
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), any())).thenReturn(applications);
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", LocalDate.now(), LocalDate.now().plusDays(1));
            assertThat(result.getHired()).isEqualTo(0L); // Trượt khỏi block if (catch exception trả false)
        }
    }

    // Test Case ID: UTIL-ST26
    // Mục tiêu: filterSchedules - Cover nhánh parse thành công và exception
    @Test
    @DisplayName("UTIL-ST26: filterSchedules - Valid và Invalid Date")
    void filterSchedules_ValidAndException() {
        List<JsonNode> schedules = Arrays.asList(
            buildScheduleNode("2026-04-10T10:00:00"), // Valid (năm 2026 trong Range)
            buildScheduleNode("INVALID_TIME") // Exception
        );

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any())).thenReturn(schedules);

            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", LocalDate.of(2026,4,1), LocalDate.of(2026,4,30));
            assertThat(result.getInterviews()).isEqualTo(1L); // Nhận 1 bản valid
        }
    }

    // Test Case ID: UTIL-ST27
    // Mục tiêu: parseDateTime - Fallback to LocalDate
    @Test
    @DisplayName("UTIL-ST27: parseDateTime - Fallback catch to LocalDate")
    void parseDateTime_FallbackToLocalDate() {
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 1L);
        schedule.put("startTime", "2026-04-20"); // Thiếu Time nên nhảy vào fallback catch
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(schedule));
            
            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);
            assertThat(result.getSchedules().get(0).getDate()).isEqualTo("2026-04-20");
        }
    }

    // Test Case ID: UTIL-ST28
    // Mục tiêu: parseDateTime - Return null completely
    @Test
    @DisplayName("UTIL-ST28: parseDateTime - Exception hoàn toàn -> null")
    void parseDateTime_FullExceptionReturnNull() {
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 1L);
        schedule.put("startTime", "TOTALLY_INVALID");
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(schedule));
            
            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);
            assertThat(result.getSchedules().get(0).getDate()).isEqualTo(""); // Null fallback ""
        }
    }

    // Test Case ID: UTIL-ST29
    // Mục tiêu: convertToJsonNode - obj == null
    @Test
    @DisplayName("UTIL-ST29: convertToJsonNode - obj null")
    void convertToJsonNode_NullObject() {
        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList((Object)null)); // Chèn 1 Object null
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);
            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res).isEmpty(); // Object null bị ignore filter
        }
    }

    // Test Case ID: UTIL-ST30
    // Mục tiêu: convertToJsonNode - ObjectMapper exception
    @Test
    @DisplayName("UTIL-ST30: convertToJsonNode - IllegalArgument Exception")
    void convertToJsonNode_ObjectMapperException() {
        PaginationDTO dto = new PaginationDTO();
        // Tạo 1 object lủng để serialize throw Exception
        Object badObj = new Object() {
            @Override
            public String toString() { return "Bad Object"; }
        }; 
        dto.setResult(Arrays.asList(badObj));
        
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);
            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res).isEmpty(); // Bị ignored
        }
    }
}

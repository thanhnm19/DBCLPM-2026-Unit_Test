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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // =======================================================================
    // PHẦN 5: Bổ sung TC để đạt độ phủ Mức 1 (Statement) & Mức 2 (Branch) 100%
    // =======================================================================

    // Test Case ID: UTIL-ST31
    // Mục tiêu: startDate có giá trị, endDate=null -> periodEnd phải = startDate + 7 ngày
    @Test
    @DisplayName("UTIL-ST31: getSummaryStatistics - startDate có, endDate=null phải set mặc định + 7 ngày")
    void getSummaryStatistics_WithStartDateOnly_ShouldDefaultEndDateToPlusSeven() {
        LocalDate start = LocalDate.of(2026, 4, 1);

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", start, null);

            // Kiểm tra: không null
            assertThat(result).isNotNull();
            // Minh chứng: candidateServiceClient gọi với startDate=2026-04-01, endDate=2026-04-08
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(),
                    isNull(),
                    eq("2026-04-01"),
                    eq("2026-04-08"),
                    any(),
                    isNull());
        }
    }

    // Test Case ID: UTIL-ST32
    // Mục tiêu: startDate=null, endDate có giá trị -> periodStart phải = today
    @Test
    @DisplayName("UTIL-ST32: getSummaryStatistics - startDate=null, endDate có thì periodStart=today")
    void getSummaryStatistics_WithEndDateOnly_ShouldDefaultStartDateToToday() {
        LocalDate end = LocalDate.of(2026, 4, 30);
        LocalDate today = LocalDate.now();

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", null, end);

            assertThat(result).isNotNull();
            // Minh chứng: startDate=today, endDate=2026-04-30
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(),
                    isNull(),
                    eq(today.toString()),
                    eq("2026-04-30"),
                    any(),
                    isNull());
        }
    }

    // Test Case ID: UTIL-ST33
    // Mục tiêu: startDate > endDate -> không crash, count=0 do filter ra rỗng
    @Test
    @DisplayName("UTIL-ST33: getSummaryStatistics - startDate > endDate, kết quả count=0")
    void getSummaryStatistics_StartDateAfterEndDate_ShouldReturnZeroCount() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 4, 1);

        // Cố tình cho dữ liệu nằm giữa, nhưng filter sẽ luôn loại bỏ vì start > end
        List<JsonNode> applications = Arrays.asList(
                buildApplicationNode("2026-04-15", "HIRED"),
                buildApplicationNode("2026-04-20", "REJECTED"));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(applications);
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

            // Thực thi: không được throw exception
            SummaryStatisticsDTO result = statisticsService.getSummaryStatistics("token", start, end);

            // Kiểm tra: count=0 do filter loại bỏ
            assertThat(result.getApplications()).isEqualTo(0L);
            assertThat(result.getHired()).isEqualTo(0L);
            assertThat(result.getRejected()).isEqualTo(0L);
            assertThat(result.getInterviews()).isEqualTo(0L);
        }
    }

    // Test Case ID: UTIL-ST34
    // Mục tiêu: candidateServiceClient throw exception -> getSummaryStatistics ném lỗi (theo implement)
    @Test
    @DisplayName("UTIL-ST34: getSummaryStatistics - candidateServiceClient throw -> exception lan tỏa")
    void getSummaryStatistics_CandidateClientThrows_ShouldPropagateException() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Candidate service down"));

            // Kiểm tra: phải ném RuntimeException (theo implement, không có try/catch ở SUT)
            assertThatThrownBy(() -> statisticsService.getSummaryStatistics(
                    "token", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Candidate service down");
        }
    }

    // Test Case ID: UTIL-ST35
    // Mục tiêu: scheduleServiceClient throw exception -> ném lỗi
    @Test
    @DisplayName("UTIL-ST35: getSummaryStatistics - scheduleServiceClient throw -> exception lan tỏa")
    void getSummaryStatistics_ScheduleClientThrows_ShouldPropagateException() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(candidateServiceClient.getApplicationsForStatistics(
                    any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(
                    any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Schedule service down"));

            assertThatThrownBy(() -> statisticsService.getSummaryStatistics(
                    "token", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Schedule service down");
        }
    }

    // Test Case ID: UTIL-ST36
    // Mục tiêu: getJobOpenings - jobServiceClient throw -> exception lan tỏa
    @Test
    @DisplayName("UTIL-ST36: getJobOpenings - jobServiceClient throw -> exception lan tỏa")
    void getJobOpenings_JobClientThrows_ShouldPropagateException() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");

            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Job service down"));

            assertThatThrownBy(() -> statisticsService.getJobOpenings("token", 1, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Job service down");
        }
    }

    // Test Case ID: UTIL-ST37
    // Mục tiêu: getJobOpenings - location null/rỗng và isRemote=false -> workLocation default "On-site"
    @Test
    @DisplayName("UTIL-ST37: getJobOpenings - location null + isRemote=false -> workLocation='On-site'")
    void getJobOpenings_NullLocationAndNotRemote_ShouldDefaultToOnSite() {
        // location = null (sẽ thành chuỗi rỗng), isRemote=false
        JsonNode pos = buildJobPositionNode(false, null, "10000000", "20000000");

        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(pos));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);

            assertThat(res).hasSize(1);
            assertThat(res.get(0).getWorkLocation()).isEqualTo("On-site");
        }
    }

    // Test Case ID: UTIL-ST38
    // Mục tiêu: location chữ thường "hcm hybrid" -> implement case-sensitive nên KHÔNG match
    @Test
    @DisplayName("UTIL-ST38: getJobOpenings - location='hcm hybrid' (lowercase) -> 'On-site' do case-sensitive")
    void getJobOpenings_LowercaseHybrid_ShouldFallbackToOnSite() {
        JsonNode pos = buildJobPositionNode(false, "hcm hybrid", "10000000", "20000000");

        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(pos));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);

            // Implement dùng contains("Hybrid") (case-sensitive) -> không match -> "On-site"
            assertThat(res.get(0).getWorkLocation()).isEqualTo("On-site");
        }
    }

    // Test Case ID: UTIL-ST39
    // Mục tiêu: salaryMin có giá trị, salaryMax=null -> "10 triệu"
    @Test
    @DisplayName("UTIL-ST39: formatSalary - min có, max=null -> 'X triệu'")
    void getJobOpenings_WithMaxNull_ShouldFormatMin() {
        JsonNode pos = buildJobPositionNode(false, "Ha Noi", "10000000", null);
        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(pos));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res.get(0).getSalaryDisplay()).isEqualTo("10 triệu");
        }
    }

    // Test Case ID: UTIL-ST40
    // Mục tiêu: min > max -> không crash, hiển thị theo implement "20 - 10 triệu"
    @Test
    @DisplayName("UTIL-ST40: formatSalary - min > max, không crash, hiển thị 'min - max triệu'")
    void getJobOpenings_WithMinGreaterMax_ShouldNotCrash() {
        JsonNode pos = buildJobPositionNode(false, "Ha Noi", "20000000", "10000000");
        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(pos));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            // Theo implement: ghép trực tiếp min - max
            assertThat(res.get(0).getSalaryDisplay()).isEqualTo("20 - 10 triệu");
        }
    }

    // Test Case ID: UTIL-ST41
    // Mục tiêu: Job item null trong list -> bỏ qua, không NPE
    @Test
    @DisplayName("UTIL-ST41: getJobOpenings - list chứa null item, bỏ qua")
    void getJobOpenings_ListWithNullItem_ShouldSkipNull() {
        JsonNode validPos = buildJobPositionNode(false, "Ha Noi", "10000000", "20000000");
        PaginationDTO dto = new PaginationDTO();
        // Trộn 1 null và 1 hợp lệ
        dto.setResult(Arrays.asList((Object) null, validPos));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);

            // Chỉ trả về 1 item hợp lệ
            assertThat(res).hasSize(1);
            assertThat(res.get(0).getTitle()).isEqualTo("Developer");
        }
    }

    // Test Case ID: UTIL-ST36b
    // Mục tiêu: getJobOpenings - jobPositions != null nhưng getResult() == null -> trả list rỗng
    @Test
    @DisplayName("UTIL-ST36b: getJobOpenings - jobPositions.getResult()=null -> list rỗng (cover branch)")
    void getJobOpenings_NullResultField_ShouldReturnEmptyList() {
        PaginationDTO dto = new PaginationDTO();
        dto.setResult(null); // result null

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res).isEmpty();
        }
    }

    // Test Case ID: UTIL-ST42
    // Mục tiêu: scheduleService trả null -> NPE lan tỏa (theo implement, không có null-safe)
    @Test
    @DisplayName("UTIL-ST42: getUpcomingSchedules - client trả null -> ném NullPointerException")
    void getUpcomingSchedules_NullResult_ShouldThrowNpe() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);

            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(null);

            // Implement gọi schedules.stream() -> NullPointerException
            assertThatThrownBy(() -> statisticsService.getUpcomingSchedules("token", 10))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // Test Case ID: UTIL-ST43
    // Mục tiêu: schedule.participants không có (absent) -> candidateName="", không NPE
    @Test
    @DisplayName("UTIL-ST43: getUpcomingSchedules - không có field 'participants', không NPE")
    void getUpcomingSchedules_NoParticipantsField_ShouldNotThrow() {
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 99L);
        schedule.put("startTime", "2026-04-20T10:00:00");
        schedule.put("title", "Interview");
        // Không có field "participants"

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            assertThat(result.getSchedules()).hasSize(1);
            assertThat(result.getSchedules().get(0).getCandidateName()).isEqualTo("");
        }
    }

    // Test Case ID: UTIL-ST44
    // Mục tiêu: Có nhiều CANDIDATE participant -> chỉ lấy người đầu tiên (do break)
    @Test
    @DisplayName("UTIL-ST44: getUpcomingSchedules - nhiều CANDIDATE, chỉ lấy người đầu tiên")
    void getUpcomingSchedules_MultipleCandidates_ShouldTakeFirst() {
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 1L);
        schedule.put("startTime", "2026-04-20T10:00:00");
        schedule.put("title", "Interview");

        ObjectNode firstCand = objectMapper.createObjectNode();
        firstCand.put("participantType", "CANDIDATE");
        firstCand.put("name", "First Candidate");

        ObjectNode secondCand = objectMapper.createObjectNode();
        secondCand.put("participantType", "CANDIDATE");
        secondCand.put("name", "Second Candidate");

        schedule.putArray("participants").add(firstCand).add(secondCand);

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            // Chỉ lấy CANDIDATE đầu tiên (do break)
            assertThat(result.getSchedules().get(0).getCandidateName()).isEqualTo("First Candidate");
        }
    }

    // Test Case ID: UTIL-ST45
    // Mục tiêu: CANDIDATE participant không có name -> candidateName="" (do has("name")=false)
    @Test
    @DisplayName("UTIL-ST45: getUpcomingSchedules - CANDIDATE thiếu 'name', candidateName=''")
    void getUpcomingSchedules_CandidateMissingName_ShouldReturnEmpty() {
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 1L);
        schedule.put("startTime", "2026-04-20T10:00:00");

        ObjectNode candidate = objectMapper.createObjectNode();
        candidate.put("participantType", "CANDIDATE");
        // Không có field "name"

        ObjectNode otherType = objectMapper.createObjectNode();
        otherType.put("participantType", "INTERVIEWER");
        otherType.put("name", "Mr. X"); // sẽ bị bỏ qua

        // Một participant không có participantType (cover nhánh has("participantType")=false)
        ObjectNode noType = objectMapper.createObjectNode();
        noType.put("name", "Unknown");

        schedule.putArray("participants").add(noType).add(otherType).add(candidate);

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            // CANDIDATE không có name -> candidateName=""
            assertThat(result.getSchedules().get(0).getCandidateName()).isEqualTo("");
        }
    }

    // Test Case ID: UTIL-ST46
    // Mục tiêu: schedule không có startTime -> date="", time="", không crash
    @Test
    @DisplayName("UTIL-ST46: getUpcomingSchedules - schedule không có 'startTime', không crash, date=''")
    void getUpcomingSchedules_NoStartTime_ShouldNotCrash() {
        ObjectNode schedule = objectMapper.createObjectNode();
        // Không có id, startTime, title, participants, meetingType, status
        // Cover các nhánh has(...) = false

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            assertThat(result.getSchedules()).hasSize(1);
            UpcomingScheduleDTO.ScheduleItem item = result.getSchedules().get(0);
            assertThat(item.getDate()).isEqualTo("");
            assertThat(item.getTime()).isEqualTo("");
            assertThat(item.getJobTitle()).isEqualTo("");
            assertThat(item.getCandidateName()).isEqualTo("");
            // type fallback "Phỏng vấn" khi thiếu meetingType
            assertThat(item.getType()).isEqualTo("Phỏng vấn");
            assertThat(item.getStatus()).isEqualTo("");
        }
    }

    // Test Case ID: UTIL-ST47
    // Mục tiêu: role lowercase ("manager", "staff") -> implement gọi toUpperCase() nên vẫn match
    @Test
    @DisplayName("UTIL-ST47: getDepartmentIdForStatistics - role lowercase được toUpperCase và match")
    void getDepartmentId_RoleLowercase_ShouldMatchAfterUpperCase() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("manager"); // lowercase
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn("IT");
            secUtil.when(SecurityUtil::extractDepartmentId).thenReturn(7L);

            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), eq(7L)))
                    .thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            statisticsService.getSummaryStatistics("token", null, null);

            // Verify: role "manager" được toUpperCase -> match MANAGER -> trả departmentId=7
            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(), isNull(), any(), any(), any(), eq(7L));
        }
    }

    // Test Case ID: UTIL-ST48
    // Mục tiêu: STAFF với departmentCode=null -> equalsIgnoreCase trả false -> lấy departmentId
    @Test
    @DisplayName("UTIL-ST48: getDepartmentIdForStatistics - STAFF, departmentCode=null, không NPE")
    void getDepartmentId_StaffNullDepartmentCode_ShouldUseDepartmentId() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("STAFF");
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn(null);
            secUtil.when(SecurityUtil::extractDepartmentId).thenReturn(5L);

            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), eq(5L)))
                    .thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            statisticsService.getSummaryStatistics("token", null, null);

            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(), isNull(), any(), any(), any(), eq(5L));
        }
    }

    // Test Case ID: UTIL-ST49
    // Mục tiêu: STAFF, departmentCode=IT, departmentId=null -> trả null, không crash
    @Test
    @DisplayName("UTIL-ST49: getDepartmentIdForStatistics - STAFF IT, departmentId=null, an toàn")
    void getDepartmentId_StaffNullDepartmentId_ShouldBeNull() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("STAFF");
            secUtil.when(SecurityUtil::extractDepartmentCode).thenReturn("IT");
            secUtil.when(SecurityUtil::extractDepartmentId).thenReturn(null);

            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            assertThatCode(() -> statisticsService.getSummaryStatistics("token", null, null))
                    .doesNotThrowAnyException();

            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(), isNull(), any(), any(), any(), isNull());
        }
    }

    // Test Case ID: UTIL-ST49b
    // Mục tiêu: Role ADMIN -> cover case "ADMIN" trong switch (giống CEO)
    @Test
    @DisplayName("UTIL-ST49b: getDepartmentIdForStatistics - ADMIN -> departmentId=null (cover branch)")
    void getDepartmentId_AdminRole_ShouldBeNull() {
        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("ADMIN");
            when(candidateServiceClient.getApplicationsForStatistics(any(), any(), any(), any(), any(), isNull()))
                    .thenReturn(Collections.emptyList());
            when(communicationServiceClient.getSchedulesForStatistics(any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            statisticsService.getSummaryStatistics("token", null, null);

            verify(candidateServiceClient, times(1)).getApplicationsForStatistics(
                    any(), isNull(), any(), any(), any(), isNull());
        }
    }

    // -----------------------------------------------------------------------
    // PHẦN 6: Test trực tiếp các private helper qua Reflection
    // -----------------------------------------------------------------------

    // Test Case ID: UTIL-ST50
    // Mục tiêu: filterApplicationsByDateRange - appliedDate field absent -> bỏ qua
    @Test
    @DisplayName("UTIL-ST50: filterApplicationsByDateRange - appliedDate absent, bỏ qua record")
    @SuppressWarnings("unchecked")
    void filterApplications_WithoutAppliedDate_ShouldSkipRecord() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        ObjectNode noDate = objectMapper.createObjectNode();
        noDate.put("status", "PENDING"); // Không có "appliedDate"

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService,
                Arrays.asList((JsonNode) noDate),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST51
    // Mục tiêu: appliedDate đúng bằng startDate (boundary) -> được tính
    @Test
    @DisplayName("UTIL-ST51: filterApplicationsByDateRange - appliedDate=startDate, được tính (boundary)")
    @SuppressWarnings("unchecked")
    void filterApplications_AppliedDateEqualStartDate_ShouldBeIncluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> apps = Arrays.asList(buildApplicationNode("2026-04-01", "PENDING"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, apps,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).hasSize(1);
    }

    // Test Case ID: UTIL-ST52
    // Mục tiêu: appliedDate đúng bằng endDate (boundary) -> được tính
    @Test
    @DisplayName("UTIL-ST52: filterApplicationsByDateRange - appliedDate=endDate, được tính (boundary)")
    @SuppressWarnings("unchecked")
    void filterApplications_AppliedDateEqualEndDate_ShouldBeIncluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> apps = Arrays.asList(buildApplicationNode("2026-04-30", "PENDING"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, apps,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).hasSize(1);
    }

    // Test Case ID: UTIL-ST53
    // Mục tiêu: filterApplicationsByDateRangeAndStatus - status case-different ("hired" vs "HIRED")
    @Test
    @DisplayName("UTIL-ST53: filterApplicationsByDateRangeAndStatus - case-sensitive 'hired' không match 'HIRED'")
    @SuppressWarnings("unchecked")
    void filterApplicationsAndStatus_DifferentCase_ShouldNotMatch() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRangeAndStatus",
                List.class, LocalDate.class, LocalDate.class, String.class);
        m.setAccessible(true);

        List<JsonNode> apps = Arrays.asList(buildApplicationNode("2026-04-15", "HIRED"));

        // Truyền "hired" lowercase, app có status "HIRED" -> equals case-sensitive -> không match
        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, apps,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "hired");

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST54
    // Mục tiêu: filterApplicationsByDateRangeAndStatus - status=null -> NPE -> catch -> false
    @Test
    @DisplayName("UTIL-ST54: filterApplicationsByDateRangeAndStatus - status=null bị NPE bắt, trả empty")
    @SuppressWarnings("unchecked")
    void filterApplicationsAndStatus_NullStatus_ShouldReturnEmpty() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRangeAndStatus",
                List.class, LocalDate.class, LocalDate.class, String.class);
        m.setAccessible(true);

        List<JsonNode> apps = Arrays.asList(buildApplicationNode("2026-04-15", "HIRED"));

        // status=null -> status.equals(...) ném NPE -> catch -> return false
        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, apps,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                (String) null);

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST54b
    // Mục tiêu: filterApplicationsByDateRangeAndStatus - record không có "status" field -> bỏ qua
    @Test
    @DisplayName("UTIL-ST54b: filterApplicationsByDateRangeAndStatus - record thiếu 'status' field")
    @SuppressWarnings("unchecked")
    void filterApplicationsAndStatus_RecordMissingStatus_ShouldSkip() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRangeAndStatus",
                List.class, LocalDate.class, LocalDate.class, String.class);
        m.setAccessible(true);

        ObjectNode noStatus = objectMapper.createObjectNode();
        noStatus.put("appliedDate", "2026-04-15");

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService,
                Arrays.asList((JsonNode) noStatus),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "HIRED");

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST54c
    // Mục tiêu: filterApplicationsByDateRangeAndStatus - record không có "appliedDate" -> bỏ qua
    @Test
    @DisplayName("UTIL-ST54c: filterApplicationsByDateRangeAndStatus - record thiếu 'appliedDate' field")
    @SuppressWarnings("unchecked")
    void filterApplicationsAndStatus_RecordMissingAppliedDate_ShouldSkip() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRangeAndStatus",
                List.class, LocalDate.class, LocalDate.class, String.class);
        m.setAccessible(true);

        ObjectNode noDate = objectMapper.createObjectNode();
        noDate.put("status", "HIRED");
        // Không có "appliedDate"

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService,
                Arrays.asList((JsonNode) noDate),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "HIRED");

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST55
    // Mục tiêu: filterSchedulesByDateRange - record không có "startTime" -> bỏ qua
    @Test
    @DisplayName("UTIL-ST55: filterSchedulesByDateRange - thiếu 'startTime', không NPE, bỏ qua")
    @SuppressWarnings("unchecked")
    void filterSchedules_WithoutStartTime_ShouldSkip() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterSchedulesByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        ObjectNode noStart = objectMapper.createObjectNode();
        noStart.put("title", "Interview");

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService,
                Arrays.asList((JsonNode) noStart),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST56
    // Mục tiêu: filterSchedulesByDateRange - startTime=startDate at 00:00 (boundary) -> tính
    @Test
    @DisplayName("UTIL-ST56: filterSchedulesByDateRange - startTime=startDate (boundary)")
    @SuppressWarnings("unchecked")
    void filterSchedules_StartTimeEqualStartDate_ShouldBeIncluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterSchedulesByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> schedules = Arrays.asList(buildScheduleNode("2026-04-01T00:00:00"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, schedules,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).hasSize(1);
    }

    // Test Case ID: UTIL-ST57
    // Mục tiêu: filterSchedulesByDateRange - startTime=endDate at 23:59 (boundary) -> tính
    @Test
    @DisplayName("UTIL-ST57: filterSchedulesByDateRange - startTime=endDate (boundary)")
    @SuppressWarnings("unchecked")
    void filterSchedules_StartTimeEqualEndDate_ShouldBeIncluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterSchedulesByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> schedules = Arrays.asList(buildScheduleNode("2026-04-30T23:59:00"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, schedules,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).hasSize(1);
    }

    // Test Case ID: UTIL-ST58
    // Mục tiêu: parseDateTime - input null -> trả null, không ném lỗi
    @Test
    @DisplayName("UTIL-ST58: parseDateTime - input null -> trả null")
    void parseDateTime_NullInput_ShouldReturnNull() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("parseDateTime", String.class);
        m.setAccessible(true);

        LocalDateTime result = (LocalDateTime) m.invoke(statisticsService, (Object) null);
        assertThat(result).isNull();
    }

    // Test Case ID: UTIL-ST59
    // Mục tiêu: parseDateTime - input "" -> trả null
    @Test
    @DisplayName("UTIL-ST59: parseDateTime - input rỗng -> trả null")
    void parseDateTime_EmptyInput_ShouldReturnNull() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("parseDateTime", String.class);
        m.setAccessible(true);

        LocalDateTime result = (LocalDateTime) m.invoke(statisticsService, "");
        assertThat(result).isNull();
    }

    // Test Case ID: UTIL-ST60
    // Mục tiêu: parseDateTime - ISO DateTime hợp lệ -> parse đúng LocalDateTime
    @Test
    @DisplayName("UTIL-ST60: parseDateTime - ISO DateTime hợp lệ, parse chính xác")
    void parseDateTime_ValidIso_ShouldParseCorrectly() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("parseDateTime", String.class);
        m.setAccessible(true);

        LocalDateTime result = (LocalDateTime) m.invoke(statisticsService, "2026-04-20T10:30:00");
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 4, 20, 10, 30, 0));
    }

    // Test Case ID: UTIL-ST61
    // Mục tiêu: convertToJsonNode - object là String "abc" -> JsonNode text
    @Test
    @DisplayName("UTIL-ST61: convertToJsonNode - String 'abc' -> TextNode")
    void convertToJsonNode_String_ShouldReturnTextNode() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("convertToJsonNode", Object.class);
        m.setAccessible(true);

        JsonNode result = (JsonNode) m.invoke(statisticsService, "abc");
        assertThat(result).isNotNull();
        assertThat(result.isTextual()).isTrue();
        assertThat(result.asText()).isEqualTo("abc");
    }

    // Test Case ID: UTIL-ST62
    // Mục tiêu: convertToJsonNode - Map.of("id",1) -> JsonNode có field id
    @Test
    @DisplayName("UTIL-ST62: convertToJsonNode - Map -> ObjectNode có field 'id'")
    void convertToJsonNode_Map_ShouldReturnObjectNodeWithIdField() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("convertToJsonNode", Object.class);
        m.setAccessible(true);

        Map<String, Object> map = new HashMap<>();
        map.put("id", 1);

        JsonNode result = (JsonNode) m.invoke(statisticsService, map);
        assertThat(result).isNotNull();
        assertThat(result.has("id")).isTrue();
        assertThat(result.get("id").asInt()).isEqualTo(1);
    }

    // Test Case ID: UTIL-ST63
    // Mục tiêu: convertToJsonNode - List rỗng -> ArrayNode rỗng
    @Test
    @DisplayName("UTIL-ST63: convertToJsonNode - List rỗng -> ArrayNode rỗng")
    void convertToJsonNode_EmptyList_ShouldReturnEmptyArray() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("convertToJsonNode", Object.class);
        m.setAccessible(true);

        JsonNode result = (JsonNode) m.invoke(statisticsService, Collections.emptyList());
        assertThat(result).isNotNull();
        assertThat(result.isArray()).isTrue();
        assertThat(result.size()).isEqualTo(0);
    }

    // Test Case ID: UTIL-ST63b
    // Mục tiêu: convertToJsonNode - object đã là JsonNode -> trả về chính nó
    @Test
    @DisplayName("UTIL-ST63b: convertToJsonNode - đã là JsonNode -> trả về chính nó (cover branch)")
    void convertToJsonNode_AlreadyJsonNode_ShouldReturnItself() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("convertToJsonNode", Object.class);
        m.setAccessible(true);

        ObjectNode jn = objectMapper.createObjectNode();
        jn.put("k", "v");

        JsonNode result = (JsonNode) m.invoke(statisticsService, jn);
        assertThat(result).isSameAs(jn);
    }

    // -----------------------------------------------------------------------
    // PHẦN 7: Bổ sung test cho các nhánh has(...) trong getJobOpenings/getUpcomingSchedules
    // để đạt độ phủ branch 100%
    // -----------------------------------------------------------------------

    // Test Case ID: UTIL-ST-EX01
    // Mục tiêu: getJobOpenings - JsonNode hoàn toàn rỗng (KHÔNG có field nào)
    // Mục đích cover các nhánh has(...)=false: title, employmentType, isRemote, location,
    // applicationCount, salaryMin, salaryMax đều không tồn tại.
    @Test
    @DisplayName("UTIL-ST-EX01: getJobOpenings - JsonNode thiếu toàn bộ field, dùng default an toàn (cover has=false)")
    void getJobOpenings_NodeMissingAllFields_ShouldUseDefaults() {
        ObjectNode minimal = objectMapper.createObjectNode(); // hoàn toàn rỗng

        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(minimal));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);

            assertThat(res).hasSize(1);
            JobOpeningDTO job = res.get(0);
            assertThat(job.getTitle()).isEqualTo("");
            assertThat(job.getEmploymentType()).isEqualTo("Full-time"); // default
            assertThat(job.getWorkLocation()).isEqualTo("On-site"); // default (no isRemote, no location)
            assertThat(job.getApplicantCount()).isEqualTo(0);
            assertThat(job.getSalaryMin()).isNull();
            assertThat(job.getSalaryMax()).isNull();
            assertThat(job.getSalaryDisplay()).isEqualTo("");
        }
    }

    // Test Case ID: UTIL-ST-EX02
    // Mục tiêu: getJobOpenings - JsonNode có salaryMin/salaryMax = NullNode (putNull)
    // Mục đích cover các nhánh has=true && isNull()=true.
    @Test
    @DisplayName("UTIL-ST-EX02: getJobOpenings - salaryMin/salaryMax putNull, xử lý an toàn (cover isNull()=true)")
    void getJobOpenings_NodeWithNullSalaryNodes_ShouldHandleNullValueNodes() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Engineer");
        node.put("employmentType", "Full-time");
        node.put("applicationCount", 3);
        node.put("isRemote", false);
        node.put("location", "Ha Noi");
        node.putNull("salaryMin"); // NullNode -> isNull()=true
        node.putNull("salaryMax"); // NullNode -> isNull()=true

        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(node));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res).hasSize(1);
            assertThat(res.get(0).getSalaryMin()).isNull();
            assertThat(res.get(0).getSalaryMax()).isNull();
        }
    }

    // Test Case ID: UTIL-ST-EX03
    // Mục tiêu: getJobOpenings - isRemote=false (chỉ field tồn tại) + location chứa \"Hybrid\" thật sự
    // Mục đích cover nhánh has("isRemote")=true, asBoolean()=false
    @Test
    @DisplayName("UTIL-ST-EX03: getJobOpenings - isRemote=false và location có 'Hybrid' -> workLocation='Hybrid'")
    void getJobOpenings_IsRemoteFalseWithHybrid_ShouldBeHybrid() {
        JsonNode pos = buildJobPositionNode(false, "HCM - Hybrid", "10000000", "20000000");
        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(pos));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);
            assertThat(res.get(0).getWorkLocation()).isEqualTo("Hybrid");
        }
    }

    // Test Case ID: UTIL-ST-EX04
    // Mục tiêu: getUpcomingSchedules - participants không phải array (cover isArray()=false)
    @Test
    @DisplayName("UTIL-ST-EX04: getUpcomingSchedules - participants không phải array -> candidateName=''")
    void getUpcomingSchedules_ParticipantsNotArray_ShouldNotCrash() {
        ObjectNode schedule = objectMapper.createObjectNode();
        schedule.put("id", 1L);
        schedule.put("startTime", "2026-04-20T10:00:00");
        schedule.put("title", "Interview");
        schedule.put("participants", "not-an-array"); // String, không phải array
        schedule.put("meetingType", "ONLINE");
        schedule.put("status", "SCHEDULED");

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(Collections.singletonList(schedule));

            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            assertThat(result.getSchedules()).hasSize(1);
            assertThat(result.getSchedules().get(0).getCandidateName()).isEqualTo("");
        }
    }

    // Test Case ID: UTIL-ST-EX05
    // Mục tiêu: getUpcomingSchedules - convertToJsonNode trả null (cover schedule == null branch)
    // Đẩy 1 phần tử raw không thể convert được (Object thường) thì valueToTree trả về node, KHÔNG null.
    // -> Để đạt schedule == null, phần tử phải là null trong list.
    @Test
    @DisplayName("UTIL-ST-EX05: getUpcomingSchedules - phần tử null trong list -> bị filter ra")
    void getUpcomingSchedules_NullElementInList_ShouldBeFiltered() {
        // List<JsonNode> chứa null
        List<JsonNode> list = Arrays.asList((JsonNode) null);

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractEmployeeId).thenReturn(10L);
            when(communicationServiceClient.getUpcomingSchedules(any(), anyLong(), anyInt()))
                    .thenReturn(list);

            UpcomingScheduleDTO result = statisticsService.getUpcomingSchedules("token", 10);

            assertThat(result.getSchedules()).isEmpty();
        }
    }

    // Test Case ID: UTIL-ST-EX06
    // Mục tiêu: formatVND - amount=0 -> rơi nhánh thousands, trả '0'
    // hoặc test các giá trị biên.
    @Test
    @DisplayName("UTIL-ST-EX06: formatVND - amount=0 -> trả về '0' (cover thousands branch)")
    void formatVND_ZeroAmount_ShouldReturnZero() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod("formatVND", BigDecimal.class);
        m.setAccessible(true);

        String result = (String) m.invoke(statisticsService, BigDecimal.ZERO);
        // 0 / 1_000_000 = 0 -> không > 0, rơi vào nhánh thousands -> 0/1000 = 0
        assertThat(result).isEqualTo("0");
    }

    // Test Case ID: UTIL-ST-EX07
    // Mục tiêu: filterApplicationsByDateRange - appliedDate AFTER end -> bỏ qua
    // Mục đích cover nhánh !isAfter(end)=false ở L311
    @Test
    @DisplayName("UTIL-ST-EX07: filterApplicationsByDateRange - appliedDate sau endDate, bỏ qua (cover isAfter=true)")
    @SuppressWarnings("unchecked")
    void filterApplications_AppliedDateAfterEnd_ShouldBeExcluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> apps = Arrays.asList(buildApplicationNode("2026-05-15", "PENDING"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, apps,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST-EX08
    // Mục tiêu: filterApplicationsByDateRangeAndStatus - appliedDate AFTER end với status match
    // Mục đích cover nhánh !isAfter(end)=false ở L338
    @Test
    @DisplayName("UTIL-ST-EX08: filterApplicationsByDateRangeAndStatus - appliedDate sau endDate (cover isAfter=true)")
    @SuppressWarnings("unchecked")
    void filterApplicationsAndStatus_AppliedDateAfterEnd_ShouldBeExcluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterApplicationsByDateRangeAndStatus",
                List.class, LocalDate.class, LocalDate.class, String.class);
        m.setAccessible(true);

        List<JsonNode> apps = Arrays.asList(buildApplicationNode("2026-05-15", "HIRED"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, apps,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "HIRED");

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST-EX09
    // Mục tiêu: filterSchedulesByDateRange - scheduleDate BEFORE start -> bỏ qua
    // Mục đích cover nhánh !isBefore(start)=false ở L360
    @Test
    @DisplayName("UTIL-ST-EX09: filterSchedulesByDateRange - schedule trước startDate (cover isBefore=true)")
    @SuppressWarnings("unchecked")
    void filterSchedules_StartTimeBeforeStartDate_ShouldBeExcluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterSchedulesByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> schedules = Arrays.asList(buildScheduleNode("2026-03-15T10:00:00"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, schedules,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST-EX10
    // Mục tiêu: filterSchedulesByDateRange - scheduleDate AFTER end -> bỏ qua
    // Mục đích cover nhánh !isAfter(end)=false ở L361
    @Test
    @DisplayName("UTIL-ST-EX10: filterSchedulesByDateRange - schedule sau endDate (cover isAfter=true)")
    @SuppressWarnings("unchecked")
    void filterSchedules_StartTimeAfterEndDate_ShouldBeExcluded() throws Exception {
        Method m = StatisticsService.class.getDeclaredMethod(
                "filterSchedulesByDateRange", List.class, LocalDate.class, LocalDate.class);
        m.setAccessible(true);

        List<JsonNode> schedules = Arrays.asList(buildScheduleNode("2026-05-15T10:00:00"));

        List<JsonNode> result = (List<JsonNode>) m.invoke(
                statisticsService, schedules,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(result).isEmpty();
    }

    // Test Case ID: UTIL-ST-EX11
    // Mục tiêu: getJobOpenings - location.asText() trả null bằng Mockito mock
    // Mục đích cover nhánh "location != null" = false ở L119 (defensive null-check unreachable
    // bằng JsonNode thật của Jackson, nhưng vẫn có trong bytecode -> dùng mock JsonNode).
    @Test
    @DisplayName("UTIL-ST-EX11: getJobOpenings - location.asText()=null (mock) -> workLocation default 'On-site'")
    void getJobOpenings_LocationAsTextReturnsNull_ShouldUseDefaultOnSite() {
        // Mock JsonNode để location.asText() trả null (covering branch defensive null-check)
        JsonNode mockPosNode = mock(JsonNode.class);
        JsonNode mockLocationNode = mock(JsonNode.class);

        // Title trả về "Test"
        when(mockPosNode.has("title")).thenReturn(true);
        when(mockPosNode.get("title")).thenReturn(com.fasterxml.jackson.databind.node.TextNode.valueOf("Mock"));

        // employmentType: false để dùng default "Full-time"
        when(mockPosNode.has("employmentType")).thenReturn(false);

        // isRemote: false để rẽ vào nhánh else if (location)
        when(mockPosNode.has("isRemote")).thenReturn(false);

        // location: tồn tại nhưng asText() trả null -> branch (location != null) = false
        when(mockPosNode.has("location")).thenReturn(true);
        when(mockPosNode.get("location")).thenReturn(mockLocationNode);
        when(mockLocationNode.asText()).thenReturn(null);

        // applicationCount: false để dùng default 0
        when(mockPosNode.has("applicationCount")).thenReturn(false);

        // salaryMin/Max: false để dùng default null
        when(mockPosNode.has("salaryMin")).thenReturn(false);
        when(mockPosNode.has("salaryMax")).thenReturn(false);

        PaginationDTO dto = new PaginationDTO();
        dto.setResult(Arrays.asList(mockPosNode));

        try (MockedStatic<SecurityUtil> secUtil = mockStatic(SecurityUtil.class)) {
            secUtil.when(SecurityUtil::extractUserRole).thenReturn("CEO");
            when(jobServiceClient.getJobPositions(any(), any(), anyInt(), anyInt())).thenReturn(dto);

            List<JobOpeningDTO> res = statisticsService.getJobOpenings("token", 1, 10);

            assertThat(res).hasSize(1);
            // location null -> short-circuit '&&' -> không vào nhánh "Hybrid" -> giữ default
            assertThat(res.get(0).getWorkLocation()).isEqualTo("On-site");
            assertThat(res.get(0).getTitle()).isEqualTo("Mock");
        }
    }
}

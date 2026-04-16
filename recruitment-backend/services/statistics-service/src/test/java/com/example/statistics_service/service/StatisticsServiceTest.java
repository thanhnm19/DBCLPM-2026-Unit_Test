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
}

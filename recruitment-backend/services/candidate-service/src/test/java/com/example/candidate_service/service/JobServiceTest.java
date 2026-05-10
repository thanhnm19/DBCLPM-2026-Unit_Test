package com.example.candidate_service.service;

import com.example.candidate_service.dto.Meta;
import com.example.candidate_service.dto.PaginationDTO;
import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho JobService (Candidate Service side) - Module 7.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Unit Tests (Candidate Service)")
class JobServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private JobService jobService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jobService = new JobService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(jobService, "jobServiceUrl", "http://job-service");
    }

    @Test
    @DisplayName("JS-TC-001: getJobPositionById")
    void testGetJobPositionById_JS_TC_001() {
        // [Chuẩn bị - Prepare]
        // 1. Tạo dữ liệu JSON mẫu đại diện cho một vị trí công việc (ID: 1)
        ObjectNode data = objectMapper.createObjectNode().put("id", 1);
        Response<JsonNode> body = new Response<>();
        body.setData(data);

        // 2. Đóng gói vào ResponseEntity để giả lập phản hồi thành công (200 OK) từ Job Service
        ResponseEntity<Response<JsonNode>> mockedResponse = (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);
        
        // 3. Mock restTemplate để trả về dữ liệu mẫu khi gọi API lấy chi tiết vị trí công việc
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Gọi hàm lấy thông tin vị trí công việc thông qua JobService
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận kết quả trả về có mã trạng thái là 200 OK
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("JS-TC-002: getJobPositionById - notFound")
    void testGetJobPositionById_NotFound_JS_TC_002() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập phản hồi API thành công nhưng nội dung dữ liệu (data) trả về là null
        Response<JsonNode> body = new Response<>();
        body.setData(null);

        ResponseEntity<Response<JsonNode>> mockedResponse = (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);
        
        // 2. Cấu hình mock để trả về body rỗng này
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện truy vấn ID không tồn tại
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống xử lý trường hợp không có dữ liệu bằng cách trả về mã lỗi 404 NOT FOUND
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-003: getJobPositionIdsByDepartmentId - Pagination")
    void testGetJobPositionIdsByDepartmentId_Pagination_JS_TC_003() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập dữ liệu mẫu cho Trang 1 (chứa ID 101, báo hiệu có tổng cộng 2 trang)
        PaginationDTO p1 = new PaginationDTO();
        p1.setResult(List.of(Map.of("id", 101)));
        Meta m1 = new Meta(); m1.setPages(2); p1.setMeta(m1);
        Response<PaginationDTO> body1 = new Response<>(); body1.setData(p1);

        // 2. Thiết lập dữ liệu mẫu cho Trang 2 (chứa ID 102)
        PaginationDTO p2 = new PaginationDTO();
        p2.setResult(List.of(Map.of("id", 102)));
        Response<PaginationDTO> body2 = new Response<>(); body2.setData(p2);

        ResponseEntity<Response<PaginationDTO>> resp1 = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body1);
        ResponseEntity<Response<PaginationDTO>> resp2 = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body2);

        // 3. Mock API để trả về đúng dữ liệu theo từng tham số page trong URL
        when(restTemplate.exchange(contains("page=1"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(resp1);
        when(restTemplate.exchange(contains("page=2"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(resp2);

        // [Thực thi - Act]
        // Gọi hàm lấy tất cả ID vị trí công việc thuộc một phòng ban (hàm sẽ tự động duyệt qua tất cả các trang)
        List<Long> ids = jobService.getJobPositionIdsByDepartmentId(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận danh sách ID thu được chứa đầy đủ và đúng thứ tự dữ liệu từ cả 2 trang (101 và 102)
        assertThat(ids).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("JS-TC-004: getJobPositionsByDepartmentId")
    void testGetJobPositionsByDepartmentId_JS_TC_004() {
        // [Chuẩn bị - Prepare]
        // 1. Khởi tạo đối tượng PaginationDTO chứa 1 kết quả vị trí công việc mẫu
        PaginationDTO p = new PaginationDTO();
        p.setResult(List.of(Map.of("id", 1)));
        Response<PaginationDTO> body = new Response<>(); body.setData(p);

        // 2. Mock phản hồi API thành công
        ResponseEntity<Response<PaginationDTO>> mockedResponse = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện lấy bản đồ (Map) các vị trí công việc theo phòng ban
        Map<Long, JsonNode> result = jobService.getJobPositionsByDepartmentId(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận bản đồ kết quả thu được có đúng 1 phần tử như đã chuẩn bị
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("JS-TC-005: getJobPositionByIdSimple")
    void testGetJobPositionByIdSimple_JS_TC_005() {
        // [Chuẩn bị - Prepare]
        // 1. Tạo cấu trúc JSON phản hồi thô (không dùng wrapper Response) để kiểm tra cơ chế phân tích trực tiếp
        ObjectNode body = objectMapper.createObjectNode();
        body.put("statusCode", 200);
        body.set("data", objectMapper.createObjectNode().put("id", 1));
        
        // 2. Mock API trả về đối tượng JsonNode trực tiếp
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class))).thenReturn(ResponseEntity.ok(body));

        // [Thực thi - Act]
        // Gọi hàm truy vấn đơn giản lấy thông tin vị trí công việc
        ResponseEntity<JsonNode> result = jobService.getJobPositionByIdSimple(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận mã trạng thái trả về là 200 OK
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("JS-TC-006: getJobPositionById - null token")
    void testGetJobPositionById_NullToken() {
        // [Chuẩn bị - Prepare]
        // Không cần mock API vì hệ thống kiểm tra token ngay từ đầu

        // [Thực thi - Act]
        // Thực hiện gọi hàm với token truyền vào là null
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, null);

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống từ chối truy cập và trả về lỗi 401 UNAUTHORIZED
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JS-TC-007: jobService - handle node mapping edge cases")
    void jobServiceMethods_WithNodeMappingEdgeCases_ShouldHandleGracefully() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập API trả về kết quả hợp lệ
        PaginationDTO p = new PaginationDTO();
        p.setResult(List.of(Map.of("id", 1)));
        Response<PaginationDTO> respP = new Response<>(); respP.setData(p);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(respP));
        
        // 2. Giả lập tình huống lỗi mapping: objectMapper trả về null khi chuyển đổi giá trị sang Tree
        doReturn(null).when(objectMapper).valueToTree(any());
        
        // [Thực thi - Act]
        // Gọi hàm lấy vị trí công việc theo phòng ban
        Map<Long, JsonNode> result = jobService.getJobPositionsByDepartmentId(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống xử lý lỗi mapping an toàn và trả về danh sách rỗng thay vì gây lỗi ứng dụng
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-008: getJobPositionByIdSimple - null body response")
    void testGetJobPositionByIdSimple_NullBody() {
        // [Chuẩn bị - Prepare]
        // Thiết lập phản hồi API có body hoàn toàn trống (null)
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(null));
        
        // [Thực thi - Act]
        // Gọi hàm truy vấn đơn giản
        ResponseEntity<JsonNode> result = jobService.getJobPositionByIdSimple(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống báo lỗi 404 NOT FOUND do không nhận được nội dung từ phản hồi
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-010: getJobPositionByIdSimple - body without data")
    void testGetJobPositionByIdSimple_BodyNoData() {
        // [Chuẩn bị - Prepare]
        // Thiết lập phản hồi API có body JSON hợp lệ nhưng thiếu trường "data" bên trong
        ObjectNode bodyNoData = objectMapper.createObjectNode();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(bodyNoData));
        
        // [Thực thi - Act]
        // Thực hiện truy vấn thông tin
        ResponseEntity<JsonNode> result = jobService.getJobPositionByIdSimple(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận trả về lỗi 404 NOT FOUND vì không bóc tách được trường dữ liệu cần thiết
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-009: getJobPositionById - handle null response")
    void testGetJobPositionById_NullResponse() {
        // [Chuẩn bị - Prepare]
        // Mock restTemplate trả về một ResponseEntity null hoàn toàn
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));
        
        // [Thực thi - Act]
        // Gọi hàm lấy thông tin chi tiết vị trí công việc
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "token");

        // [Kiểm tra - Assert]
        // Hệ thống phải xử lý an toàn và trả về mã lỗi 404
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-011: getJobPositionIdsByDepartmentId - handle null response")
    void testGetJobPositionIdsByDepartmentId_NullResponse() {
        // [Chuẩn bị - Prepare]
        // Giả lập tình huống phản hồi API rỗng khi lấy danh sách ID
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));
        
        // [Thực thi - Act]
        // Thực hiện lấy danh sách ID
        List<Long> ids = jobService.getJobPositionIdsByDepartmentId(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận kết quả thu được là một danh sách rỗng thay vì gây lỗi NullPointerException
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-012: getJobPositionById - API Down")
    void testGetJobPositionById_ApiDown() {
        // [Chuẩn bị - Prepare]
        // Giả lập kịch bản Job Service bị sập (ném ngoại lệ RuntimeException)
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));
        
        // [Thực thi - Act]
        // Thực hiện gọi hàm lấy chi tiết vị trí công việc
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bắt lỗi ngoại lệ và trả về mã lỗi 500 INTERNAL_SERVER_ERROR
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("JS-TC-019: getJobPositionIdsByDepartmentId - API Down")
    void testGetJobPositionIdsByDepartmentId_ApiDown() {
        // [Chuẩn bị - Prepare]
        // Thiết lập mock để ném lỗi khi truy cập API lấy danh sách ID
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));
        
        // [Thực thi - Act]
        // Thực hiện lấy danh sách ID
        List<Long> ids = jobService.getJobPositionIdsByDepartmentId(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận trả về danh sách rỗng để đảm bảo tính sẵn sàng của ứng dụng khi một phần hệ thống lỗi
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-020: getJobPositionByIdSimple - API Down")
    void testGetJobPositionByIdSimple_ApiDown() {
        // [Chuẩn bị - Prepare]
        // Giả lập lỗi kết nối trong phương thức truy vấn đơn giản
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("API Down"));
        
        // [Thực thi - Act]
        // Gọi hàm truy vấn đơn giản
        ResponseEntity<JsonNode> result = jobService.getJobPositionByIdSimple(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận trả về mã lỗi 500 phù hợp
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("JS-TC-013: getJobPositionsByIds - null input")
    void testGetJobPositionsByIds_NullInput() {
        // [Chuẩn bị - Prepare]
        // Không cần chuẩn bị

        // [Thực thi - Act]
        // Gọi hàm lấy danh sách vị trí công việc với đầu vào là danh sách ID null
        Map<Long, JsonNode> result = jobService.getJobPositionsByIdsSimple(null, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống xử lý đầu vào null và trả về Map rỗng
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-021: getJobPositionsByIds - empty list input")
    void testGetJobPositionsByIds_EmptyList() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra với danh sách trống

        // [Thực thi - Act]
        // Thực hiện truy vấn với danh sách IDs rỗng
        Map<Long, JsonNode> result = jobService.getJobPositionsByIdsSimple(Collections.emptyList(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận kết quả trả về là Map rỗng
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-014: getJobPositionIds - handle sub-page exception")
    void getJobPositionIds_WithSubPageException_ShouldHandleGracefully() {
        // [Chuẩn bị - Prepare]
        // 1. Trang 1 trả về thành công với ID 101 và báo hiệu có trang tiếp theo
        PaginationDTO p1 = new PaginationDTO();
        p1.setResult(List.of(Map.of("id", 101)));
        Meta m1 = new Meta(); m1.setPages(2); p1.setMeta(m1);
        Response<PaginationDTO> b1 = new Response<>(); b1.setData(p1);
        when(restTemplate.exchange(contains("page=1"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(b1));
        
        // 2. Trang 2 gặp sự cố kết nối và ném lỗi
        when(restTemplate.exchange(contains("page=2"), any(), any(), any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException());
        
        // [Thực thi - Act]
        // Thực hiện lấy toàn bộ danh sách IDs của phòng ban
        List<Long> result = jobService.getJobPositionIdsByDepartmentId(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn giữ lại và trả về được dữ liệu đã lấy thành công ở trang 1 (ID 101) thay vì trả về rỗng hoàn toàn
        assertThat(result).contains(101L);
    }

    @Test
    @DisplayName("JS-TC-015: getJobPositionById - empty token")
    void testGetJobPositionById_EmptyToken() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra validation với token rỗng

        // [Thực thi - Act]
        // Gọi hàm lấy chi tiết vị trí công việc với token rỗng
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "");

        // [Kiểm tra - Assert]
        // Xác nhận trả về mã lỗi 401 UNAUTHORIZED
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JS-TC-016: getJobPositionIdsByDepartmentId - null token")
    void testGetJobPositionIdsByDepartmentId_NullToken() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra lấy IDs với token null

        // [Thực thi - Act]
        // Thực hiện lấy danh sách ID
        List<Long> result = jobService.getJobPositionIdsByDepartmentId(1L, null);

        // [Kiểm tra - Assert]
        // Xác nhận kết quả trả về là danh sách rỗng do không thể thực hiện truy vấn mà không có token
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-017: getJobPositionIdsByDepartmentId - empty token")
    void testGetJobPositionIdsByDepartmentId_EmptyToken() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra lấy IDs với token rỗng

        // [Thực thi - Act]
        // Thực hiện lấy danh sách ID
        List<Long> result = jobService.getJobPositionIdsByDepartmentId(1L, "");

        // [Kiểm tra - Assert]
        // Xác nhận kết quả trả về là danh sách rỗng
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-018: getJobPositionsByDepartmentId - null token")
    void testGetJobPositionsByDepartmentId_NullToken() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra lấy Map vị trí công việc với token null

        // [Thực thi - Act]
        // Thực hiện lấy Map vị trí công việc
        Map<Long, JsonNode> result = jobService.getJobPositionsByDepartmentId(1L, null);

        // [Kiểm tra - Assert]
        // Xác nhận kết quả trả về là Map rỗng
        assertThat(result).isEmpty();
    }
}
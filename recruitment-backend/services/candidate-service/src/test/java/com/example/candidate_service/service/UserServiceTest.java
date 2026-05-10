package com.example.candidate_service.service;

import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho UserService (Candidate Service side) - Module 7.
 * 
 * Chiến lược:
 * - Sử dụng Mockito để mock RestTemplate (vì đây là Client Service).
 * - Đảm bảo cấu trúc Arrange - Act - Assert rõ ràng.
 * - Giữ nguyên các Test Case ID từ tài liệu SQA.
 * - Đạt 100% Branch Coverage.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests (Candidate Service)")
class UserServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(userService, "userServiceUrl", "http://localhost:8082");
    }

    @Test
    @DisplayName("US-TC-001: getEmployeeName - parse đúng tên nhân viên")
    void testGetEmployeeName_US_TC_001() {
        // [Chuẩn bị - Prepare]
        // 1. Khởi tạo dữ liệu mẫu cho nhân viên (ID 1, tên John Doe)
        Long employeeId = 1L;
        String token = "token";
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");

        // 2. Đóng gói dữ liệu vào đối tượng Response và ResponseEntity để giả lập phản hồi từ User Service
        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        // 3. Thiết lập mock cho restTemplate để trả về dữ liệu mẫu khi gọi API lấy thông tin nhân viên
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện gọi hàm lấy tên nhân viên thông qua UserService
        ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, token);

        // [Kiểm tra - Assert]
        // 1. Xác nhận kết quả trả về không null và trạng thái HTTP là 200 OK
        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 2. Xác nhận tên nhân viên trong body JSON trả về khớp đúng với dữ liệu mẫu ("John Doe")
        assertThat(result.getBody().get("name").asText()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("US-TC-002: getEmployeeName - trả 404 khi không có data")
    void testGetEmployeeName_NoData_US_TC_002() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập phản hồi API thành công (200 OK) nhưng phần dữ liệu (data) bên trong là null
        Response<Map<String, Object>> body = new Response<>();
        body.setData(null);

        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        // 2. Cấu hình Mockito để trả về phản hồi rỗng này khi gọi API
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện truy vấn tên nhân viên
        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống xử lý trường hợp data null bằng cách trả về mã trạng thái 404 NOT FOUND
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US-TC-003: getEmployeeNames - parse đúng list tên")
    void testGetEmployeeNames_US_TC_003() {
        // [Chuẩn bị - Prepare]
        // 1. Khởi tạo danh sách mẫu gồm 2 nhân viên (John và Jane)
        List<Map<String, Object>> data = List.of(Map.of("id", 1, "name", "John"), Map.of("id", 2, "name", "Jane"));
        Response<List<Map<String, Object>>> body = new Response<>();
        body.setData(data);

        ResponseEntity<Response<List<Map<String, Object>>>> mockedResponse = 
                (ResponseEntity<Response<List<Map<String, Object>>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        // 2. Mock API trả về danh sách nhân viên khi được gọi
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Gọi hàm lấy danh sách tên cho các ID 1 và 2
        ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L, 2L), "token");

        // [Kiểm tra - Assert]
        // 1. Xác nhận trạng thái HTTP 200 OK
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 2. Xác nhận dữ liệu JSON trả về đã được map chính xác: ID là key và Name là value
        assertThat(result.getBody().get("1").asText()).isEqualTo("John");
        assertThat(result.getBody().get("2").asText()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("US-TC-004: getUserIdByEmail - trả employeeId hợp lệ")
    void testGetUserIdByEmail_US_TC_004() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập dữ liệu mẫu chứa trường employeeId = 100
        Map<String, Object> data = Map.of("employeeId", 100);
        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        // 2. Mock API tìm kiếm người dùng theo email
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện lấy ID người dùng bằng email
        Long result = userService.getUserIdByEmail("test@example.com");

        // [Kiểm tra - Assert]
        // Xác nhận hàm trả về đúng giá trị employeeId (100)
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("US-TC-005: getUserIdByEmail - trả id khi không có employeeId")
    void testGetUserIdByEmail_Id_US_TC_005() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập dữ liệu mẫu chỉ có trường 'id' = 200 (không có 'employeeId')
        Map<String, Object> data = Map.of("id", 200);
        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        // 2. Cấu hình mock API
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện lấy ID người dùng
        Long result = userService.getUserIdByEmail("test@example.com");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống lấy trường 'id' làm giá trị thay thế khi 'employeeId' vắng mặt
        assertThat(result).isEqualTo(200L);
    }

    @Test
    @DisplayName("US-TC-006: getUserIdByEmail - trả null khi không tìm thấy")
    void testGetUserIdByEmail_NotFound_US_TC_006() {
        // [Chuẩn bị - Prepare]
        // Giả lập tình huống API ném ngoại lệ (lỗi kết nối hoặc không tìm thấy bản ghi)
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Not found"));

        // [Thực thi - Act]
        // Thực hiện lấy ID bằng một email không tồn tại
        Long result = userService.getUserIdByEmail("none@example.com");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bắt lỗi ngoại lệ và trả về null một cách an toàn
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("US-TC-007: createEmployeeFromCandidate - thành công")
    void testCreateEmployeeFromCandidate_US_TC_007() {
        // [Chuẩn bị - Prepare]
        // 1. Giả lập phản hồi thành công từ User Service sau khi tạo nhân viên mới (employeeId = 500)
        ObjectNode data = objectMapper.createObjectNode().put("employeeId", 500);
        Response<JsonNode> body = new Response<>();
        body.setData(data);

        ResponseEntity<Response<JsonNode>> mockedResponse = 
                (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        // 2. Mock API POST để tạo nhân viên
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Gọi hàm tạo nhân viên từ thông tin ứng viên
        ResponseEntity<JsonNode> result = userService.createEmployeeFromCandidate(1L, "A", "E", "P", null, null, null, null, null, null, 1L, 1L, "S", "T");

        // [Kiểm tra - Assert]
        // 1. Xác nhận trạng thái 200 OK
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 2. Xác nhận ID nhân viên mới được tạo là 500
        assertThat(result.getBody().get("employeeId").asInt()).isEqualTo(500);
    }

    @Test
    @DisplayName("US-TC-008: createEmployeeFromCandidate - trả status gốc khi lỗi data")
    void testCreateEmployeeFromCandidate_NoData_US_TC_008() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập tình huống API trả về lỗi logic (400 Bad Request) kèm body data null
        Response<JsonNode> body = new Response<>();
        body.setData(null);

        ResponseEntity<Response<JsonNode>> mockedResponse = 
                (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.status(400).body(body);

        // 2. Cấu hình mock cho phương thức POST
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        // [Thực thi - Act]
        // Thực hiện lệnh tạo nhân viên
        ResponseEntity<JsonNode> result = userService.createEmployeeFromCandidate(1L, "A", "E", "P", null, null, null, null, null, null, 1L, 1L, "S", "T");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống trả về mã lỗi 400 đúng như phản hồi từ User Service
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US-TC-009: getEmployeeName - null token")
    void testGetEmployeeName_NullToken() {
        // [Chuẩn bị - Prepare]
        // Không cần chuẩn bị mock vì kiểm tra ràng buộc đầu vào ngay tại Service

        // [Thực thi - Act]
        // Gọi hàm với token là null
        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, null);

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống từ chối thực hiện và trả về lỗi 401 UNAUTHORIZED
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("US-TC-015: getEmployeeName - empty token")
    void testGetEmployeeName_EmptyToken() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra validation cho chuỗi rỗng

        // [Thực thi - Act]
        // Thực hiện gọi hàm với token rỗng
        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "");

        // [Kiểm tra - Assert]
        // Xác nhận trả về lỗi 401 UNAUTHORIZED do thiếu thông tin xác thực
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("US-TC-016: getEmployeeNames - null token")
    void testGetEmployeeNames_NullToken() {
        // [Chuẩn bị - Prepare]
        // Kiểm tra danh sách nhân viên với token null

        // [Thực thi - Act]
        // Gọi hàm lấy danh sách tên với token null
        ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L), null);

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống yêu cầu xác thực bằng mã lỗi 401
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("US-TC-010: getEmployeeName - null response body")
    void testGetEmployeeName_NullResponseBody() {
        // [Chuẩn bị - Prepare]
        // Thiết lập API trả về ResponseEntity có body hoàn toàn null
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

        // [Thực thi - Act]
        // Thực hiện truy vấn tên nhân viên
        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống xử lý an toàn lỗi body null và trả về 404 NOT FOUND
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US-TC-017: getEmployeeNames - null response body")
    void testGetEmployeeNames_NullResponseBody() {
        // [Chuẩn bị - Prepare]
        // Thiết lập API trả về body null khi truy vấn danh sách
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

        // [Thực thi - Act]
        // Thực hiện gọi hàm lấy danh sách tên
        ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn trả về 200 OK nhưng dữ liệu bên trong sẽ được handle là Map rỗng theo logic nghiệp vụ
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK); 
    }

    @Test
    @DisplayName("US-TC-018: getUserIdByEmail - null response body")
    void testGetUserIdByEmail_NullResponseBody() {
        // [Chuẩn bị - Prepare]
        // Mock API trả về phản hồi rỗng
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

        // [Thực thi - Act]
        // Gọi hàm tìm kiếm ID theo email
        Long result = userService.getUserIdByEmail("test@example.com");

        // [Kiểm tra - Assert]
        // Xác nhận kết quả trả về là null do không thể bóc tách dữ liệu
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("US-TC-011: getEmployeeName - handle null name in data")
    void testGetEmployeeName_NullNameInBody() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập data có tồn tại key "name" nhưng giá trị là null
        Map<String, Object> dataNullName = new HashMap<>();
        dataNullName.put("name", null);
        Response<Map<String, Object>> respNullName = new Response<>();
        respNullName.setData(dataNullName);

        // 2. Mock API trả về dữ liệu này
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respNullName));

        // [Thực thi - Act]
        // Truy vấn tên nhân viên
        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống trả về 404 NOT FOUND vì không có tên hợp lệ để trả về
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US-TC-012: getEmployeeNames - null ID in list")
    void testGetEmployeeNames_NullIdInList() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập danh sách nhân viên trong đó có bản ghi chứa ID bị null
        Map<String, Object> emp1 = new HashMap<>();
        emp1.put("id", null);
        emp1.put("name", "John");

        Response<List<Map<String, Object>>> respListNull = new Response<>();
        respListNull.setData(List.of(emp1));

        // 2. Mock API trả về danh sách này
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respListNull));

        // [Thực thi - Act]
        // Gọi hàm lấy danh sách tên
        ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống loại bỏ các bản ghi không có ID và trả về kết quả rỗng
        assertThat(result.getBody().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("US-TC-019: getEmployeeNames - null Name in list")
    void testGetEmployeeNames_NullNameInList() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập bản ghi nhân viên có ID hợp lệ nhưng tên bị null
        Map<String, Object> emp2 = new HashMap<>();
        emp2.put("id", 2);
        emp2.put("name", null);

        Response<List<Map<String, Object>>> respListNull = new Response<>();
        respListNull.setData(List.of(emp2));

        // 2. Cấu hình mock
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respListNull));

        // [Thực thi - Act]
        // Thực hiện truy vấn danh sách
        ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(2L), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bỏ qua các bản ghi có tên null và trả về body JSON trống
        assertThat(result.getBody().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("US-TC-013: getUserIdByEmail - handle various ID types")
    void testGetUserIdByEmail_VariousIdTypes() {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập data "bẩn": employeeId là chuỗi không phải số, nhưng id là số hợp lệ (123)
        Map<String, Object> dataU = new HashMap<>();
        dataU.put("employeeId", "not_a_number");
        dataU.put("id", 123);
        Response<Map<String, Object>> respU = new Response<>();
        respU.setData(dataU);

        // 2. Mock API trả về tập dữ liệu hỗn hợp này
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respU));

        // [Thực thi - Act]
        // Thực hiện tìm ID theo email
        Long result = userService.getUserIdByEmail("a@b.com");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bỏ qua employeeId lỗi định dạng và fallback sang lấy trường 'id' thành công
        assertThat(result).isEqualTo(123L);
    }

    @Test
    @DisplayName("US-TC-014: getEmployeeName - API Down")
    void testGetEmployeeName_ApiDown() {
        // [Chuẩn bị - Prepare]
        // Giả lập User Service bị sập (ném lỗi RuntimeException)
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));

        // [Thực thi - Act]
        // Thực hiện gọi hàm lấy tên nhân viên
        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bắt lỗi kết nối và chuyển đổi sang lỗi 500 INTERNAL_SERVER_ERROR
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("US-TC-020: getUserIdByEmail - API Down")
    void testGetUserIdByEmail_ApiDown() {
        // [Chuẩn bị - Prepare]
        // Giả lập User Service không phản hồi
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));

        // [Thực thi - Act]
        // Tìm ID theo email trong điều kiện hệ thống lỗi
        Long result = userService.getUserIdByEmail("test@example.com");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống trả về null để báo hiệu không tìm thấy/lỗi thay vì crash ứng dụng
        assertThat(result).isNull();
    }
}
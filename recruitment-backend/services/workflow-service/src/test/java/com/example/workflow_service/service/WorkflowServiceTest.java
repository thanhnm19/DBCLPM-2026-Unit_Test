package com.example.workflow_service.service;

import com.example.workflow_service.dto.PaginationDTO;
import com.example.workflow_service.dto.workflow.CreateStepDTO;
import com.example.workflow_service.dto.workflow.CreateWorkflowDTO;
import com.example.workflow_service.dto.workflow.UpdateWorkflowDTO;
import com.example.workflow_service.dto.workflow.WorkflowResponseDTO;
import com.example.workflow_service.exception.CustomException;
import com.example.workflow_service.exception.IdInvalidException;
import com.example.workflow_service.model.Workflow;
import com.example.workflow_service.repository.WorkflowRepository;
import com.example.workflow_service.repository.WorkflowStepRepository;
import com.example.workflow_service.utils.SecurityUtil;
import com.example.workflow_service.utils.enums.WorkflowType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * =============================================================
 * Unit Test: WorkflowService — Quy trình Phê duyệt
 * =============================================================
 *
 * Mục tiêu:
 *   - Phủ Branch Coverage cấp 2 (mọi nhánh if/else trong source code)
 *   - Mỗi test bắt một lỗi cụ thể: nếu implementation sai → test FAIL
 *   - Expected output căn cứ vào ĐẶC TẢ, không căn cứ vào source code hiện tại
 *
 * Sơ đồ nhánh cần phủ:
 *
 * create():
 *   [N1] tên đã tồn tại → CustomException
 *   [N2] steps = null   → bỏ qua validation, tạo workflow
 *   [N3] steps rỗng     → bỏ qua validation, tạo workflow
 *   [N4] positionId null trong steps → bỏ qua trong danh sách, không gọi userService
 *   [N5] positionId không có trong map → CustomException
 *   [N6] hierarchyOrder tăng dần (sai) → CustomException
 *   [N7] hierarchyOrder giảm dần (đúng) → tạo thành công + steps được lưu
 *
 * getById():
 *   [N8] ID tồn tại → trả về DTO đúng (đủ trường)
 *   [N9] ID không tồn tại → IdInvalidException
 *
 * getAll():
 *   [N10] type=null → không lọc type
 *   [N11] type!=null → lọc đúng type
 *   [N12] isActive filter hoạt động
 *
 * update():
 *   [N13] ID không tồn tại → IdInvalidException
 *   [N14] đổi tên = tên hiện tại → không kiểm tra trùng
 *   [N15] đổi tên = tên khác đã tồn tại → CustomException, DB không đổi
 *   [N16] đổi tên = tên mới hợp lệ → DB ghi tên mới
 *   [N17] description/type/isActive/departmentId null → không overwrite
 *   [N18] isActive=false → DB cập nhật
 *   [N19] updatedBy ghi lại user hiện tại
 *
 * delete():
 *   [N20] ID không tồn tại → IdInvalidException
 *   [N21] ID tồn tại → soft delete: isActive=false, record vẫn còn
 *
 * CheckDB: Sau mỗi thao tác ghi, truy vấn lại DB để xác minh
 * Rollback: @Transactional đảm bảo DB sạch sau mỗi test
 * =============================================================
 */
@SuppressWarnings("unused")
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@ExtendWith(com.example.workflow_service.TestResultLogger.class)
@DisplayName("WorkflowService - Branch Coverage Cấp 2")
class WorkflowServiceTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private WorkflowStepRepository workflowStepRepository;

    // --- Mock external dependencies ---
    @MockitoBean private UserService userService;
    @MockitoBean private CandidateService candidateService;
    @MockitoBean private com.example.workflow_service.messaging.NotificationProducer notificationProducer;
    @MockitoBean private com.example.workflow_service.messaging.RecruitmentWorkflowProducer workflowProducer;
    @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean private RestTemplate restTemplate;

    private MockedStatic<SecurityUtil> mockedSecurityUtil;
    private static final Long CREATOR_USER_ID = 42L; // User đang đăng nhập trong test

    /** Workflow đã tồn tại sẵn — dùng cho các test update/delete/getById */
    private Workflow existingWorkflow;

    @BeforeEach
    void setUp() {
        // Giả lập người dùng ID=42 đang đăng nhập
        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::extractEmployeeId).thenReturn(CREATOR_USER_ID);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("test-token"));

        // Default mock: hierarchyOrder hợp lệ (giảm dần Staff→Manager: 3→1)
        when(userService.getPositionHierarchyOrdersByIds(anyList(), anyString()))
                .thenReturn(Map.of(10L, 3, 20L, 1));
        when(userService.getPositionNamesByIds(anyList(), anyString()))
                .thenReturn(Map.of());

        // Workflow mẫu trong DB
        existingWorkflow = new Workflow();
        existingWorkflow.setName("Workflow Phê Duyệt Tuyển Dụng");
        existingWorkflow.setType(WorkflowType.REQUEST);
        existingWorkflow.setDepartmentId(10L);
        existingWorkflow.setIsActive(true);
        existingWorkflow = workflowRepository.save(existingWorkflow);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtil.close();
    }

    // ================================================================
    // NHÓM 1: create()
    // ================================================================

    /**
     * Test Case ID: WF-TC01
     * Nhánh [N1]: Tên workflow đã tồn tại → phải ném CustomException
     *
     * Bug bị bắt: Nếu thiếu kiểm tra tên trùng, workflow sẽ bị tạo
     * trùng lặp vi phạm ràng buộc unique của UNIQUE constraint.
     *
     * CheckDB: Số lượng workflow phải không tăng sau khi exception
     */
    @Test
    @DisplayName("[WF-TC01][N1] create() - Tên trùng → CustomException; DB không tạo thêm")
    void tc01_create_duplicateName_throwsCustomException() {
        // INPUT: Tên đã tồn tại trong DB (do setUp tạo)
        CreateWorkflowDTO dto = buildCreateDto("Workflow Phê Duyệt Tuyển Dụng", WorkflowType.REQUEST, 5L);

        long countBefore = workflowRepository.count();

        // EXECUTE: phải ném CustomException
        CustomException ex = assertThrows(CustomException.class,
                () -> workflowService.create(dto),
                "BUG: Thiếu kiểm tra tên trùng → workflow bị tạo duplicate");

        // VERIFY exception message đúng đặc tả
        assertTrue(ex.getMessage().contains("Tên workflow đã tồn tại"),
                "BUG: Message exception không đúng đặc tả");

        // CHECK DB: không có workflow nào được thêm
        assertEquals(countBefore, workflowRepository.count(),
                "BUG: DB đã tạo thêm workflow dù tên bị trùng");
    }

    /**
     * Test Case ID: WF-TC02
     * Nhánh [N2]: Steps = null → bỏ qua validation, tạo workflow thành công
     *
     * Bug bị bắt: Nếu code không xử lý null steps → NullPointerException
     *
     * CheckDB: Workflow được lưu, createdBy = user đang đăng nhập, isActive = true
     */
    @Test
    @DisplayName("[WF-TC02][N2] create() - Steps=null → workflow được tạo, không có step nào trong DB")
    void tc02_create_nullSteps_createsWorkflowWithoutSteps() {
        CreateWorkflowDTO dto = buildCreateDto("Workflow Không Bước", WorkflowType.REQUEST, 5L);
        dto.setSteps(null);

        // EXECUTE
        WorkflowResponseDTO result = workflowService.create(dto);

        // VERIFY: DTO phải có ID hợp lệ
        assertNotNull(result.getId(), "BUG: ID không được sinh ra");
        assertEquals("Workflow Không Bước", result.getName(), "BUG: Tên bị sai sau khi lưu");

        // CHECK DB: workflow tồn tại với đúng thuộc tính
        Workflow saved = workflowRepository.findById(result.getId()).orElseThrow();
        assertTrue(saved.getIsActive(),
                "BUG: isActive phải mặc định là true, không phải false");
        assertEquals(CREATOR_USER_ID, saved.getCreatedBy(),
                "BUG: createdBy không được ghi là user đang đăng nhập");
        assertNull(saved.getUpdatedBy(),
                "BUG: updatedBy phải null khi mới tạo (chưa có ai update)");

        // CHECK DB: không có steps nào
        long stepCount = workflowStepRepository.findAll().stream()
                .filter(s -> s.getWorkflow().getId().equals(result.getId()))
                .count();
        assertEquals(0L, stepCount, "BUG: Có steps được tạo dù steps=null");
    }

    /**
     * Test Case ID: WF-TC03
     * Nhánh [N3]: Steps là danh sách rỗng → bỏ qua validation, tạo workflow không có steps
     *
     * Bug bị bắt: Nếu code xử lý steps=[] giống steps=null thì đúng;
     * nếu không → vẫn cố tạo steps rỗng hoặc lỗi.
     */
    @Test
    @DisplayName("[WF-TC03][N3] create() - Steps=[] rỗng → workflow được tạo, không có step nào")
    void tc03_create_emptySteps_createsWorkflow() {
        CreateWorkflowDTO dto = buildCreateDto("Workflow Steps Rỗng", WorkflowType.OFFER, 3L);
        dto.setSteps(List.of()); // Danh sách rỗng

        WorkflowResponseDTO result = workflowService.create(dto);

        assertNotNull(result.getId(), "BUG: ID không được sinh ra với steps rỗng");

        long stepCount = workflowStepRepository.findAll().stream()
                .filter(s -> s.getWorkflow().getId().equals(result.getId()))
                .count();
        assertEquals(0L, stepCount, "BUG: Có steps trong DB dù steps=[]");
    }

    /**
     * Test Case ID: WF-TC04
     * Nhánh [N5]: Position ID không có trong map hierarchyOrder → CustomException
     *
     * Bug bị bắt: Nếu code không kiểm tra null từ map.get() → NullPointerException
     * hoặc bỏ qua validation → workflow sai cấu trúc được tạo.
     *
     * CheckDB: Không có workflow nào được tạo
     */
    @Test
    @DisplayName("[WF-TC04][N5] create() - PositionId không có trong map → CustomException")
    void tc04_create_positionIdNotInHierarchyMap_throwsCustomException() {
        // Mock: trả về map rỗng → positionId 99L sẽ không có trong map
        when(userService.getPositionHierarchyOrdersByIds(anyList(), anyString()))
                .thenReturn(Map.of());

        CreateWorkflowDTO dto = buildCreateDto("Workflow Position Lạ", WorkflowType.REQUEST, 5L);
        dto.setSteps(List.of(buildStep(1, 99L)));

        long countBefore = workflowRepository.count();

        CustomException ex = assertThrows(CustomException.class,
                () -> workflowService.create(dto),
                "BUG: Không ném exception khi positionId không có hierarchyOrder");

        assertTrue(ex.getMessage().contains("Không tìm thấy hierarchyOrder"),
                "BUG: Message exception không đúng đặc tả");

        // CHECK DB: không có workflow mới
        assertEquals(countBefore, workflowRepository.count(),
                "BUG: Workflow được tạo dù positionId không hợp lệ");
    }

    /**
     * Test Case ID: WF-TC05
     * Nhánh [N6]: HierarchyOrder tăng dần (step sau có level thấp hơn step trước) → CustomException
     *
     * Đặc tả: Workflow phải đi từ cấp thấp → cấp cao (hierarchyOrder giảm dần).
     * CEO=1, Staff=4. Nếu step1(order=1) → step2(order=3): TĂNG → SAI.
     *
     * Bug bị bắt: Nếu điều kiện so sánh bị sai dấu (< thay vì >) → không phát hiện sai thứ tự.
     */
    @Test
    @DisplayName("[WF-TC05][N6] create() - HierarchyOrder tăng dần (sai thứ tự) → CustomException")
    void tc05_create_hierarchyOrderIncreasing_throwsCustomException() {
        // position10=CEO(order=1), position20=Staff(order=3)
        // step1 dùng CEO(1) → step2 dùng Staff(3): TĂNG → VI PHẠM đặc tả
        when(userService.getPositionHierarchyOrdersByIds(anyList(), anyString()))
                .thenReturn(Map.of(10L, 1, 20L, 3));

        CreateWorkflowDTO dto = buildCreateDto("Workflow Sai Thứ Tự", WorkflowType.REQUEST, 5L);
        dto.setSteps(List.of(buildStep(1, 10L), buildStep(2, 20L)));

        CustomException ex = assertThrows(CustomException.class,
                () -> workflowService.create(dto),
                "BUG: Không phát hiện hierarchyOrder tăng dần (vi phạm quy tắc leo cấp)");

        assertTrue(ex.getMessage().contains("Thứ tự hierarchy không hợp lệ"),
                "BUG: Message không đề cập đến lỗi thứ tự hierarchy");
    }

    /**
     * Test Case ID: WF-TC06
     * Nhánh [N6]: HierarchyOrder bằng nhau (step2 = step1) → PHẢI HỢP LỆ (chỉ reject khi TĂNG)
     *
     * Đặc tả: điều kiện reject là `hierarchyOrder > previousHierarchyOrder`.
     * Khi bằng nhau (=) → hợp lệ.
     *
     * Bug bị bắt: Nếu code dùng `>=` thay vì `>` → trường hợp bằng bị reject sai.
     */
    @Test
    @DisplayName("[WF-TC06][N6] create() - HierarchyOrder bằng nhau → hợp lệ, không ném exception")
    void tc06_create_hierarchyOrderEqual_isValid() {
        // position10 và position20 cùng hierarchyOrder=2
        when(userService.getPositionHierarchyOrdersByIds(anyList(), anyString()))
                .thenReturn(Map.of(10L, 2, 20L, 2));

        CreateWorkflowDTO dto = buildCreateDto("Workflow Cùng Cấp", WorkflowType.REQUEST, 5L);
        dto.setSteps(List.of(buildStep(1, 10L), buildStep(2, 20L)));

        // PHẢI không ném exception
        assertDoesNotThrow(() -> workflowService.create(dto),
                "BUG: HierarchyOrder bằng nhau bị reject sai - có thể đang dùng >= thay vì >");
    }

    /**
     * Test Case ID: WF-TC07
     * Nhánh [N7]: Steps hợp lệ (hierarchyOrder giảm dần) → workflow và steps được lưu đúng
     *
     * Bug bị bắt:
     *   - Steps không được lưu vào DB
     *   - stepOrder bị sai
     *   - approverPositionId bị sai
     *   - isActive của step không được set true
     *
     * CheckDB: Truy vấn từng step để xác minh thuộc tính
     */
    @Test
    @DisplayName("[WF-TC07][N7] create() - Steps hợp lệ → workflow + steps được lưu đúng trong DB")
    void tc07_create_validSteps_persistsWorkflowAndStepsCorrectly() {
        // Staff(order=3) → Manager(order=1): GIẢM → HỢP LỆ
        when(userService.getPositionHierarchyOrdersByIds(anyList(), anyString()))
                .thenReturn(Map.of(10L, 3, 20L, 1));

        CreateWorkflowDTO dto = buildCreateDto("Workflow 2 Bước", WorkflowType.REQUEST, 5L);
        dto.setSteps(List.of(buildStep(1, 10L), buildStep(2, 20L)));

        WorkflowResponseDTO result = workflowService.create(dto);

        // CHECK DB: workflow tồn tại
        Workflow wf = workflowRepository.findById(result.getId())
                .orElseThrow(() -> new AssertionError("BUG: Workflow không được lưu vào DB"));

        // CHECK DB: đúng 2 steps được lưu
        List<com.example.workflow_service.model.WorkflowStep> steps = workflowStepRepository.findAll()
                .stream()
                .filter(s -> s.getWorkflow().getId().equals(wf.getId()))
                .sorted(java.util.Comparator.comparing(
                        com.example.workflow_service.model.WorkflowStep::getStepOrder))
                .toList();

        assertEquals(2, steps.size(), "BUG: Không lưu đúng số lượng steps");
        // Xác minh step 1
        assertEquals(1, steps.get(0).getStepOrder(), "BUG: stepOrder của step 1 sai");
        assertEquals(10L, steps.get(0).getApproverPositionId(), "BUG: approverPositionId step 1 sai");
        assertTrue(steps.get(0).getIsActive(), "BUG: isActive của step phải là true");
        // Xác minh step 2
        assertEquals(2, steps.get(1).getStepOrder(), "BUG: stepOrder của step 2 sai");
        assertEquals(20L, steps.get(1).getApproverPositionId(), "BUG: approverPositionId step 2 sai");
    }

    // ================================================================
    // NHÓM 2: getById()
    // ================================================================

    /**
     * Test Case ID: WF-TC08
     * Nhánh [N8]: ID tồn tại → trả về DTO với đủ thông tin
     *
     * Bug bị bắt: Mapping thiếu trường (id, name, type, departmentId, isActive)
     */
    @Test
    @DisplayName("[WF-TC08][N8] getById() - ID tồn tại → DTO có đầy đủ thông tin đúng")
    void tc08_getById_existingId_returnsCorrectDto() {
        WorkflowResponseDTO result = workflowService.getById(existingWorkflow.getId());

        assertAll("BUG: Mapping DTO thiếu hoặc sai trường",
                () -> assertEquals(existingWorkflow.getId(), result.getId(), "id sai"),
                () -> assertEquals("Workflow Phê Duyệt Tuyển Dụng", result.getName(), "name sai"),
                () -> assertEquals(WorkflowType.REQUEST, result.getType(), "type sai"),
                () -> assertEquals(10L, result.getDepartmentId(), "departmentId sai"),
                () -> assertTrue(result.getIsActive(), "isActive sai")
        );
    }

    /**
     * Test Case ID: WF-TC09
     * Nhánh [N9]: ID không tồn tại → IdInvalidException
     *
     * Bug bị bắt: Nếu code trả về null thay vì ném exception → null pointer ở caller
     */
    @Test
    @DisplayName("[WF-TC09][N9] getById() - ID không tồn tại → IdInvalidException")
    void tc09_getById_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> workflowService.getById(99999L),
                "BUG: Trả về null thay vì ném IdInvalidException khi ID không tồn tại");
    }

    // ================================================================
    // NHÓM 3: getAll()
    // ================================================================

    /**
     * Test Case ID: WF-TC10
     * Nhánh [N10]: type = null → không lọc loại, trả về cả REQUEST lẫn OFFER
     *
     * Bug bị bắt: Nếu filter type=null vẫn bị áp dụng → mất kết quả
     */
    @Test
    @SuppressWarnings("unchecked") // Java type erasure: PaginationDTO.getResult() trả về Object, cast an toàn vì service luôn trả về List<WorkflowResponseDTO>
    @DisplayName("[WF-TC10][N10] getAll() - type=null → trả về cả REQUEST và OFFER")
    void tc10_getAll_nullType_returnsAllTypes() {
        // Tạo thêm workflow OFFER
        Workflow offerW = new Workflow();
        offerW.setName("Workflow OFFER Test");
        offerW.setType(WorkflowType.OFFER);
        offerW.setIsActive(true);
        workflowRepository.save(offerW);

        PaginationDTO result = workflowService.getAll(null, null, null, null, PageRequest.of(0, 10));

        // Phải tìm thấy cả 2 loại trong kết quả
        List<WorkflowResponseDTO> list = (List<WorkflowResponseDTO>) result.getResult();
        boolean hasRequest = list.stream().anyMatch(w -> w.getType() == WorkflowType.REQUEST);
        boolean hasOffer   = list.stream().anyMatch(w -> w.getType() == WorkflowType.OFFER);

        assertTrue(hasRequest, "BUG: Không có workflow REQUEST trong kết quả khi type=null");
        assertTrue(hasOffer,   "BUG: Không có workflow OFFER trong kết quả khi type=null");
    }

    /**
     * Test Case ID: WF-TC11
     * Nhánh [N11]: type = OFFER → chỉ trả về workflow OFFER, không lẫn REQUEST
     *
     * Bug bị bắt: Filter type không hoạt động → trả về tất cả loại
     */
    @Test
    @SuppressWarnings("unchecked") // Java type erasure: PaginationDTO.getResult() trả về Object, cast an toàn vì service luôn trả về List<WorkflowResponseDTO>
    @DisplayName("[WF-TC11][N11] getAll() - type=OFFER → chỉ trả về OFFER, không có REQUEST")
    void tc11_getAll_filterByOffer_excludesRequest() {
        Workflow offerW = new Workflow();
        offerW.setName("Workflow OFFER");
        offerW.setType(WorkflowType.OFFER);
        offerW.setIsActive(true);
        workflowRepository.save(offerW);

        PaginationDTO result = workflowService.getAll(WorkflowType.OFFER, null, null, null,
                PageRequest.of(0, 10));
        List<WorkflowResponseDTO> list = (List<WorkflowResponseDTO>) result.getResult();

        assertFalse(list.isEmpty(), "BUG: Không tìm thấy workflow OFFER nào");
        assertTrue(list.stream().allMatch(w -> w.getType() == WorkflowType.OFFER),
                "BUG: Kết quả lẫn workflow REQUEST khi filter type=OFFER");
    }

    /**
     * Test Case ID: WF-TC12
     * Nhánh [N12]: isActive = false → chỉ trả về workflow không active
     *
     * Bug bị bắt: Filter isActive không hoạt động → trả về cả active lẫn inactive
     */
    @Test
    @SuppressWarnings("unchecked") // Java type erasure: PaginationDTO.getResult() trả về Object, cast an toàn vì service luôn trả về List<WorkflowResponseDTO>
    @DisplayName("[WF-TC12][N12] getAll() - isActive=false → chỉ trả về workflow bị deactivate")
    void tc12_getAll_filterByInactive_excludesActive() {
        // Tạo workflow inactive
        Workflow inactiveW = new Workflow();
        inactiveW.setName("Workflow Đã Ẩn");
        inactiveW.setType(WorkflowType.REQUEST);
        inactiveW.setIsActive(false);
        workflowRepository.save(inactiveW);

        PaginationDTO result = workflowService.getAll(null, false, null, null, PageRequest.of(0, 10));
        List<WorkflowResponseDTO> list = (List<WorkflowResponseDTO>) result.getResult();

        assertFalse(list.isEmpty(), "BUG: Không tìm thấy workflow inactive nào");
        assertTrue(list.stream().allMatch(w -> !w.getIsActive()),
                "BUG: Kết quả lẫn workflow active khi filter isActive=false");
    }

    // ================================================================
    // NHÓM 4: update()
    // ================================================================

    /**
     * Test Case ID: WF-TC13
     * Nhánh [N13]: ID không tồn tại → IdInvalidException
     */
    @Test
    @DisplayName("[WF-TC13][N13] update() - ID không tồn tại → IdInvalidException")
    void tc13_update_nonExistentId_throwsIdInvalidException() {
        UpdateWorkflowDTO dto = new UpdateWorkflowDTO();
        dto.setName("Tên Mới");

        assertThrows(IdInvalidException.class,
                () -> workflowService.update(77777L, dto),
                "BUG: Không ném exception khi update ID không tồn tại");
    }

    /**
     * Test Case ID: WF-TC14
     * Nhánh [N14]: Tên mới = tên hiện tại → KHÔNG kiểm tra trùng, không ném exception
     *
     * Bug bị bắt: Nếu code dùng `.equals()` sai chiều → ném exception sai khi đặt cùng tên
     */
    @Test
    @DisplayName("[WF-TC14][N14] update() - Tên mới = tên cũ → không ném exception")
    void tc14_update_sameNameAsCurrent_succeeds() {
        UpdateWorkflowDTO dto = new UpdateWorkflowDTO();
        dto.setName("Workflow Phê Duyệt Tuyển Dụng"); // Cùng tên đang có

        assertDoesNotThrow(() -> workflowService.update(existingWorkflow.getId(), dto),
                "BUG: Ném exception cho tên không thay đổi - kiểm tra trùng sai logic");
    }

    /**
     * Test Case ID: WF-TC15
     * Nhánh [N15]: Tên mới đã bị workflow KHÁC dùng → CustomException, DB không đổi
     *
     * Bug bị bắt: Thiếu kiểm tra trùng tên khi update → tên bị ghi đè sai
     *
     * CheckDB: Tên của existingWorkflow trong DB phải không thay đổi
     */
    @Test
    @DisplayName("[WF-TC15][N15] update() - Tên đã bị workflow khác dùng → CustomException; tên cũ giữ nguyên")
    void tc15_update_nameAlreadyUsedByAnother_throwsCustomException() {
        // Tạo workflow thứ 2 để "chiếm" tên
        Workflow other = new Workflow();
        other.setName("Tên Đã Bị Chiếm");
        other.setType(WorkflowType.REQUEST);
        other.setIsActive(true);
        workflowRepository.save(other);

        UpdateWorkflowDTO dto = new UpdateWorkflowDTO();
        dto.setName("Tên Đã Bị Chiếm"); // Tên thuộc về `other`

        assertThrows(CustomException.class,
                () -> workflowService.update(existingWorkflow.getId(), dto),
                "BUG: Không ngăn đổi tên sang tên đã tồn tại của workflow khác");

        // CHECK DB: tên của existingWorkflow vẫn giữ nguyên
        String nameInDb = workflowRepository.findById(existingWorkflow.getId())
                .orElseThrow().getName();
        assertEquals("Workflow Phê Duyệt Tuyển Dụng", nameInDb,
                "BUG: Tên trong DB bị thay đổi dù update thất bại");
    }

    /**
     * Test Case ID: WF-TC16
     * Nhánh [N16]+[N17]+[N19]: Update hợp lệ — description mới, isActive=false, updatedBy ghi đúng
     *
     * Bug bị bắt:
     *   - description không được ghi vào DB
     *   - isActive không được cập nhật
     *   - updatedBy không được ghi là user đang đăng nhập
     *   - Trường null không bị overwrite (dto.getName()==null → tên không bị xóa)
     *
     * CheckDB: Truy vấn lại từng trường
     */
    @Test
    @DisplayName("[WF-TC16][N16-N19] update() - Các trường hợp lệ → DB cập nhật đúng; null-field không overwrite")
    void tc16_update_validFields_persistsAllChanges() {
        UpdateWorkflowDTO dto = new UpdateWorkflowDTO();
        dto.setName(null);             // null → không thay đổi tên hiện tại
        dto.setDescription("Mô tả mới sau review");
        dto.setIsActive(false);
        // type, departmentId để null để kiểm tra không bị overwrite

        workflowService.update(existingWorkflow.getId(), dto);

        // CHECK DB
        Workflow updated = workflowRepository.findById(existingWorkflow.getId()).orElseThrow();
        assertAll("BUG: Một hoặc nhiều trường không được cập nhật đúng",
                () -> assertEquals("Workflow Phê Duyệt Tuyển Dụng", updated.getName(),
                        "BUG: Tên bị thay đổi dù dto.getName()==null"),
                () -> assertEquals("Mô tả mới sau review", updated.getDescription(),
                        "BUG: description không được lưu vào DB"),
                () -> assertFalse(updated.getIsActive(),
                        "BUG: isActive không được đổi thành false"),
                () -> assertEquals(WorkflowType.REQUEST, updated.getType(),
                        "BUG: type bị thay đổi dù dto.getType()==null"),
                () -> assertEquals(CREATOR_USER_ID, updated.getUpdatedBy(),
                        "BUG: updatedBy không ghi lại user đang đăng nhập")
        );
    }

    // ================================================================
    // NHÓM 5: delete()
    // ================================================================

    /**
     * Test Case ID: WF-TC17
     * Nhánh [N20]: ID không tồn tại → IdInvalidException
     */
    @Test
    @DisplayName("[WF-TC17][N20] delete() - ID không tồn tại → IdInvalidException")
    void tc17_delete_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> workflowService.delete(55555L),
                "BUG: Không ném exception khi delete ID không tồn tại");
    }

    /**
     * Test Case ID: WF-TC18
     * Nhánh [N21]: ID hợp lệ → Soft delete: isActive=false, record phải VẪN CÒN trong DB
     *
     * Bug bị bắt:
     *   - Hard delete thay vì soft delete → record bị xóa khỏi DB
     *   - isActive không được set false → soft delete không có hiệu lực
     *
     * CheckDB: existsById vẫn true; isActive = false
     */
    @Test
    @DisplayName("[WF-TC18][N21] delete() - ID hợp lệ → Soft delete: isActive=false, record vẫn còn")
    void tc18_delete_existingId_softDeleteSetsInactiveAndKeepsRecord() {
        Long id = existingWorkflow.getId();
        assertTrue(existingWorkflow.getIsActive(), "Precondition: workflow phải active");

        workflowService.delete(id);

        // CHECK DB: vẫn tồn tại (không bị hard delete)
        assertTrue(workflowRepository.existsById(id),
                "BUG: Record bị xóa khỏi DB (hard delete) thay vì soft delete");

        // CHECK DB: isActive phải là false
        Boolean isActive = workflowRepository.findById(id).orElseThrow().getIsActive();
        assertFalse(isActive,
                "BUG: isActive vẫn là true sau khi delete → soft delete không hoạt động");
    }

    // ================================================================
    // HELPER
    // ================================================================

    private CreateWorkflowDTO buildCreateDto(String name, WorkflowType type, Long deptId) {
        CreateWorkflowDTO dto = new CreateWorkflowDTO();
        dto.setName(name);
        dto.setType(type);
        dto.setDepartmentId(deptId);
        return dto;
    }

    private CreateStepDTO buildStep(int order, Long positionId) {
        CreateStepDTO step = new CreateStepDTO();
        step.setStepOrder(order);
        step.setApproverPositionId(positionId);
        return step;
    }
}

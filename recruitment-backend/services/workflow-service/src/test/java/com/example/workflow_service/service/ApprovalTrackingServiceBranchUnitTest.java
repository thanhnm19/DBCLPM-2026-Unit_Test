package com.example.workflow_service.service;

import com.example.workflow_service.dto.Response;
import com.example.workflow_service.dto.approval.ApprovalTrackingResponseDTO;
import com.example.workflow_service.dto.approval.ApproveStepDTO;
import com.example.workflow_service.dto.approval.CreateApprovalTrackingDTO;
import com.example.workflow_service.messaging.NotificationProducer;
import com.example.workflow_service.messaging.RecruitmentWorkflowEvent;
import com.example.workflow_service.messaging.RecruitmentWorkflowProducer;
import com.example.workflow_service.model.ApprovalTracking;
import com.example.workflow_service.model.Workflow;
import com.example.workflow_service.model.WorkflowStep;
import com.example.workflow_service.repository.ApprovalTrackingRepository;
import com.example.workflow_service.repository.WorkflowRepository;
import com.example.workflow_service.repository.WorkflowStepRepository;
import com.example.workflow_service.utils.SecurityUtil;
import com.example.workflow_service.utils.enums.ApprovalStatus;
import com.example.workflow_service.utils.enums.WorkflowType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalTrackingService - JaCoCo branch unit coverage")
class ApprovalTrackingServiceBranchUnitTest {

    @Mock private ApprovalTrackingRepository approvalTrackingRepository;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowStepRepository workflowStepRepository;
    @Mock private RecruitmentWorkflowProducer workflowProducer;
    @Mock private NotificationProducer notificationProducer;
    @Mock private UserService userService;
    @Mock private CandidateService candidateService;
    @Mock private RestTemplate restTemplate;

    private ApprovalTrackingService service;
    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        service = new ApprovalTrackingService(
                approvalTrackingRepository,
                workflowRepository,
                workflowStepRepository,
                workflowProducer,
                notificationProducer,
                new ObjectMapper(),
                userService,
                candidateService,
                restTemplate);
        ReflectionTestUtils.setField(service, "userServiceBaseUrl", "http://user-service");
        ReflectionTestUtils.setField(service, "jobServiceBaseUrl", "http://job-service");

        securityUtil = mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::extractEmployeeId).thenReturn(100L);
        securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("token"));

        lenient().when(userService.getUserNamesByIds(anyList(), anyString())).thenReturn(Map.of());
        lenient().when(userService.getPositionNamesByIds(anyList(), anyString())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    @Test
    @DisplayName("[AT-BR01] initialize/approve/getById - nullable DTO mapping branches")
    void initializeApproveAndGetById_coverNullableMappingBranches() {
        Workflow workflow = workflow(1L, WorkflowType.REQUEST, 10L);
        WorkflowStep firstStep = step(11L, workflow, 1, 100L);

        when(workflowRepository.findMatchingWorkflow(10L)).thenReturn(Optional.of(workflow));
        when(workflowStepRepository.findByWorkflowIdAndStepOrder(1L, 1)).thenReturn(Optional.of(firstStep));
        doReturn(userPositionsResponse(100L)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), eq(10L), anyString())).thenReturn(null);
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenAnswer(invocation -> {
            ApprovalTracking tracking = invocation.getArgument(0);
            tracking.setId(101L);
            tracking.setApproverPositionId(null);
            tracking.setActionUserId(null);
            return tracking;
        });

        CreateApprovalTrackingDTO createDto = new CreateApprovalTrackingDTO();
        createDto.setDepartmentId(10L);
        createDto.setRequestId(9001L);
        createDto.setLevelId(100L);

        ApprovalTrackingResponseDTO created = service.initializeApproval(createDto);
        assertNull(created.getApproverPositionId(), "BUG: DTO phải giữ null approverPositionId từ repository");
        assertNull(created.getActionUserId(), "BUG: DTO phải giữ null actionUserId từ repository");

        ApprovalTracking pending = tracking(201L, 9002L, firstStep, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findById(201L)).thenReturn(Optional.of(pending));

        ApproveStepDTO rejectDto = new ApproveStepDTO();
        rejectDto.setApproved(false);
        rejectDto.setApprovalNotes("reject");

        ApprovalTrackingResponseDTO rejected = service.approve(201L, rejectDto);
        assertNull(rejected.getApproverPositionId(), "BUG: approve response phải handle approverPositionId null");
        assertNull(rejected.getActionUserId(), "BUG: approve response phải handle actionUserId null");

        ApprovalTracking nullableTracking = tracking(202L, 9003L, firstStep, ApprovalStatus.PENDING, null);
        nullableTracking.setApproverPositionId(null);
        when(approvalTrackingRepository.findById(202L)).thenReturn(Optional.of(nullableTracking));

        ApprovalTrackingResponseDTO byId = service.getById(202L);
        assertNull(byId.getActionUserId(), "BUG: getById phải handle actionUserId null");
        assertNull(byId.getApproverPositionId(), "BUG: getById phải handle approverPositionId null");
    }

    @Test
    @DisplayName("[AT-BR02] findUserByPositionId - null body/data/empty/exception branches")
    void findUserByPositionId_coverResponseGuardBranches() throws Exception {
        Method method = ApprovalTrackingService.class.getDeclaredMethod("findUserByPositionId", Long.class);
        method.setAccessible(true);

        doReturn(ResponseEntity.ok().body(null)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(method.invoke(service, 1L));

        Response<List<Object>> nullData = new Response<>();
        nullData.setData(null);
        doReturn(ResponseEntity.ok(nullData)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(method.invoke(service, 1L));

        Response<List<Object>> emptyData = new Response<>();
        emptyData.setData(List.of());
        doReturn(ResponseEntity.ok(emptyData)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(method.invoke(service, 1L));

        doThrow(new RuntimeException("down")).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(method.invoke(service, 1L));
    }

    @Test
    @DisplayName("[AT-BR03] getAll/getPending - null id filter branches")
    void getAllAndPending_coverNullIdFilters() {
        Workflow workflow = workflow(1L, WorkflowType.REQUEST, 10L);
        WorkflowStep firstStep = step(11L, workflow, 1, 100L);
        ApprovalTracking nullIds = tracking(301L, 9004L, firstStep, ApprovalStatus.PENDING, null);
        nullIds.setApproverPositionId(null);
        ApprovalTracking fullIds = tracking(302L, 9004L, firstStep, ApprovalStatus.PENDING, 100L);
        fullIds.setActionUserId(200L);

        when(approvalTrackingRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(nullIds, fullIds), PageRequest.of(0, 10), 2));
        when(approvalTrackingRepository.findByApproverPositionIdAndStatus(null, ApprovalStatus.PENDING))
                .thenReturn(List.of(nullIds, fullIds));

        assertNotNull(service.getAll(null, null, null, PageRequest.of(0, 10)).getResult());
        assertEquals(2, service.getPendingApprovalsForUser(null).size());
    }

    @Test
    @DisplayName("[AT-BR04] getWorkflowInfoByRequestId - request type/filter/null step branches")
    void getWorkflowInfoByRequestId_coverFilterAndNullStepBranches() {
        Workflow workflow = workflow(401L, WorkflowType.REQUEST, 10L);
        WorkflowStep stepWithApprover = step(411L, workflow, 1, 100L);
        WorkflowStep stepWithoutApprover = step(412L, workflow, 2, null);
        workflow.setSteps(new LinkedHashSet<>(List.of(stepWithApprover, stepWithoutApprover)));

        ApprovalTracking nullStep = tracking(421L, 9005L, null, ApprovalStatus.PENDING, null);
        ApprovalTracking nullWorkflowStep = tracking(422L, 9005L, step(413L, null, 1, null), ApprovalStatus.PENDING, null);
        ApprovalTracking matching = tracking(423L, 9005L, stepWithApprover, ApprovalStatus.APPROVED, null);
        matching.setActionUserId(null);

        when(approvalTrackingRepository.findByRequestId(9005L)).thenReturn(List.of(nullStep, nullWorkflowStep, matching));
        when(workflowRepository.findById(401L)).thenReturn(Optional.of(workflow));

        var result = service.getWorkflowInfoByRequestId(9005L, 401L, "RECRUITMENT_REQUEST");

        assertNotNull(result);
        assertEquals(401L, result.getWorkflow().getId());
        assertEquals(1, result.getApprovalTrackings().size());
    }

    @Test
    @DisplayName("[AT-BR04B] getById/workflow info - positive and null stream branches")
    void getByIdAndWorkflowInfo_coverRemainingCollectionBranches() {
        Workflow workflow = workflow(430L, WorkflowType.REQUEST, 10L);
        WorkflowStep stepWithApprover = step(431L, workflow, 1, 100L);
        ApprovalTracking withUsers = tracking(432L, 9007L, stepWithApprover, ApprovalStatus.PENDING, 100L);
        withUsers.setActionUserId(200L);
        when(approvalTrackingRepository.findById(432L)).thenReturn(Optional.of(withUsers));

        ApprovalTrackingResponseDTO byId = service.getById(432L);
        assertEquals(200L, byId.getActionUserId());

        ApprovalTracking nullStepPending = tracking(433L, 9008L, null, ApprovalStatus.PENDING, null);
        ApprovalTracking nullActionWithStep = tracking(434L, 9008L, stepWithApprover, ApprovalStatus.APPROVED, null);
        when(approvalTrackingRepository.findByRequestId(9008L)).thenReturn(List.of(nullStepPending, nullActionWithStep));
        when(workflowRepository.findById(430L)).thenReturn(Optional.of(workflow));

        var result = service.getWorkflowInfoByRequestId(9008L, 430L, "   ");

        assertEquals(2, result.getApprovalTrackings().size());
        assertEquals(430L, result.getWorkflow().getId());
    }

    @Test
    @DisplayName("[AT-BR05] private helpers - workflow type, requester parsing, invalidation guards")
    void privateHelpers_coverRemainingGuardBranches() throws Exception {
        Method workflowType = ApprovalTrackingService.class.getDeclaredMethod("getWorkflowTypeFromRequestType", String.class);
        workflowType.setAccessible(true);
        assertNull(workflowType.invoke(service, (String) null));
        assertNull(workflowType.invoke(service, "   "));
        assertEquals(WorkflowType.REQUEST, workflowType.invoke(service, "RECRUITMENT_REQUEST"));
        assertEquals(WorkflowType.REQUEST, workflowType.invoke(service, "REQUEST"));
        assertEquals(WorkflowType.OFFER, workflowType.invoke(service, "OFFER"));
        assertNull(workflowType.invoke(service, "OTHER"));

        Method filter = ApprovalTrackingService.class.getDeclaredMethod("filterByWorkflowType", List.class, WorkflowType.class);
        filter.setAccessible(true);
        Workflow workflow = workflow(501L, WorkflowType.REQUEST, 10L);
        ApprovalTracking nullStep = tracking(511L, 9006L, null, ApprovalStatus.PENDING, 100L);
        ApprovalTracking nullWorkflow = tracking(512L, 9006L, step(513L, null, 1, 100L), ApprovalStatus.PENDING, 100L);
        ApprovalTracking matching = tracking(514L, 9006L, step(515L, workflow, 1, 100L), ApprovalStatus.PENDING, 100L);
        assertEquals(3, ((List<?>) filter.invoke(service, List.of(nullStep, nullWorkflow, matching), null)).size());
        assertEquals(1, ((List<?>) filter.invoke(service, List.of(nullStep, nullWorkflow, matching), WorkflowType.REQUEST)).size());

        Method invalidate = ApprovalTrackingService.class.getDeclaredMethod("invalidateFutureSteps", Long.class, Long.class);
        invalidate.setAccessible(true);
        WorkflowStep currentWithoutWorkflow = step(520L, null, 1, 100L);
        when(workflowStepRepository.findById(520L)).thenReturn(Optional.of(currentWithoutWorkflow));
        assertThrows(InvocationTargetException.class, () -> invalidate.invoke(service, 9006L, 520L));

        WorkflowStep lastStep = step(521L, workflow, 2, 100L);
        when(workflowStepRepository.findById(521L)).thenReturn(Optional.of(lastStep));
        when(workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(501L)).thenReturn(List.of(lastStep));
        assertDoesNotThrow(() -> invalidate.invoke(service, 9006L, 521L));
    }

    @Test
    @DisplayName("[AT-BR06] requester helper JSON branches")
    void requesterHelpers_coverJsonFallbackBranches() throws Exception {
        Method positionMethod = ApprovalTrackingService.class.getDeclaredMethod("getRequesterPositionId", Long.class, String.class);
        Method departmentMethod = ApprovalTrackingService.class.getDeclaredMethod("getRequesterDepartmentId", Long.class, String.class);
        positionMethod.setAccessible(true);
        departmentMethod.setAccessible(true);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode employeeWithoutPosition = mapper.createObjectNode();
        employeeWithoutPosition.put("departmentId", 77L);
        doReturn(employeeResponse(employeeWithoutPosition)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        assertNull(positionMethod.invoke(service, 601L, null));
        assertEquals(77L, departmentMethod.invoke(service, 601L, ""));

        ObjectNode employeeWithNullDepartment = mapper.createObjectNode();
        ObjectNode department = mapper.createObjectNode();
        department.putNull("id");
        employeeWithNullDepartment.set("department", department);
        employeeWithNullDepartment.putNull("departmentId");
        doReturn(employeeResponse(employeeWithNullDepartment)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        assertNull(departmentMethod.invoke(service, 602L, "token"));

        Response<JsonNode> nullData = new Response<>();
        nullData.setData(null);
        doReturn(ResponseEntity.ok(nullData)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(positionMethod.invoke(service, 603L, "token"));
        assertNull(departmentMethod.invoke(service, 603L, "token"));

        doReturn(ResponseEntity.ok().body(null)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(positionMethod.invoke(service, 604L, ""));
        assertNull(departmentMethod.invoke(service, 604L, "token"));

        ObjectNode positionWithoutId = mapper.createObjectNode();
        positionWithoutId.set("position", mapper.createObjectNode());
        doReturn(employeeResponse(positionWithoutId)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(positionMethod.invoke(service, 605L, "token"));

        ObjectNode departmentWithoutId = mapper.createObjectNode();
        departmentWithoutId.set("department", mapper.createObjectNode());
        doReturn(employeeResponse(departmentWithoutId)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        assertNull(departmentMethod.invoke(service, 606L, "token"));
    }

    @Test
    @DisplayName("[AT-BR07] handle event guards and reject false branch")
    void handleWorkflowEvent_coverGuardAndRejectFalseBranches() {
        assertDoesNotThrow(() -> service.handleWorkflowEvent(null));

        RecruitmentWorkflowEvent missingType = event(null, "REQUEST", 700L);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(missingType));

        RecruitmentWorkflowEvent missingRequest = event("REQUEST_SUBMITTED", "REQUEST", null);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(missingRequest));

        when(approvalTrackingRepository.findByRequestIdAndStatus(701L, ApprovalStatus.PENDING)).thenReturn(List.of());
        RecruitmentWorkflowEvent rejectWithoutTracking = event("REQUEST_REJECTED", "REQUEST", 701L);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(rejectWithoutTracking));
    }

    @Test
    @DisplayName("[AT-BR08] submit/approve/reject/return - remaining event branches")
    void eventHandlers_coverRemainingSubmitApproveRejectReturnBranches() {
        Workflow workflow = workflow(800L, WorkflowType.REQUEST, 10L);
        WorkflowStep step1 = step(801L, workflow, 1, 100L);
        WorkflowStep step2 = step(802L, workflow, 2, 200L);

        when(workflowRepository.findById(800L)).thenReturn(Optional.of(workflow));
        when(workflowStepRepository.findByWorkflowIdAndStepOrder(800L, 1)).thenReturn(Optional.of(step1));
        lenient().when(workflowStepRepository.findByWorkflowIdAndStepOrder(800L, 2)).thenReturn(Optional.of(step2));
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), anyLong(), anyString())).thenReturn(200L);
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalTracking returnedWithoutTarget = tracking(811L, 9100L, step1, ApprovalStatus.RETURNED, 100L);
        returnedWithoutTarget.setReturnedToStepId(null);
        when(approvalTrackingRepository.findByRequestId(9100L)).thenReturn(List.of(returnedWithoutTarget));
        RecruitmentWorkflowEvent firstSubmit = event("REQUEST_SUBMITTED", "REQUEST", 9100L);
        firstSubmit.setWorkflowId(800L);
        firstSubmit.setDepartmentId(10L);
        firstSubmit.setRequesterId(null);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(firstSubmit));

        RecruitmentWorkflowEvent nullDepartmentSubmit = event("REQUEST_SUBMITTED", "REQUEST", 9101L);
        nullDepartmentSubmit.setWorkflowId(800L);
        nullDepartmentSubmit.setDepartmentId(null);
        nullDepartmentSubmit.setRequesterId(100L);
        when(approvalTrackingRepository.findByRequestId(9101L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.handleWorkflowEvent(nullDepartmentSubmit));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode employee = mapper.createObjectNode();
        ObjectNode position = mapper.createObjectNode();
        position.put("id", 999L);
        employee.set("position", position);
        employee.put("departmentId", 10L);
        doReturn(employeeResponse(employee)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));

        RecruitmentWorkflowEvent requesterMismatch = event("REQUEST_SUBMITTED", "REQUEST", 9102L);
        requesterMismatch.setWorkflowId(800L);
        requesterMismatch.setDepartmentId(10L);
        requesterMismatch.setRequesterId(100L);
        when(approvalTrackingRepository.findByRequestId(9102L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.handleWorkflowEvent(requesterMismatch));

        ObjectNode employeeDeptMismatch = mapper.createObjectNode();
        ObjectNode matchingPosition = mapper.createObjectNode();
        matchingPosition.put("id", 100L);
        employeeDeptMismatch.set("position", matchingPosition);
        employeeDeptMismatch.put("departmentId", 99L);
        doReturn(employeeResponse(employeeDeptMismatch)).when(restTemplate).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        RecruitmentWorkflowEvent departmentMismatch = event("REQUEST_SUBMITTED", "REQUEST", 9103L);
        departmentMismatch.setWorkflowId(800L);
        departmentMismatch.setDepartmentId(10L);
        departmentMismatch.setRequesterId(100L);
        when(approvalTrackingRepository.findByRequestId(9103L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.handleWorkflowEvent(departmentMismatch));

        ApprovalTracking returned = tracking(812L, 9104L, step1, ApprovalStatus.RETURNED, 100L);
        returned.setReturnedToStepId(801L);
        when(approvalTrackingRepository.findByRequestId(9104L)).thenReturn(List.of(returned));
        when(workflowStepRepository.findById(801L)).thenReturn(Optional.of(step1));
        RecruitmentWorkflowEvent resubmit = event("REQUEST_SUBMITTED", "REQUEST", 9104L);
        resubmit.setWorkflowId(800L);
        resubmit.setDepartmentId(10L);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(resubmit));

        ApprovalTracking approvedWithoutStep = tracking(813L, 9105L, null, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findByRequestIdAndStatus(9105L, ApprovalStatus.PENDING))
                .thenReturn(List.of(approvedWithoutStep));
        RecruitmentWorkflowEvent approveNoStep = event("REQUEST_APPROVED", null, 9105L);
        assertThrows(com.example.workflow_service.exception.CustomException.class,
                () -> service.handleWorkflowEvent(approveNoStep));

        ApprovalTracking lastStepPending = tracking(814L, 9106L, step2, ApprovalStatus.PENDING, 200L);
        ApprovalTracking otherPending = tracking(815L, 9106L, step1, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findByRequestIdAndStatus(9106L, ApprovalStatus.PENDING))
                .thenReturn(List.of(lastStepPending), List.of(otherPending));
        when(workflowStepRepository.findByWorkflowIdAndStepOrder(800L, 3)).thenReturn(Optional.empty());
        RecruitmentWorkflowEvent approveWithRemaining = event("REQUEST_APPROVED", "REQUEST", 9106L);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(approveWithRemaining));

        ApprovalTracking rejectNoStep = tracking(816L, 9107L, null, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findByRequestIdAndStatus(9107L, ApprovalStatus.PENDING))
                .thenReturn(List.of(rejectNoStep));
        RecruitmentWorkflowEvent reject = event("REQUEST_REJECTED", null, 9107L);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(reject));

        ApprovalTracking returnNoStep = tracking(817L, 9108L, null, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findByRequestIdAndStatus(9108L, ApprovalStatus.PENDING))
                .thenReturn(List.of(returnNoStep));
        RecruitmentWorkflowEvent returnedEvent = event("REQUEST_RETURNED", null, 9108L);
        returnedEvent.setWorkflowId(800L);
        assertDoesNotThrow(() -> service.handleWorkflowEvent(returnedEvent));
    }

    @Test
    @DisplayName("[AT-BR09] private invalidation/create/placeholder branches")
    void privateInvalidationCreateAndPlaceholder_coverRemainingBranches() throws Exception {
        Workflow workflow = workflow(900L, WorkflowType.REQUEST, 10L);
        WorkflowStep step1 = step(901L, workflow, 1, 100L);
        WorkflowStep step2 = step(902L, workflow, 2, 200L);

        Method invalidate = ApprovalTrackingService.class.getDeclaredMethod("invalidateFutureSteps", Long.class, Long.class);
        invalidate.setAccessible(true);
        when(workflowStepRepository.findById(901L)).thenReturn(Optional.of(step1));
        when(workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(900L)).thenReturn(List.of(step1, step2));
        ApprovalTracking nullStep = tracking(911L, 9200L, null, ApprovalStatus.PENDING, 100L);
        ApprovalTracking approvedFuture = tracking(912L, 9200L, step2, ApprovalStatus.APPROVED, 200L);
        when(approvalTrackingRepository.findByRequestId(9200L)).thenReturn(List.of(nullStep, approvedFuture));
        assertDoesNotThrow(() -> invalidate.invoke(service, 9200L, 901L));

        Method create = ApprovalTrackingService.class
                .getDeclaredMethod("createTrackingForStep", Long.class, WorkflowStep.class, Long.class, String.class);
        create.setAccessible(true);
        WorkflowStep noApprover = step(903L, workflow, 3, null);
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApprovalTracking created = (ApprovalTracking) create.invoke(service, 9201L, noApprover, 10L, "token");
        assertNull(created.getActionUserId());

        Method placeholder = ApprovalTrackingService.class
                .getDeclaredMethod("isReturnPlaceholderTracking", ApprovalTracking.class);
        placeholder.setAccessible(true);
        ApprovalTracking nullNotes = tracking(913L, 9202L, step1, ApprovalStatus.PENDING, 100L);
        nullNotes.setNotes(null);
        ApprovalTracking otherNotes = tracking(914L, 9202L, step1, ApprovalStatus.PENDING, 100L);
        otherNotes.setNotes("other");
        assertEquals(false, placeholder.invoke(service, nullNotes));
        assertEquals(false, placeholder.invoke(service, otherNotes));
    }

    @Test
    @DisplayName("[AT-BR10] final JaCoCo branch edges")
    void finalJacocoBranchEdges() throws Exception {
        Workflow workflow = workflow(1000L, WorkflowType.REQUEST, 10L);
        WorkflowStep step1 = step(1001L, workflow, 1, 100L);
        WorkflowStep step2 = step(1002L, workflow, 2, 200L);

        ApprovalTracking actionUserTracking = tracking(1011L, 9300L, step1, ApprovalStatus.APPROVED, 100L);
        actionUserTracking.setActionUserId(300L);
        when(approvalTrackingRepository.findByRequestId(9300L)).thenReturn(List.of(actionUserTracking));
        when(workflowRepository.findById(1000L)).thenReturn(Optional.of(workflow));
        service.getWorkflowInfoByRequestId(9300L, 1000L, " ");

        Workflow noApproverWorkflow = workflow(1001L, WorkflowType.REQUEST, 10L);
        WorkflowStep noApproverStep = step(1003L, noApproverWorkflow, 1, null);
        when(workflowRepository.findById(1001L)).thenReturn(Optional.of(noApproverWorkflow));
        when(workflowStepRepository.findByWorkflowIdAndStepOrder(1001L, 1)).thenReturn(Optional.of(noApproverStep));
        when(approvalTrackingRepository.findByRequestId(9301L)).thenReturn(List.of());
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RecruitmentWorkflowEvent noApproverSubmit = event("REQUEST_SUBMITTED", "REQUEST", 9301L);
        noApproverSubmit.setWorkflowId(1001L);
        noApproverSubmit.setDepartmentId(10L);
        noApproverSubmit.setRequesterId(100L);
        service.handleWorkflowEvent(noApproverSubmit);

        ApprovalTracking toggledReturned = mock(ApprovalTracking.class);
        when(toggledReturned.getReturnedToStepId()).thenReturn(1001L, null);
        when(toggledReturned.getStep()).thenReturn(step1);
        when(approvalTrackingRepository.findByRequestId(9302L)).thenReturn(List.of(toggledReturned));
        when(workflowRepository.findById(1000L)).thenReturn(Optional.of(workflow));
        when(workflowStepRepository.findByWorkflowIdAndStepOrder(1000L, 1)).thenReturn(Optional.of(step1));
        RecruitmentWorkflowEvent toggledSubmit = event("REQUEST_SUBMITTED", "REQUEST", 9302L);
        toggledSubmit.setWorkflowId(1000L);
        toggledSubmit.setDepartmentId(10L);
        service.handleWorkflowEvent(toggledSubmit);

        ApprovalTracking returned = tracking(1012L, 9303L, step1, ApprovalStatus.RETURNED, 100L);
        returned.setReturnedToStepId(1001L);
        when(approvalTrackingRepository.findByRequestId(9303L)).thenReturn(List.of(returned));
        when(workflowStepRepository.findById(1001L)).thenReturn(Optional.of(step1));
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), anyLong(), anyString())).thenReturn(100L);
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenAnswer(invocation -> {
            ApprovalTracking saved = invocation.getArgument(0);
            saved.setNotes("old note");
            return saved;
        });
        RecruitmentWorkflowEvent resubmitWithOldNote = event("REQUEST_SUBMITTED", "REQUEST", 9303L);
        resubmitWithOldNote.setWorkflowId(1000L);
        resubmitWithOldNote.setDepartmentId(10L);
        service.handleWorkflowEvent(resubmitWithOldNote);

        ApprovalTracking returnedBlank = tracking(1013L, 9304L, step1, ApprovalStatus.RETURNED, 100L);
        returnedBlank.setReturnedToStepId(1001L);
        when(approvalTrackingRepository.findByRequestId(9304L)).thenReturn(List.of(returnedBlank));
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenAnswer(invocation -> {
            ApprovalTracking saved = invocation.getArgument(0);
            saved.setNotes(" ");
            return saved;
        });
        RecruitmentWorkflowEvent resubmitWithBlankNote = event("REQUEST_SUBMITTED", "REQUEST", 9304L);
        resubmitWithBlankNote.setWorkflowId(1000L);
        resubmitWithBlankNote.setDepartmentId(10L);
        service.handleWorkflowEvent(resubmitWithBlankNote);

        ApprovalTracking returnedNullSave = tracking(1014L, 9305L, step1, ApprovalStatus.RETURNED, 100L);
        returnedNullSave.setReturnedToStepId(1001L);
        when(approvalTrackingRepository.findByRequestId(9305L)).thenReturn(List.of(returnedNullSave));
        when(approvalTrackingRepository.save(any(ApprovalTracking.class))).thenReturn(null);
        RecruitmentWorkflowEvent resubmitNullSave = event("REQUEST_SUBMITTED", "REQUEST", 9305L);
        resubmitNullSave.setWorkflowId(1000L);
        resubmitNullSave.setDepartmentId(10L);
        service.handleWorkflowEvent(resubmitNullSave);

        Method invalidate = ApprovalTrackingService.class.getDeclaredMethod("invalidateFutureSteps", Long.class, Long.class);
        invalidate.setAccessible(true);
        when(workflowStepRepository.findById(1001L)).thenReturn(Optional.of(step1));
        when(workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(1000L)).thenReturn(List.of(step1, step2));
        ApprovalTracking notFutureStep = tracking(1015L, 9306L, step1, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findByRequestId(9306L)).thenReturn(List.of(notFutureStep));
        invalidate.invoke(service, 9306L, 1001L);

        Workflow workflowWithoutType = workflow(1002L, null, 10L);
        WorkflowStep currentWithoutType = step(1004L, workflowWithoutType, 1, 100L);
        WorkflowStep futureWithoutType = step(1005L, workflowWithoutType, 2, 200L);
        when(workflowStepRepository.findById(1004L)).thenReturn(Optional.of(currentWithoutType));
        when(workflowStepRepository.findByWorkflowIdOrderByStepOrderAsc(1002L))
                .thenReturn(List.of(currentWithoutType, futureWithoutType));
        ApprovalTracking nullStepFutureTracking = tracking(1016L, 9307L, null, ApprovalStatus.PENDING, 100L);
        when(approvalTrackingRepository.findByRequestId(9307L)).thenReturn(List.of(nullStepFutureTracking));
        invalidate.invoke(service, 9307L, 1004L);
    }

    private Workflow workflow(Long id, WorkflowType type, Long departmentId) {
        Workflow workflow = new Workflow();
        workflow.setId(id);
        workflow.setName("Workflow " + id);
        workflow.setType(type);
        workflow.setDepartmentId(departmentId);
        workflow.setIsActive(true);
        return workflow;
    }

    private WorkflowStep step(Long id, Workflow workflow, int order, Long approverPositionId) {
        WorkflowStep step = new WorkflowStep();
        step.setId(id);
        step.setWorkflow(workflow);
        step.setStepOrder(order);
        step.setApproverPositionId(approverPositionId);
        step.setIsActive(true);
        return step;
    }

    private ApprovalTracking tracking(Long id, Long requestId, WorkflowStep step, ApprovalStatus status, Long approverPositionId) {
        ApprovalTracking tracking = new ApprovalTracking();
        tracking.setId(id);
        tracking.setRequestId(requestId);
        tracking.setStep(step);
        tracking.setStatus(status);
        tracking.setApproverPositionId(approverPositionId);
        tracking.setActionUserId(approverPositionId);
        return tracking;
    }

    private RecruitmentWorkflowEvent event(String eventType, String requestType, Long requestId) {
        RecruitmentWorkflowEvent event = new RecruitmentWorkflowEvent();
        event.setEventType(eventType);
        event.setRequestType(requestType);
        event.setRequestId(requestId);
        event.setActorUserId(100L);
        event.setAuthToken("token");
        return event;
    }

    private ResponseEntity<Response<List<Object>>> userPositionsResponse(Long userId) {
        Response<List<Object>> response = new Response<>();
        response.setData(List.of(userPositionDto(userId)));
        return ResponseEntity.ok(response);
    }

    private Object userPositionDto(Long userId) {
        try {
            Class<?> dtoClass = Class.forName("com.example.workflow_service.service.ApprovalTrackingService$UserPositionDTO");
            var constructor = dtoClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object dto = constructor.newInstance();
            var field = dtoClass.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(dto, userId);
            return dto;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ResponseEntity<Response<JsonNode>> employeeResponse(JsonNode node) {
        Response<JsonNode> response = new Response<>();
        response.setData(node);
        return ResponseEntity.ok(response);
    }
}

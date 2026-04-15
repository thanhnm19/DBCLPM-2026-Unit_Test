package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.PaginationDTO;
import com.example.user_service.dto.employee.CreateEmployeeDTO;
import com.example.user_service.dto.employee.CreateEmployeeFromCandidateDTO;
import com.example.user_service.dto.employee.UpdateEmployeeDTO;
import com.example.user_service.exception.CustomException;
import com.example.user_service.model.Department;
import com.example.user_service.model.Employee;
import com.example.user_service.model.Position;
import com.example.user_service.repository.DepartmentRepository;
import com.example.user_service.repository.EmployeeRepository;
import com.example.user_service.repository.PositionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("EmployeeService Unit Test")
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private DataInitializer dataInitializer;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private PositionService positionService;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Test
    @DisplayName("[EMP-TC01] - create() tạo nhân viên mới và lưu đúng dữ liệu")
    void tc01_create_persistsEmployeeWithDepartmentAndPosition() {
        // Test Case ID: EMP-TC01
        // Mục tiêu: xác minh create() ghi đúng bản ghi nhân viên vào DB thật.

        // Arrange
        Department department = createDepartment("ORG_EMP_01", "Human Resources");
        Position position = createPosition("Recruiter", "L2", 2);
        when(departmentService.getById(department.getId())).thenReturn(department);
        when(positionService.getById(position.getId())).thenReturn(position);

        CreateEmployeeDTO dto = new CreateEmployeeDTO(
                "Nguyen Van A",
                "0901001001",
                "a.nguyen@company.com",
                "MALE",
                "Ha Noi",
                "Viet Nam",
                LocalDate.of(1998, 1, 12),
                "012345678",
                department.getId(),
                position.getId(),
                null);
        long countBeforeCreate = employeeRepository.count();

        // Act
        Employee result = employeeService.create(dto);
        forceSyncPersistenceContext();

        // Assert
        assertThat(employeeRepository.count()).isEqualTo(countBeforeCreate + 1);
        Employee savedEmployee = employeeRepository.findById(result.getId()).orElseThrow();
        assertThat(savedEmployee.getName()).isEqualTo("Nguyen Van A");
        assertThat(savedEmployee.getEmail()).isEqualTo("a.nguyen@company.com");
        assertThat(savedEmployee.getStatus()).isEqualTo("ACTIVE");
        assertThat(savedEmployee.getDepartment().getId()).isEqualTo(department.getId());
        assertThat(savedEmployee.getPosition().getId()).isEqualTo(position.getId());
    }

    @Test
    @DisplayName("[EMP-TC02] - create() ném CustomException khi phòng ban không tồn tại")
    void tc02_create_missingDepartment_throwsCustomException() {
        // Test Case ID: EMP-TC02
        // Mục tiêu: xác minh nhánh lỗi phòng ban không tồn tại.

        // Arrange
        when(departmentService.getById(404L)).thenReturn(null);
        CreateEmployeeDTO dto = new CreateEmployeeDTO(
                "Nguyen Van B",
                "0901001002",
                "b.nguyen@company.com",
                "MALE",
                "Ha Noi",
                "Viet Nam",
                LocalDate.of(1998, 2, 12),
                "012345679",
                404L,
                1L,
                null);

        // Act
        CustomException exception = assertThrows(CustomException.class, () -> employeeService.create(dto));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Phòng ban không tồn tại");
    }

    @Test
    @DisplayName("[EMP-TC03] - update() cập nhật đúng nhân viên trong DB")
    void tc03_update_updatesPersistedEmployeeSuccessfully() {
        // Test Case ID: EMP-TC03
        // Mục tiêu: xác minh update() ghi đè đúng dữ liệu nhân viên.

        // Arrange
        Department department1 = createDepartment("ORG_EMP_02", "Operations");
        Department department2 = createDepartment("ORG_EMP_03", "Finance");
        Position position1 = createPosition("Staff", "L1", 1);
        Position position2 = createPosition("Senior Staff", "L3", 3);
        Employee existingEmployee = createEmployee("Nguyen Van C", "c.nguyen@company.com", department1, position1,
                "ACTIVE");

        when(departmentService.getById(department2.getId())).thenReturn(department2);
        when(positionService.getById(position2.getId())).thenReturn(position2);

        UpdateEmployeeDTO dto = new UpdateEmployeeDTO(
                "Nguyen Van C Updated",
                "0901001003",
                "c.updated@company.com",
                "FEMALE",
                "Da Nang",
                "Viet Nam",
                LocalDate.of(1997, 3, 10),
                "012345680",
                department2.getId(),
                position2.getId(),
                "PROBATION");

        // Act
        Employee result = employeeService.update(existingEmployee.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Employee updatedEmployee = employeeRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedEmployee.getName()).isEqualTo("Nguyen Van C Updated");
        assertThat(updatedEmployee.getEmail()).isEqualTo("c.updated@company.com");
        assertThat(updatedEmployee.getStatus()).isEqualTo("PROBATION");
        assertThat(updatedEmployee.getDepartment().getId()).isEqualTo(department2.getId());
        assertThat(updatedEmployee.getPosition().getId()).isEqualTo(position2.getId());
    }

    @Test
    @DisplayName("[EMP-TC04] - delete() xóa nhân viên khỏi DB")
    void tc04_delete_removesEmployeeFromDatabase() {
        // Test Case ID: EMP-TC04
        // Mục tiêu: xác minh delete() xóa đúng record và DB giảm số lượng.

        // Arrange
        Department department = createDepartment("ORG_EMP_04", "Quality Assurance");
        Position position = createPosition("Tester", "L1", 1);
        Employee existingEmployee = createEmployee("Nguyen Van D", "d.nguyen@company.com", department, position,
                "ACTIVE");
        long countBeforeDelete = employeeRepository.count();

        // Act
        employeeService.delete(existingEmployee.getId());
        forceSyncPersistenceContext();

        // Assert
        assertThat(employeeRepository.existsById(existingEmployee.getId())).isFalse();
        assertThat(employeeRepository.count()).isEqualTo(countBeforeDelete - 1);
    }

    @Test
    @DisplayName("[EMP-TC05] - getById() trả về đúng nhân viên khi id tồn tại")
    void tc05_getById_returnsEmployee() {
        // Test Case ID: EMP-TC05
        // Mục tiêu: xác minh nhánh đọc dữ liệu theo id hợp lệ.

        // Arrange
        Department department = createDepartment("ORG_EMP_05", "Administration");
        Position position = createPosition("Admin", "L2", 2);
        Employee existingEmployee = createEmployee("Nguyen Van E", "e.nguyen@company.com", department, position,
                "ACTIVE");

        // Act
        Employee result = employeeService.getById(existingEmployee.getId());

        // Assert
        assertThat(result.getId()).isEqualTo(existingEmployee.getId());
        assertThat(result.getEmail()).isEqualTo("e.nguyen@company.com");
    }

    @Test
    @DisplayName("[EMP-TC06] - getById() trả về null khi id không tồn tại")
    void tc06_getById_missingId_returnsNull() {
        // Test Case ID: EMP-TC06
        // Mục tiêu: xác minh service trả null đúng theo thiết kế hiện tại.

        // Act
        Employee result = employeeService.getById(40404L);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("[EMP-TC07] - getAll() trả về metadata và danh sách nhân viên")
    void tc07_getAll_returnsPaginationData() {
        // Test Case ID: EMP-TC07
        // Mục tiêu: xác minh paging metadata và danh sách trả về.

        // Arrange
        Department department = createDepartment("ORG_EMP_06", "Department 1");
        Position position = createPosition("P1", "L1", 1);
        createEmployee("Employee 1", "emp1@company.com", department, position, "ACTIVE");
        createEmployee("Employee 2", "emp2@company.com", department, position, "PROBATION");

        // Act
        PaginationDTO result = employeeService.getAll(PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isGreaterThanOrEqualTo(2L);
        assertThat((List<Employee>) result.getResult()).isNotEmpty();
    }

    @Test
    @DisplayName("[EMP-TC08] - getAllWithFilters() lọc theo phòng ban, vị trí, trạng thái và keyword")
    void tc08_getAllWithFilters_filtersByDepartmentPositionStatusAndKeyword() {
        // Test Case ID: EMP-TC08
        // Mục tiêu: xác minh query lọc trả về đúng bản ghi theo điều kiện.

        // Arrange
        Department department1 = createDepartment("ORG_EMP_07", "Sales");
        Department department2 = createDepartment("ORG_EMP_08", "Human Resources");
        Position position1 = createPosition("Sales Staff", "L1", 1);
        Position position2 = createPosition("HR Staff", "L1", 1);
        createEmployee("Alice Sales", "alice.sales@company.com", department1, position1, "ACTIVE");
        createEmployee("Bob HR", "bob.hr@company.com", department2, position2, "PROBATION");

        // Act
        PaginationDTO result = employeeService.getAllWithFilters(
                department1.getId(),
                position1.getId(),
                "ACTIVE",
                "alice",
                PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        List<Employee> employees = (List<Employee>) result.getResult();
        assertThat(employees).hasSize(1);
        assertThat(employees.get(0).getEmail()).isEqualTo("alice.sales@company.com");
    }

    @Test
    @DisplayName("[EMP-TC09] - getByIds() trả về đúng danh sách nhân viên")
    void tc09_getByIds_returnsMatchedEmployees() {
        // Test Case ID: EMP-TC09
        // Mục tiêu: xác minh lấy nhiều nhân viên theo danh sách id.

        // Arrange
        Department department = createDepartment("ORG_EMP_09", "Operations");
        Position position = createPosition("Operator", "L1", 1);
        Employee employee1 = createEmployee("Employee A", "a@company.com", department, position, "ACTIVE");
        Employee employee2 = createEmployee("Employee B", "b@company.com", department, position, "ACTIVE");
        Employee employee3 = createEmployee("Employee C", "c@company.com", department, position, "ACTIVE");

        // Act
        List<Employee> result = employeeService.getByIds(List.of(employee1.getId(), employee3.getId()));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Employee::getEmail).containsExactlyInAnyOrder("a@company.com", "c@company.com");
        assertThat(result).extracting(Employee::getId).doesNotContain(employee2.getId());
    }

    @Test
    @DisplayName("[EMP-TC10] - getByDepartmentIds() trả về nhân viên theo danh sách phòng ban")
    void tc10_getByDepartmentIds_returnsEmployeesByDepartmentIds() {
        // Test Case ID: EMP-TC10
        // Mục tiêu: xác minh query lấy nhân viên theo danh sách phòng ban.

        // Arrange
        Department department1 = createDepartment("ORG_EMP_10", "Finance");
        Department department2 = createDepartment("ORG_EMP_11", "Marketing");
        Position position = createPosition("Staff", "L1", 1);
        Employee employee1 = createEmployee("Finance Staff", "fin@company.com", department1, position, "ACTIVE");
        Employee employee2 = createEmployee("Marketing Staff", "mkt@company.com", department2, position, "ACTIVE");

        // Act
        List<Employee> result = employeeService.getByDepartmentIds(List.of(department1.getId()));

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(employee1.getId());
        assertThat(result).extracting(Employee::getEmail).doesNotContain(employee2.getEmail());
    }

    @Test
    @DisplayName("[EMP-TC11] - uploadAvatar() trả về URL từ Cloudinary")
    void tc11_uploadAvatar_returnsSecureUrl() {
        // Test Case ID: EMP-TC11
        // Mục tiêu: xác minh service ủy quyền đúng cho CloudinaryService.

        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "avatar-content".getBytes());
        when(cloudinaryService.uploadFile(file)).thenReturn("https://cdn.example.com/avatar.png");

        // Act
        String result = employeeService.uploadAvatar(file);

        // Assert
        assertThat(result).isEqualTo("https://cdn.example.com/avatar.png");
    }

    @Test
    @DisplayName("[EMP-TC12] - createFromCandidate() tạo employee từ candidate và gán trạng thái mặc định")
    void tc12_createFromCandidate_mapsCandidateData() {
        // Test Case ID: EMP-TC12
        // Mục tiêu: xác minh mapping dữ liệu từ candidate sang employee.

        // Arrange
        Department department = createDepartment("ORG_EMP_12", "Talent Acquisition");
        Position position = createPosition("Talent Scout", "L2", 2);
        when(departmentService.getById(department.getId())).thenReturn(department);
        when(positionService.getById(position.getId())).thenReturn(position);

        CreateEmployeeFromCandidateDTO dto = new CreateEmployeeFromCandidateDTO();
        dto.setCandidateId(1001L);
        dto.setName("Candidate One");
        dto.setEmail("candidate.one@company.com");
        dto.setPhone("0901999888");
        dto.setDateOfBirth("1998-01-12");
        dto.setGender("MALE");
        dto.setNationality("Viet Nam");
        dto.setIdNumber("012345681");
        dto.setAddress("Ha Noi");
        dto.setAvatarUrl("https://cdn.example.com/candidate.png");
        dto.setDepartmentId(department.getId());
        dto.setPositionId(position.getId());

        // Act
        Employee result = employeeService.createFromCandidate(dto);
        forceSyncPersistenceContext();

        // Assert
        Employee savedEmployee = employeeRepository.findById(result.getId()).orElseThrow();
        assertThat(savedEmployee.getCandidateId()).isEqualTo(1001L);
        assertThat(savedEmployee.getName()).isEqualTo("Candidate One");
        assertThat(savedEmployee.getStatus()).isEqualTo("PROBATION");
        assertThat(savedEmployee.getDateOfBirth()).isEqualTo(LocalDate.of(1998, 1, 12));
        assertThat(savedEmployee.getDepartment().getId()).isEqualTo(department.getId());
        assertThat(savedEmployee.getPosition().getId()).isEqualTo(position.getId());
    }

    @Test
    @DisplayName("[EMP-TC13] - createFromCandidate() ném CustomException khi ngày sinh không hợp lệ")
    void tc13_createFromCandidate_invalidDate_throwsCustomException() {
        // Test Case ID: EMP-TC13
        // Mục tiêu: xác minh parse ngày sinh sai định dạng sẽ báo lỗi rõ ràng.

        // Arrange
        Department department = createDepartment("ENG", "Engineering");
        Position position = createPosition("Engineer", "L3", 3);
        when(departmentService.getById(department.getId())).thenReturn(department);
        when(positionService.getById(position.getId())).thenReturn(position);

        CreateEmployeeFromCandidateDTO dto = new CreateEmployeeFromCandidateDTO();
        dto.setCandidateId(1002L);
        dto.setName("Candidate Two");
        dto.setEmail("candidate.two@company.com");
        dto.setPhone("0901999777");
        dto.setDateOfBirth("invalid-date");
        dto.setGender("FEMALE");
        dto.setNationality("Viet Nam");
        dto.setIdNumber("012345682");
        dto.setAddress("Da Nang");
        dto.setDepartmentId(department.getId());
        dto.setPositionId(position.getId());

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> employeeService.createFromCandidate(dto));

        // Assert
        assertThat(exception.getMessage()).contains("Định dạng ngày sinh không hợp lệ");
    }

    private Department createDepartment(String code, String name) {
        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        department.setDescription(name + " description");
        department.set_active(true);
        return departmentRepository.save(department);
    }

    private Position createPosition(String name, String level, Integer hierarchyOrder) {
        Position position = new Position();
        position.setName(name);
        position.setLevel(level);
        position.setHierarchyOrder(hierarchyOrder);
        position.setActive(true);
        return positionRepository.save(position);
    }

    private Employee createEmployee(String name, String email, Department department, Position position,
            String status) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setEmail(email);
        employee.setDepartment(department);
        employee.setPosition(position);
        employee.setStatus(status);
        return employeeRepository.save(employee);
    }

    private void forceSyncPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
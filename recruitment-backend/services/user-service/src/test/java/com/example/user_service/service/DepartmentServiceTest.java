package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.PaginationDTO;
import com.example.user_service.dto.department.CreateDepartmentDTO;
import com.example.user_service.dto.department.UpdateDepartmentDTO;
import com.example.user_service.exception.CustomException;
import com.example.user_service.model.Department;
import com.example.user_service.model.Employee;
import com.example.user_service.repository.DepartmentRepository;
import com.example.user_service.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("DepartmentService Unit Test")
class DepartmentServiceTest {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("[DEP-TC01] - create() tạo phòng ban mới và lưu đúng dữ liệu")
    void tc01_create_persistsDepartmentAndUppercasesCode() {
        // Test Case ID: DEP-TC01
        // Mục tiêu: xác minh create() ghi đúng bản ghi phòng ban vào DB thật.

        // Arrange
        CreateDepartmentDTO dto = new CreateDepartmentDTO("hr", "Human Resources", "Phòng nhân sự", null);
        long countBeforeCreate = departmentRepository.count();

        // Act
        Department result = departmentService.create(dto);
        forceSyncPersistenceContext();

        // Assert
        assertThat(departmentRepository.count()).isEqualTo(countBeforeCreate + 1);
        Department savedDepartment = departmentRepository.findById(result.getId()).orElseThrow();
        assertThat(savedDepartment.getCode()).isEqualTo("HR");
        assertThat(savedDepartment.getName()).isEqualTo("Human Resources");
        assertThat(savedDepartment.getDescription()).isEqualTo("Phòng nhân sự");
        assertThat(savedDepartment.is_active()).isTrue();
    }

    @Test
    @DisplayName("[DEP-TC02] - create() ném CustomException khi mã phòng ban đã tồn tại")
    void tc02_create_duplicateCode_throwsCustomException() {
        // Test Case ID: DEP-TC02
        // Mục tiêu: xác minh nhánh trùng mã phòng ban không làm thay đổi DB.

        // Arrange
        createDepartment("HR", "Human Resources", "Phòng nhân sự", true);
        long countBeforeCreate = departmentRepository.count();

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> departmentService.create(new CreateDepartmentDTO("hr", "HR Duplicate", "Trùng mã", true)));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Mã phòng ban 'hr' đã tồn tại");
        assertThat(departmentRepository.count()).isEqualTo(countBeforeCreate);
    }

    @Test
    @DisplayName("[DEP-TC03] - update() cập nhật đúng phòng ban trong DB")
    void tc03_update_updatesPersistedDepartmentSuccessfully() {
        // Test Case ID: DEP-TC03
        // Mục tiêu: xác minh update() ghi đè đúng dữ liệu đã có.

        // Arrange
        Department existingDepartment = createDepartment("FIN", "Finance", "Phòng tài chính", true);
        UpdateDepartmentDTO dto = new UpdateDepartmentDTO("finance_ops", "Finance Ops", "Mô tả mới", false);

        // Act
        Department result = departmentService.update(existingDepartment.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Department updatedDepartment = departmentRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedDepartment.getCode()).isEqualTo("FINANCE_OPS");
        assertThat(updatedDepartment.getName()).isEqualTo("Finance Ops");
        assertThat(updatedDepartment.getDescription()).isEqualTo("Mô tả mới");
        assertThat(updatedDepartment.is_active()).isFalse();
    }

    @Test
    @DisplayName("[DEP-TC04] - update() ném CustomException khi mã mới đã tồn tại")
    void tc04_update_duplicateCode_throwsCustomException() {
        // Test Case ID: DEP-TC04
        // Mục tiêu: xác minh update() chặn trùng code giữa hai phòng ban khác nhau.

        // Arrange
        Department currentDepartment = createDepartment("OPS", "Operations", "Phòng vận hành", true);
        Department duplicatedDepartment = createDepartment("TECH", "Technology", "Phòng công nghệ", true);
        UpdateDepartmentDTO dto = new UpdateDepartmentDTO("tech", null, null, null);

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> departmentService.update(currentDepartment.getId(), dto));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Mã phòng ban 'TECH' đã tồn tại");
        assertThat(departmentRepository.findById(currentDepartment.getId()).orElseThrow().getCode()).isEqualTo("OPS");
        assertThat(departmentRepository.findById(duplicatedDepartment.getId()).orElseThrow().getCode())
                .isEqualTo("TECH");
    }

    @Test
    @DisplayName("[DEP-TC05] - delete() xóa phòng ban khỏi DB khi không còn nhân viên")
    void tc05_delete_removesDepartmentWithoutEmployees() {
        // Test Case ID: DEP-TC05
        // Mục tiêu: xác minh delete() xóa đúng record và DB giảm số lượng.

        // Arrange
        Department existingDepartment = createDepartment("LEGAL", "Legal", "Phòng pháp chế", true);
        long countBeforeDelete = departmentRepository.count();

        // Act
        departmentService.delete(existingDepartment.getId());
        forceSyncPersistenceContext();

        // Assert
        assertThat(departmentRepository.existsById(existingDepartment.getId())).isFalse();
        assertThat(departmentRepository.count()).isEqualTo(countBeforeDelete - 1);
    }

    @Test
    @DisplayName("[DEP-TC06] - delete() ném CustomException khi phòng ban còn nhân viên")
    void tc06_delete_withEmployees_throwsCustomException() {
        // Test Case ID: DEP-TC06
        // Mục tiêu: xác minh không thể xóa phòng ban đang được nhân viên sử dụng.

        // Arrange
        Department existingDepartment = createDepartment("QA", "Quality Assurance", "Phòng kiểm thử", true);
        Employee employee = new Employee();
        employee.setName("Nguyen Van QA");
        employee.setEmail("qa@company.com");
        employee.setDepartment(existingDepartment);
        employeeRepository.save(employee);
        forceSyncPersistenceContext();
        long countBeforeDelete = departmentRepository.count();

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> departmentService.delete(existingDepartment.getId()));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Không thể xóa phòng ban vì còn nhân viên trong phòng ban này");
        assertThat(departmentRepository.count()).isEqualTo(countBeforeDelete);
        assertThat(departmentRepository.existsById(existingDepartment.getId())).isTrue();
    }

    @Test
    @DisplayName("[DEP-TC07] - getById() trả về phòng ban đúng khi id tồn tại")
    void tc07_getById_returnsDepartment() {
        // Test Case ID: DEP-TC07
        // Mục tiêu: xác minh nhánh đọc dữ liệu theo id hợp lệ.

        // Arrange
        Department existingDepartment = createDepartment("ADM", "Administration", "Phòng hành chính", true);

        // Act
        Department result = departmentService.getById(existingDepartment.getId());

        // Assert
        assertThat(result.getId()).isEqualTo(existingDepartment.getId());
        assertThat(result.getCode()).isEqualTo("ADM");
        assertThat(result.getName()).isEqualTo("Administration");
    }

    @Test
    @DisplayName("[DEP-TC08] - getById() ném CustomException khi id không tồn tại")
    void tc08_getById_missingId_throwsCustomException() {
        // Test Case ID: DEP-TC08
        // Mục tiêu: xác minh nhánh lỗi khi phòng ban không tồn tại.

        // Act
        CustomException exception = assertThrows(CustomException.class, () -> departmentService.getById(40404L));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Không tìm thấy phòng ban với ID: 40404");
    }

    @Test
    @DisplayName("[DEP-TC09] - getAll() trả về metadata và danh sách phòng ban")
    void tc09_getAll_returnsPaginationData() {
        // Test Case ID: DEP-TC09
        // Mục tiêu: xác minh paging metadata và dữ liệu trả về đúng.

        // Arrange
        createDepartment("D1", "Department 1", "Mô tả 1", true);
        createDepartment("D2", "Department 2", "Mô tả 2", true);

        // Act
        PaginationDTO result = departmentService.getAll(PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isGreaterThanOrEqualTo(2L);
        assertThat((List<Department>) result.getResult()).isNotEmpty();
    }

    @Test
    @DisplayName("[DEP-TC10] - getAllWithFilters() lọc theo trạng thái và keyword")
    void tc10_getAllWithFilters_filtersByStatusAndKeyword() {
        // Test Case ID: DEP-TC10
        // Mục tiêu: xác minh query lọc có keyword và isActive hoạt động đúng.

        // Arrange
        createDepartment("ACC", "Accounting", "Tài chính kế toán", true);
        createDepartment("MKT", "Marketing", "Bộ phận truyền thông", false);

        // Act
        PaginationDTO result = departmentService.getAllWithFilters(true, "acc", PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        List<Department> departments = (List<Department>) result.getResult();
        assertThat(departments).hasSize(1);
        assertThat(departments.get(0).getCode()).isEqualTo("ACC");
    }

    @Test
    @DisplayName("[DEP-TC11] - getByIds() trả về đúng danh sách phòng ban")
    void tc11_getByIds_returnsMatchedDepartments() {
        // Test Case ID: DEP-TC11
        // Mục tiêu: xác minh lấy nhiều phòng ban theo danh sách id.

        // Arrange
        Department department1 = createDepartment("S1", "Sales 1", "Mô tả 1", true);
        Department department2 = createDepartment("S2", "Sales 2", "Mô tả 2", true);
        Department department3 = createDepartment("S3", "Sales 3", "Mô tả 3", true);

        // Act
        List<Department> result = departmentService.getByIds(List.of(department1.getId(), department3.getId()));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Department::getCode).containsExactlyInAnyOrder("S1", "S3");
        assertThat(result).extracting(Department::getId).doesNotContain(department2.getId());
    }

    private Department createDepartment(String code, String name, String description, boolean isActive) {
        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        department.setDescription(description);
        department.set_active(isActive);
        return departmentRepository.save(department);
    }

    private void forceSyncPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
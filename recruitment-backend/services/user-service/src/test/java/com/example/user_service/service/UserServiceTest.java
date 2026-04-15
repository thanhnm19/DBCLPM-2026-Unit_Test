package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.PaginationDTO;
import com.example.user_service.dto.user.CreateUserDTO;
import com.example.user_service.dto.user.UpdateUserDTO;
import com.example.user_service.dto.user.UserDTO;
import com.example.user_service.exception.CustomException;
import com.example.user_service.model.Employee;
import com.example.user_service.model.Role;
import com.example.user_service.model.User;
import com.example.user_service.repository.EmployeeRepository;
import com.example.user_service.repository.RoleRepository;
import com.example.user_service.repository.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("UserService Unit Test")
class UserServiceTest {

    // Ghi chú chung:
    // - Các test trong class này chạy trên DB test H2 thật.
    // - @Transactional đảm bảo rollback sau mỗi test để không làm bẩn dữ liệu.
    // - Với các luồng thay đổi DB, luôn có CheckDB bằng count/findById/existsById.

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("[US-TC01] - create() tạo user mới và lưu đúng dữ liệu")
    void tc01_create_persistsNewUserAndCopiesFields() {
        // Arrange
        // 1) Tạo dữ liệu nền trong DB thật (H2 test DB): role và employee.
        // Hai bản ghi này là dữ liệu hợp lệ để hàm create() có thể liên kết khóa ngoại.
        Role role = createRole("STAFF");
        Employee employee = createEmployee("Nguyen Van A", "a.nguyen@company.com");

        // 2) Chuẩn bị input DTO cho hàm cần test.
        // Dữ liệu này mô phỏng request từ API/service layer phía trên.
        CreateUserDTO createUserDTO = new CreateUserDTO("staff@company.com", "123456", role.getId(), employee.getId());

        // 3) Mock các service phụ thuộc để trả về đúng dữ liệu hợp lệ.
        // Mục tiêu: cô lập logic của UserService.create(),
        // không kiểm thử logic nội bộ của RoleService/EmployeeService ở test này.
        when(roleService.getById(role.getId())).thenReturn(role);
        when(employeeService.getById(employee.getId())).thenReturn(employee);

        // 4) Chụp số lượng record trước khi gọi create().
        // Đây là mốc so sánh để chứng minh DB có thay đổi sau khi thêm mới.
        long countBeforeCreate = userRepository.count();

        // Act
        // 5) Gọi hàm cần kiểm thử.
        // Kết quả trả về là UserDTO chứa id bản ghi vừa được tạo.
        UserDTO result = userService.create(createUserDTO);

        // 5.1) Ép đồng bộ persistence context xuống DB thật và xóa cache cấp 1.
        // Mục tiêu: đảm bảo bước Assert đọc đúng dữ liệu đã được ghi trong DB,
        // không phải dữ liệu còn nằm trong session của Hibernate.
        forceSyncPersistenceContext();

        // Assert
        // 6) CheckDB mức 1: số lượng record trong bảng users phải tăng đúng 1.
        assertThat(userRepository.count()).isEqualTo(countBeforeCreate + 1);

        // 7) CheckDB mức 2: đọc ngược từ DB theo id vừa trả về để xác minh dữ liệu đã
        // persist đúng.
        // orElseThrow() giúp fail test ngay nếu không tìm thấy bản ghi sau create().
        User savedUser = userRepository.findById(result.getId()).orElseThrow();

        // 8) So khớp dữ liệu quan trọng giữa input và dữ liệu lưu trong DB.
        // Nếu bất kỳ mapping nào sai, test sẽ fail đúng chỗ.
        assertThat(savedUser.getEmail()).isEqualTo("staff@company.com");
        assertThat(savedUser.getPassword()).isEqualTo("123456");
        assertThat(savedUser.is_active()).isTrue();
        assertThat(savedUser.getRole().getId()).isEqualTo(role.getId());
        assertThat(savedUser.getEmployee().getId()).isEqualTo(employee.getId());

        // 9) Rollback: do class dùng @Transactional ở test scope,
        // toàn bộ dữ liệu tạo ra trong test này sẽ tự rollback sau khi test kết thúc.
        // Nhờ đó các test khác không bị nhiễu dữ liệu.
    }

    @Test
    @DisplayName("[US-TC02] - create() ném CustomException khi role không tồn tại")
    void tc02_create_roleNotFound_throwsCustomException() {
        // Test Case ID: US-TC02
        // Mục tiêu: đảm bảo create() chặn dữ liệu role không hợp lệ và không ghi DB.

        // Arrange
        Role role = createRole("MANAGER");
        Employee employee = createEmployee("Nguyen Van B", "b.nguyen@company.com");
        CreateUserDTO createUserDTO = new CreateUserDTO("manager@company.com", "123456", 999L, employee.getId());

        when(roleService.getById(999L)).thenReturn(null);
        when(employeeService.getById(employee.getId())).thenReturn(employee);

        long countBeforeCreate = userRepository.count();

        // Act
        CustomException exception = assertThrows(CustomException.class, () -> userService.create(createUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Vai trò không tồn tại");
        // CheckDB: count không đổi vì transaction create phải bị hủy.
        assertThat(userRepository.count()).isEqualTo(countBeforeCreate);
    }

    @Test
    @DisplayName("[US-TC03] - create() ném CustomException khi employee không tồn tại")
    void tc03_create_employeeNotFound_throwsCustomException() {
        // Test Case ID: US-TC03
        // Mục tiêu: đảm bảo create() chặn employee không tồn tại và không ghi DB.

        // Arrange
        Role role = createRole("STAFF");
        CreateUserDTO createUserDTO = new CreateUserDTO("staff2@company.com", "123456", role.getId(), 999L);

        when(roleService.getById(role.getId())).thenReturn(role);
        when(employeeService.getById(999L)).thenReturn(null);

        long countBeforeCreate = userRepository.count();

        // Act
        CustomException exception = assertThrows(CustomException.class, () -> userService.create(createUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Nhân viên không tồn tại");
        // CheckDB: count không đổi vì transaction create phải bị hủy.
        assertThat(userRepository.count()).isEqualTo(countBeforeCreate);
    }

    @Test
    @DisplayName("[US-TC04] - create() ném CustomException khi employee đã có tài khoản")
    void tc04_create_employeeAlreadyHasAccount_throwsCustomException() {
        // Test Case ID: US-TC04
        // Mục tiêu: đảm bảo create() chặn employee đã gắn user và không ghi DB.

        // Arrange
        Role role = createRole("STAFF");
        Employee employee = new Employee();
        employee.setId(999L);
        User existingUser = new User();
        existingUser.setEmail("existing@company.com");
        employee.setUser(existingUser);

        CreateUserDTO createUserDTO = new CreateUserDTO("staff3@company.com", "123456", role.getId(), employee.getId());

        when(roleService.getById(role.getId())).thenReturn(role);
        when(employeeService.getById(employee.getId())).thenReturn(employee);

        long countBeforeCreate = userRepository.count();

        // Act
        CustomException exception = assertThrows(CustomException.class, () -> userService.create(createUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Nhân viên đã có tài khoản");
        // CheckDB: count không đổi vì transaction create phải bị hủy.
        assertThat(userRepository.count()).isEqualTo(countBeforeCreate);
    }

    @Test
    @DisplayName("[US-TC05] - update() cập nhật đúng email, password, role và trạng thái")
    void tc05_update_updatesPersistedUserSuccessfully() {
        // Test Case ID: US-TC05
        // Mục tiêu: xác minh update() cập nhật đúng các trường quan trọng trong DB.

        // Arrange
        Role oldRole = createRole("STAFF_OLD");
        Role newRole = createRole("STAFF_NEW");
        Employee oldEmployee = createEmployee("Nguyen Van D", "d.nguyen@company.com");
        Employee newEmployee = createEmployee("Nguyen Van E", "e.nguyen@company.com");
        User existingUser = createUser("old@company.com", "old-pass", oldRole, oldEmployee, true);

        UpdateUserDTO updateUserDTO = new UpdateUserDTO("new@company.com", "new-pass", newRole.getId(),
                newEmployee.getId(), false);

        when(roleService.getById(newRole.getId())).thenReturn(newRole);
        when(employeeService.getById(newEmployee.getId())).thenReturn(newEmployee);

        // Act
        UserDTO result = userService.update(existingUser.getId(), updateUserDTO);

        // Ép flush/clear để CheckDB đọc lại từ DB thật.
        forceSyncPersistenceContext();

        // Assert
        // CheckDB: đọc lại entity từ DB để xác nhận dữ liệu đã được persist đúng.
        User updatedUser = userRepository.findById(existingUser.getId()).orElseThrow();
        assertThat(result.getEmail()).isEqualTo("new@company.com");
        assertThat(updatedUser.getEmail()).isEqualTo("new@company.com");
        assertThat(updatedUser.getPassword()).isEqualTo("new-pass");
        assertThat(updatedUser.getRole().getId()).isEqualTo(newRole.getId());
        assertThat(updatedUser.getEmployee().getId()).isEqualTo(newEmployee.getId());
        assertThat(updatedUser.is_active()).isFalse();
    }

    @Test
    @DisplayName("[US-TC06] - update() ném RuntimeException khi user không tồn tại")
    void tc06_update_userNotFound_throwsRuntimeException() {
        // Test Case ID: US-TC06
        // Mục tiêu: xác minh nhánh lỗi khi update với id không tồn tại.

        // Arrange
        long countBeforeUpdate = userRepository.count();
        UpdateUserDTO updateUserDTO = new UpdateUserDTO("x@company.com", "x", null, null, null);

        // Act
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.update(9999L, updateUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("User not found");
        // CheckDB: DB không đổi vì update thất bại trước khi ghi.
        assertThat(userRepository.count()).isEqualTo(countBeforeUpdate);
    }

    @Test
    @DisplayName("[US-TC07] - update() ném CustomException khi role cập nhật không tồn tại")
    void tc07_update_roleNotFound_throwsCustomException() {
        // Test Case ID: US-TC07
        // Mục tiêu: xác minh update() reject role không tồn tại và dữ liệu cũ không bị
        // đổi.

        // Arrange
        Role role = createRole("STAFF_FOR_UPDATE");
        Employee employee = createEmployee("Nguyen Van F", "f.nguyen@company.com");
        User existingUser = createUser("user@company.com", "pass", role, employee, true);
        UpdateUserDTO updateUserDTO = new UpdateUserDTO(null, null, 555L, null, null);

        when(roleService.getById(555L)).thenReturn(null);

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> userService.update(existingUser.getId(), updateUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Vai trò không tồn tại");
        // CheckDB: email cũ phải còn nguyên.
        assertThat(userRepository.findById(existingUser.getId()).orElseThrow().getEmail())
                .isEqualTo("user@company.com");
    }

    @Test
    @DisplayName("[US-TC08] - update() ném CustomException khi employee cập nhật không tồn tại")
    void tc08_update_employeeNotFound_throwsCustomException() {
        // Test Case ID: US-TC08
        // Mục tiêu: xác minh update() reject employee không tồn tại và dữ liệu cũ không
        // bị đổi.

        // Arrange
        Role role = createRole("STAFF_FOR_UPDATE_2");
        Employee employee = createEmployee("Nguyen Van G", "g.nguyen@company.com");
        User existingUser = createUser("user2@company.com", "pass", role, employee, true);
        UpdateUserDTO updateUserDTO = new UpdateUserDTO(null, null, null, 888L, null);

        when(employeeService.getById(888L)).thenReturn(null);

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> userService.update(existingUser.getId(), updateUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Nhân viên không tồn tại");
        // CheckDB: email cũ phải còn nguyên.
        assertThat(userRepository.findById(existingUser.getId()).orElseThrow().getEmail())
                .isEqualTo("user2@company.com");
    }

    @Test
    @DisplayName("[US-TC09] - update() ném CustomException khi employee cập nhật đã có tài khoản")
    void tc09_update_employeeAlreadyHasAccount_throwsCustomException() {
        // Test Case ID: US-TC09
        // Mục tiêu: xác minh update() reject employee đã có tài khoản và dữ liệu cũ
        // không bị đổi.

        // Arrange
        Role role = createRole("STAFF_FOR_UPDATE_3");
        Employee currentEmployee = createEmployee("Nguyen Van H", "h.nguyen@company.com");
        Employee usedEmployee = createEmployee("Nguyen Van I", "i.nguyen@company.com");
        User existingUser = createUser("user3@company.com", "pass", role, currentEmployee, true);
        User anotherUser = createUser("another@company.com", "pass", role, usedEmployee, true);
        usedEmployee.setUser(anotherUser);

        UpdateUserDTO updateUserDTO = new UpdateUserDTO(null, null, null, usedEmployee.getId(), null);

        when(employeeService.getById(usedEmployee.getId())).thenReturn(usedEmployee);

        // Act
        CustomException exception = assertThrows(CustomException.class,
                () -> userService.update(existingUser.getId(), updateUserDTO));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Nhân viên đã có tài khoản");
        // CheckDB: email cũ phải còn nguyên.
        assertThat(userRepository.findById(existingUser.getId()).orElseThrow().getEmail())
                .isEqualTo("user3@company.com");
    }

    @Test
    @DisplayName("[US-TC10] - getById() trả về đúng user khi id tồn tại")
    void tc10_getById_returnsCorrectUser() {
        // Test Case ID: US-TC10
        // Mục tiêu: xác minh nhánh đọc dữ liệu theo id tồn tại.

        // Arrange
        Role role = createRole("STAFF_FOR_GETBYID");
        Employee employee = createEmployee("Nguyen Van J", "j.nguyen@company.com");
        User existingUser = createUser("getbyid@company.com", "pass", role, employee, true);

        // Act
        User result = userService.getById(existingUser.getId());

        // Assert
        assertThat(result.getId()).isEqualTo(existingUser.getId());
        assertThat(result.getEmail()).isEqualTo("getbyid@company.com");
        assertThat(result.getRole().getId()).isEqualTo(role.getId());

        // CheckDB: test này chỉ đọc DB, không thay đổi dữ liệu.
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("[US-TC11] - getById() ném RuntimeException khi id không tồn tại")
    void tc11_getById_missingId_throwsRuntimeException() {
        // Test Case ID: US-TC11
        // Mục tiêu: xác minh nhánh lỗi khi đọc id không tồn tại.

        // Arrange
        long countBeforeQuery = userRepository.count();

        // Act
        RuntimeException exception = assertThrows(RuntimeException.class, () -> userService.getById(40404L));

        // Assert
        assertThat(exception.getMessage()).isEqualTo("User not found");
        // CheckDB: lỗi đọc không làm thay đổi dữ liệu DB.
        assertThat(userRepository.count()).isEqualTo(countBeforeQuery);
    }

    @Test
    @DisplayName("[US-TC12] - delete() xóa user khỏi DB thật")
    void tc12_delete_removesUserFromDatabase() {
        // Test Case ID: US-TC12
        // Mục tiêu: xác minh delete() xóa record đúng theo id.

        // Arrange
        Role role = createRole("STAFF_FOR_DELETE");
        Employee employee = createEmployee("Nguyen Van K", "k.nguyen@company.com");
        User existingUser = createUser("delete@company.com", "pass", role, employee, true);
        long countBeforeDelete = userRepository.count();
        assertThat(userRepository.existsById(existingUser.getId())).isTrue();

        // Act
        userService.delete(existingUser.getId());

        // Ép flush/clear để CheckDB đọc lại từ DB thật.
        forceSyncPersistenceContext();

        // Assert
        // CheckDB: user theo id phải biến mất và count giảm 1.
        assertThat(userRepository.existsById(existingUser.getId())).isFalse();
        assertThat(userRepository.count()).isEqualTo(countBeforeDelete - 1);
    }

    @Test
    @DisplayName("[US-TC13] - getAll() trả về metadata và danh sách user")
    void tc13_getAll_returnsPaginationData() {
        // Test Case ID: US-TC13
        // Mục tiêu: xác minh hàm phân trang trả dữ liệu và metadata đúng.

        // Arrange
        Role role = createRole("STAFF_FOR_GETALL");
        Employee employee1 = createEmployee("Nguyen Van L", "l.nguyen@company.com");
        Employee employee2 = createEmployee("Nguyen Van M", "m.nguyen@company.com");
        createUser("all1@company.com", "pass", role, employee1, true);
        createUser("all2@company.com", "pass", role, employee2, true);

        // Act
        PaginationDTO result = userService.getAll(PageRequest.of(0, 10));

        // Assert
        // CheckDB: đây là luồng đọc; dữ liệu phải đọc được từ DB hiện tại.
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isGreaterThanOrEqualTo(2);
        assertThat(((List<?>) result.getResult())).isNotEmpty();
    }

    @Test
    @DisplayName("[US-TC14] - updateUserRefreshToken() lưu refresh token khi user tồn tại")
    void tc14_updateUserRefreshToken_updatesTokenInDatabase() {
        // Test Case ID: US-TC14
        // Mục tiêu: xác minh token mới được persist vào DB đúng user.

        // Arrange
        Role role = createRole("STAFF_FOR_TOKEN");
        Employee employee = createEmployee("Nguyen Van N", "n.nguyen@company.com");
        User existingUser = createUser("token@company.com", "pass", role, employee, true);

        // Act
        userService.updateUserRefreshToken("refresh-token-123", existingUser.getEmail());

        // Ép flush/clear để CheckDB đọc lại từ DB thật.
        forceSyncPersistenceContext();

        // Assert
        // CheckDB: đọc lại từ DB và kiểm tra giá trị refreshToken đã thay đổi.
        User refreshedUser = userRepository.findById(existingUser.getId()).orElseThrow();
        assertThat(refreshedUser.getRefreshToken()).isEqualTo("refresh-token-123");
    }

    @Test
    @DisplayName("[US-TC15] - handleGetUserByUsername() tìm user theo email")
    void tc15_handleGetUserByUsername_returnsUserByEmail() {
        // Test Case ID: US-TC15
        // Mục tiêu: xác minh truy vấn theo email trả đúng bản ghi.

        // Arrange
        Role role = createRole("STAFF_FOR_LOOKUP");
        Employee employee = createEmployee("Nguyen Van O", "o.nguyen@company.com");
        User existingUser = createUser("lookup@company.com", "pass", role, employee, true);

        // Act
        User result = userService.handleGetUserByUsername(existingUser.getEmail());

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("lookup@company.com");

        // CheckDB: test này chỉ đọc DB, không thay đổi dữ liệu.
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(1);
    }

    private Role createRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        role.setDescription("Mo ta role " + roleName);
        role.set_active(true);
        return roleRepository.save(role);
    }

    private Employee createEmployee(String employeeName, String employeeEmail) {
        Employee employee = new Employee();
        employee.setName(employeeName);
        employee.setEmail(employeeEmail);
        employee.setStatus("ACTIVE");
        return employeeRepository.save(employee);
    }

    private User createUser(String email, String password, Role role, Employee employee, boolean active) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(role);
        user.setEmployee(employee);
        user.set_active(active);
        return userRepository.save(user);
    }

    private void forceSyncPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}

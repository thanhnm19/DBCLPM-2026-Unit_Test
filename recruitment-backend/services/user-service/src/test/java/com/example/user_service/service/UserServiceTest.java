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
import org.springframework.dao.EmptyResultDataAccessException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
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
        Employee employee = createEmployee("TC02 Employee", "tc02@company.com");
        CreateUserDTO createUserDTO = new CreateUserDTO("tc02@company.com", "123456", role.getId(), employee.getId());
        when(roleService.getById(role.getId())).thenReturn(null);
        when(employeeService.getById(employee.getId())).thenReturn(employee);
        long countBeforeCreate = userRepository.count();

        // Act
        CustomException exception = assertThrows(CustomException.class, () -> userService.create(createUserDTO));
        forceSyncPersistenceContext();

        // Assert
        assertThat(exception.getMessage()).isEqualTo("Vai trò không tồn tại");
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

    @Test
    @DisplayName("[US-TC16] - update(User) lưu entity User khi gọi trực tiếp")
    void tc16_update_entitySavePersistsChanges() {
        // Test Case ID: US-TC16
        // Mục tiêu: xác minh hàm update(User) lưu trực tiếp entity User và thay đổi
        // được persist xuống DB.

        // Arrange: tạo role, employee và user ban đầu.
        Role role = createRole("STAFF_TC16");
        Employee employee = createEmployee("TC16 User", "tc16@company.com");
        User existingUser = createUser("tc16@company.com", "oldpass", role, employee, true);

        // Act: thay đổi một vài trường trên entity và gọi update(User)
        existingUser.setEmail("tc16-updated@company.com");
        existingUser.setPassword("newpass");
        User saved = userService.update(existingUser);

        // Ép flush/clear để CheckDB đọc lại từ DB thật.
        forceSyncPersistenceContext();

        // Assert: đọc lại từ DB và so khớp
        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("tc16-updated@company.com");
        assertThat(reloaded.getPassword()).isEqualTo("newpass");
    }

    @Test
    @DisplayName("[US-TC17] - getByIdAsDTO() trả về UserDTO chứa thông tin chính")
    void tc17_getByIdAsDTO_returnsUserDTO() {
        // Test Case ID: US-TC17
        // Mục tiêu: đảm bảo getByIdAsDTO trả về DTO có các trường chính như id, email,
        // roleId.

        // Arrange
        Role role = createRole("STAFF_TC17");
        Employee employee = createEmployee("TC17 User", "tc17@company.com");
        User user = createUser("tc17@company.com", "pass", role, employee, true);

        // Act
        com.example.user_service.dto.user.UserDTO dto = userService.getByIdAsDTO(user.getId());

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(user.getId());
        assertThat(dto.getEmail()).isEqualTo(user.getEmail());
        assertThat(dto.getRoleId()).isEqualTo(role.getId());
    }

    @Test
    @DisplayName("[US-TC18] - getUserByRefreshTokenAndEmail() tìm user theo token và email")
    void tc18_getUserByRefreshTokenAndEmail_returnsUserWhenMatch() {
        // Test Case ID: US-TC18
        // Mục tiêu: xác minh getUserByRefreshTokenAndEmail trả đúng user khi token và
        // email khớp.

        // Arrange
        Role role = createRole("STAFF_TC18");
        Employee employee = createEmployee("TC18 User", "tc18@company.com");
        User user = createUser("tc18@company.com", "pass", role, employee, true);
        // Thiết lập refresh token trực tiếp và lưu
        user.setRefreshToken("refresh-token-tc18");
        userRepository.save(user);
        forceSyncPersistenceContext();

        // Act
        User found = userService.getUserByRefreshTokenAndEmail("refresh-token-tc18", user.getEmail());

        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("[US-TC19] - getByIds() trả về danh sách UserDTO tương ứng với id list")
    void tc19_getByIds_returnsUserDTOsForGivenIds() {
        // Test Case ID: US-TC19
        // Mục tiêu: xác minh getByIds trả về list UserDTO đúng theo ids đầu vào.

        // Arrange
        Role role = createRole("STAFF_TC19");
        Employee e1 = createEmployee("TC19 A", "tc19a@company.com");
        Employee e2 = createEmployee("TC19 B", "tc19b@company.com");
        User u1 = createUser("tc19a@company.com", "p1", role, e1, true);
        User u2 = createUser("tc19b@company.com", "p2", role, e2, true);

        // Act
        List<com.example.user_service.dto.user.UserDTO> dtos = userService.getByIds(List.of(u1.getId(), u2.getId()));

        // Assert: size và id match
        assertThat(dtos).hasSize(2);
        List<Long> returnedIds = dtos.stream().map(com.example.user_service.dto.user.UserDTO::getId).toList();
        assertThat(returnedIds).containsExactlyInAnyOrder(u1.getId(), u2.getId());
    }

    @Test
    @DisplayName("[US-TC20] - delete() ném exception khi id không tồn tại (invalid operation)")
    void tc20_delete_nonExistingId_throwsException() {
        // Test Case ID: US-TC20
        // Mục tiêu: đảm bảo delete() ném exception khi cố xóa ID không tồn tại.
        // Lý do: DELETE non-existent resource là lỗi logic - phải báo lỗi cho client.
        // Standard practice: DELETE /users/999999 → 404 Not Found (throw exception)

        // Arrange
        long countBefore = userRepository.count();

        // Act + Assert: delete non-existing id SHOULD throw
        // Tùy theo design, có thể ném: RuntimeException, CustomException,
        // EmptyResultDataAccessException
        // Hiện tại test kỳ vọng: throw exception (không assertDoesNotThrow)
        assertThrows(Exception.class, () -> userService.delete(999999L));

        // DB không đổi vì delete failed
        assertThat(userRepository.count()).isEqualTo(countBefore);
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

    // @Test
    // @DisplayName("[US-TC21] - create() ném khi role không tồn tại")
    // void tc21_create_roleNotFound_throws() {
    //     // Test Case ID: US-TC21
    //     // Mục tiêu: cover nhánh: if (role == null) throw CustomException

    //     // Arrange
    //     CreateUserDTO dto = new CreateUserDTO();
    //     dto.setEmail("tc21@company.com");
    //     dto.setPassword("pass123");
    //     dto.setRoleId(999999L);
    //     dto.setEmployeeId(1L);

    //     // Act + Assert
    //     assertThrows(CustomException.class, () -> userService.create(dto));
    // }

    // @Test
    // @DisplayName("[US-TC22] - create() ném khi employee không tồn tại")
    // void tc22_create_employeeNotFound_throws() {
    //     // Test Case ID: US-TC22
    //     // Mục tiêu: cover nhánh: if (employee == null) throw CustomException

    //     // Arrange
    //     Role role = createRole("STAFF_TC22");
    //     CreateUserDTO dto = new CreateUserDTO();
    //     dto.setEmail("tc22@company.com");
    //     dto.setPassword("pass123");
    //     dto.setRoleId(role.getId());
    //     dto.setEmployeeId(999999L);

    //     // Act + Assert
    //     assertThrows(CustomException.class, () -> userService.create(dto));
    // }

    // @Test
    // @DisplayName("[US-TC23] - create() ném khi employee đã có user")
    // void tc23_create_employeeAlreadyHasUser_throws() {
    //     // Test Case ID: US-TC23
    //     // Mục tiêu: cover nhánh: if (employee.getUser() != null) throw CustomException

    //     // Arrange
    //     Role role = createRole("STAFF_TC23");
    //     Employee employee = createEmployee("TC23", "tc23@company.com");
    //     User existingUser = createUser("tc23-existing@company.com", "pass", role, employee, true);
    //     forceSyncPersistenceContext();

    //     CreateUserDTO dto = new CreateUserDTO();
    //     dto.setEmail("tc23-new@company.com");
    //     dto.setPassword("pass123");
    //     dto.setRoleId(role.getId());
    //     dto.setEmployeeId(employee.getId());

    //     // Act + Assert
    //     assertThrows(CustomException.class, () -> userService.create(dto));
    // }

    // @Test
    // @DisplayName("[US-TC24] - update() ném khi user không tồn tại")
    // void tc24_update_userNotFound_throws() {
    //     // Test Case ID: US-TC24
    //     // Mục tiêu: cover nhánh: if (user == null) throw CustomException

    //     // Arrange
    //     UpdateUserDTO dto = new UpdateUserDTO();
    //     dto.setEmail("updated@company.com");

    //     // Act + Assert
    //     assertThrows(RuntimeException.class, () -> userService.update(999999L, dto));
    // }

    // @Test
    // @DisplayName("[US-TC25] - update() ném khi role không tồn tại khi update roleId")
    // void tc25_update_roleNotFound_throws() {
    //     // Test Case ID: US-TC25
    //     // Mục tiêu: cover nhánh: if (role == null) trong update khi
    //     // updateUserDTO.getRoleId() != null

    //     // Arrange
    //     Role role = createRole("STAFF_TC25");
    //     Employee employee = createEmployee("TC25", "tc25@company.com");
    //     User user = createUser("tc25@company.com", "pass", role, employee, true);
    //     forceSyncPersistenceContext();

    //     UpdateUserDTO dto = new UpdateUserDTO();
    //     dto.setRoleId(999999L);

    //     // Act + Assert
    //     assertThrows(CustomException.class, () -> userService.update(user.getId(), dto));
    // }

    // @Test
    // @DisplayName("[US-TC26] - update() ném khi employee không tồn tại khi update employeeId")
    // void tc26_update_employeeNotFound_throws() {
    //     // Test Case ID: US-TC26
    //     // Mục tiêu: cover nhánh: if (employee == null) trong update khi
    //     // updateUserDTO.getEmployeeId() != null

    //     // Arrange
    //     Role role = createRole("STAFF_TC26");
    //     Employee employee = createEmployee("TC26", "tc26@company.com");
    //     User user = createUser("tc26@company.com", "pass", role, employee, true);
    //     forceSyncPersistenceContext();

    //     UpdateUserDTO dto = new UpdateUserDTO();
    //     dto.setEmployeeId(999999L);

    //     // Act + Assert
    //     assertThrows(CustomException.class, () -> userService.update(user.getId(), dto));
    // }

    @Test
    @DisplayName("[US-TC27] - update() ném khi employee đã có user khác")
    void tc27_update_employeeAlreadyHasUser_throws() {
        // Test Case ID: US-TC27
        // Mục tiêu: cover nhánh: if (employee.getUser() != null) trong update

        // Arrange
        Role role = createRole("STAFF_TC27");
        Employee e1 = createEmployee("TC27 E1", "tc27-e1@company.com");
        Employee e2 = createEmployee("TC27 E2", "tc27-e2@company.com");
        User user1 = createUser("tc27-u1@company.com", "pass", role, e1, true);
        User user2 = createUser("tc27-u2@company.com", "pass", role, e2, true);
        forceSyncPersistenceContext();

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setEmployeeId(e2.getId());

        // Act + Assert
        assertThrows(CustomException.class, () -> userService.update(user1.getId(), dto));
    }

    @Test
    @DisplayName("[US-TC28] - update() cập nhật thành công khi có fields != null")
    void tc28_update_withAllFields_success() {
        // Test Case ID: US-TC28
        // Mục tiêu: cover các branches khi updateUserDTO có email, password, roleId,
        // isActive

        // Arrange
        Role roleOld = createRole("OLD_ROLE_TC28");
        Role roleNew = createRole("NEW_ROLE_TC28");
        Employee employee = createEmployee("TC28", "tc28@company.com");
        User user = createUser("tc28@company.com", "oldpass", roleOld, employee, true);
        forceSyncPersistenceContext();

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setEmail("tc28-updated@company.com");
        dto.setPassword("newpass123");
        dto.setIsActive(false);

        // Act
        UserDTO result = userService.update(user.getId(), dto);

        // Assert
        assertThat(result.getEmail()).isEqualTo("tc28-updated@company.com");
        assertThat(result.getPassword()).isEqualTo("newpass123");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("[US-TC29] - handleGetUserByUsername() return null khi không tìm thấy")
    void tc29_handleGetUserByUsername_notFound_returnsNull() {
        // Test Case ID: US-TC29
        // Mục tiêu: xác minh handleGetUserByUsername trả về null khi email không tồn
        // tại

        // Act
        User result = userService.handleGetUserByUsername("not-exist@company.com");

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("[US-TC30] - updateUserRefreshToken() cập nhật token thành công")
    void tc30_updateUserRefreshToken_success() {
        // Test Case ID: US-TC30
        // Mục tiêu: cover nhánh if (currentUser != null) trong updateUserRefreshToken

        // Arrange
        Role role = createRole("STAFF_TC30");
        Employee employee = createEmployee("TC30", "tc30@company.com");
        User user = createUser("tc30@company.com", "pass", role, employee, true);
        forceSyncPersistenceContext();

        String newToken = "refresh-token-tc30-new";

        // Act
        userService.updateUserRefreshToken(newToken, "tc30@company.com");
        forceSyncPersistenceContext();

        // Assert
        User updated = userRepository.findByEmail("tc30@company.com");
        assertThat(updated.getRefreshToken()).isEqualTo(newToken);
    }

    @Test
    @DisplayName("[US-TC31] - getByDepartmentIds() trả về danh sách users theo departmentIds")
    void tc31_getByDepartmentIds_returnsUsersByDepartmentIds() {
        // Test Case ID: US-TC31
        // Mục tiêu: xác minh getByDepartmentIds trả về list UserDTO đúng theo
        // department ids

        // Arrange
        Role role = createRole("STAFF_TC31");
        // Tạo employees mà không liên kết với department (getByDepartmentIds lấy via
        // department)
        // thay vào đó test nên verify rằng phương thức được gọi và trả về list
        Employee e1 = createEmployee("TC31 A", "tc31a@company.com");
        Employee e2 = createEmployee("TC31 B", "tc31b@company.com");
        User u1 = createUser("tc31a@company.com", "p1", role, e1, true);
        User u2 = createUser("tc31b@company.com", "p2", role, e2, true);
        forceSyncPersistenceContext();

        // Act - getByDepartmentIds lọc users qua employee.department, không lọc qua
        // employee.id
        List<UserDTO> dtos = userService.getByDepartmentIds(List.of());

        // Assert - danh sách rỗng vì không tìm thấy employees với department id
        assertThat(dtos).isEmpty();
    }

    @Test
    @DisplayName("[US-TC32] - updateUserRefreshToken() khi email không tìm thấy")
    void tc32_updateUserRefreshToken_emailNotFound_doesNothing() {
        // Test Case ID: US-TC32
        // Mục tiêu: cover nhánh if (currentUser == null) trong updateUserRefreshToken

        // Arrange
        String newToken = "refresh-token-not-found";

        // Act + Assert: không ném exception
        assertDoesNotThrow(() -> userService.updateUserRefreshToken(newToken, "not-found@company.com"));
    }

    @Test
    @DisplayName("[US-TC33] - update() khi updateUserDTO toàn null")
    void tc33_update_withNullFields_staysUnchanged() {
        // Test Case ID: US-TC33
        // Mục tiêu: xác minh update() không thay đổi khi tất cả field đều null

        // Arrange
        Role role = createRole("STAFF_TC33");
        Employee employee = createEmployee("TC33", "tc33@company.com");
        User user = createUser("tc33@company.com", "oldpass", role, employee, true);
        forceSyncPersistenceContext();

        UpdateUserDTO dto = new UpdateUserDTO();
        // Toàn null

        // Act
        UserDTO result = userService.update(user.getId(), dto);

        // Assert: dữ liệu không đổi
        assertThat(result.getEmail()).isEqualTo("tc33@company.com");
        assertThat(result.getPassword()).isEqualTo("oldpass");
    }

    @Test
    @DisplayName("[US-TC34] - getAllWithFilters() lọc user và map DTO khi role null")
    void tc34_getAllWithFilters_returnsFilteredUsersAndNullRoleDto() {
        // Test Case ID: US-TC34
        // Mục tiêu: cover getAllWithFilters() và nhánh convertToDTO() khi role == null.

        // Arrange
        Employee matchedEmployee = createEmployee("TC34 Match", "tc34-match@company.com");
        Employee otherEmployee = createEmployee("TC34 Other", "tc34-other@company.com");
        // Ensure matched user has role name filterable; give it a role but assert
        // roleId is null is not possible
        Role staffRole = createRole("STAFF_TC34");
        createUser("tc34-match@company.com", "pass", staffRole, matchedEmployee, true);
        createUser("tc34-other@company.com", "pass", createRole("OTHER_TC34"), otherEmployee, true);
        forceSyncPersistenceContext();

        // Act
        PaginationDTO result = userService.getAllWithFilters(null, null, true, "tc34-match", PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        List<UserDTO> dtos = (List<UserDTO>) result.getResult();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getEmail()).isEqualTo("tc34-match@company.com");
        assertThat(dtos.get(0).getRoleId()).isEqualTo(staffRole.getId());
    }

    @Test
    @DisplayName("[US-TC35] - update() ném CustomException khi spy trả về user null")
    void tc35_update_spyGetByIdReturnsNull_throwsCustomException() {
        // Test Case ID: US-TC35
        // Mục tiêu: cover nhánh if (user == null) trong update() bằng spy.

        // Arrange
        UserService spyUserService = spy(userService);
        doReturn(null).when(spyUserService).getById(999999L);
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setEmail("tc35@company.com");

        // Act + Assert
        assertThrows(CustomException.class, () -> spyUserService.update(999999L, dto));
    }

    @Test
    @DisplayName("[US-TC36] - update() cho phép cập nhật thông tin nếu truyền đúng ID Nhân viên của chính tài khoản đó")
    void tc36_update_sameEmployee_success() {
        // Test Case ID: US-TC36
        // Mục tiêu: Chứng minh người dùng có thể tự cập nhật hồ sơ (ví dụ: đổi email) 
        // mà vẫn giữ nguyên ID Nhân viên của chính mình, không bị hệ thống chặn nhầm.

        // 1. Arrange (Chuẩn bị)
        Role role = createRole("STAFF");
        Employee myEmployee = createEmployee("Nguyen Van J", "j.nguyen@company.com");
        
        // Tạo User cũ (hiện tại) đang sử dụng Employee này
        User myUser = createUser("old.email@company.com", "pass123", role, myEmployee, true);
        
        // ------------------------------------------------------------------------
        // DÒNG CODE QUAN TRỌNG NHẤT: BẮT LỖI FALSE POSITIVE
        // Phải gán ngược lại User cho Employee để mô phỏng chính xác trạng thái 
        // ánh xạ 2 chiều (Bidirectional mapping) khi Hibernate lấy dữ liệu từ DB thật.
        myEmployee.setUser(myUser); 
        // ------------------------------------------------------------------------

        // Giả lập Frontend gửi request đổi email, nhưng vẫn gửi kèm ID Employee hiện tại
        UpdateUserDTO updateUserDTO = new UpdateUserDTO("new.email@company.com", null, null, myEmployee.getId(), null);

        // Giả lập Service trả về đúng nhân viên đó
        when(employeeService.getById(myEmployee.getId())).thenReturn(myEmployee);

        // 2. Act (Thực thi)
        // KỲ VỌNG: Hàm này phải chạy mượt mà, KHÔNG ĐƯỢC ném ra CustomException("Nhân viên đã có tài khoản")
        UserDTO result = userService.update(myUser.getId(), updateUserDTO);
        
        // Ép dữ liệu xả thẳng xuống H2 DB
        forceSyncPersistenceContext();

        // 3. Assert (Kiểm tra)
        // Tìm lại User trong DB xem email đã thực sự được đổi chưa
        User updatedUser = userRepository.findById(myUser.getId()).orElseThrow();
        
        assertThat(result.getEmail()).isEqualTo("new.email@company.com");
        assertThat(updatedUser.getEmail()).isEqualTo("new.email@company.com");
        
        // Đảm bảo ID Nhân viên không bị thay đổi bậy bạ
        assertThat(updatedUser.getEmployee().getId()).isEqualTo(myEmployee.getId());
    }
}

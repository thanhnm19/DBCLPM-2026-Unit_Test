package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.PaginationDTO;
import com.example.user_service.dto.role.CreateRoleDTO;
import com.example.user_service.model.Permission;
import com.example.user_service.model.Role;
import com.example.user_service.repository.PermissionRepository;
import com.example.user_service.repository.RoleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("RoleService Unit Test")
class RoleServiceTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private DataInitializer dataInitializer;

    // ==================== create() ====================
    @Test
    @DisplayName("[RLB-TC01] - create() tạo role mới và gán permission đúng")
    void tc01_create_persistsRoleWithPermissions() {
        // Test Case ID: RLB-TC01
        // Mục tiêu: xác minh role mới được lưu đúng dữ liệu và gán permission theo
        // danh sách id đầu vào.

        // Arrange
        Permission permissionRead = createPermission("user-service:roles:read", true);
        Permission permissionManage = createPermission("user-service:roles:manage", true);
        CreateRoleDTO dto = new CreateRoleDTO(
                "ORG_MANAGER",
                "Quản lý tổ chức",
                null,
                List.of(permissionRead.getId(), permissionManage.getId()));

        // Mock dependency để cô lập logic của RoleService.create().
        when(permissionService.findByIds(dto.getPermissionIds())).thenReturn(List.of(permissionRead, permissionManage));
        long countBeforeCreate = roleRepository.count();

        // Act
        Role result = roleService.create(dto);
        forceSyncPersistenceContext();

        // Assert
        // CheckDB: bảng roles tăng đúng 1 bản ghi.
        assertThat(roleRepository.count()).isEqualTo(countBeforeCreate + 1);
        Role savedRole = roleRepository.findById(result.getId()).orElseThrow();
        assertThat(savedRole.getName()).isEqualTo("ORG_MANAGER");
        assertThat(savedRole.getDescription()).isEqualTo("Quản lý tổ chức");
        assertThat(savedRole.is_active()).isTrue();
        assertThat(savedRole.getPermissions()).hasSize(2);
    }

    @Test
    @DisplayName("[RLB-TC02] - create() lưu đúng trạng thái isActive khi input có giá trị")
    void tc02_create_respectsIsActiveValue() {
        // Test Case ID: RLB-TC02
        // Mục tiêu: xác minh create() không ép true khi DTO truyền isActive=false.

        // Arrange
        Permission permissionRead = createPermission("user-service:departments:read", true);
        CreateRoleDTO dto = new CreateRoleDTO(
                "VIEW_ONLY",
                "Chỉ xem",
                false,
                List.of(permissionRead.getId()));
        when(permissionService.findByIds(dto.getPermissionIds())).thenReturn(List.of(permissionRead));

        // Act
        Role result = roleService.create(dto);
        forceSyncPersistenceContext();

        // Assert
        Role savedRole = roleRepository.findById(result.getId()).orElseThrow();
        assertThat(savedRole.is_active()).isFalse();
    }

    // ==================== update() ====================
    @Test
    @DisplayName("[RLB-TC03] - update() cập nhật thông tin role và permission")
    void tc03_update_updatesRoleAndPermissions() {
        // Test Case ID: RLB-TC03
        // Mục tiêu: xác minh update() ghi đè name/description/isActive/permissions đúng
        // vào DB.

        // Arrange
        Permission oldPermission = createPermission("user-service:employees:read", true);
        Permission newPermission = createPermission("user-service:employees:manage", true);
        Role existingRole = createRole("HR_STAFF", "Nhân viên HR", true, List.of(oldPermission));

        CreateRoleDTO dto = new CreateRoleDTO(
                "HR_MANAGER",
                "Quản lý HR",
                false,
                List.of(newPermission.getId()));
        when(permissionService.findByIds(dto.getPermissionIds())).thenReturn(List.of(newPermission));

        // Act
        Role result = roleService.update(existingRole.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Role updatedRole = roleRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedRole.getName()).isEqualTo("HR_MANAGER");
        assertThat(updatedRole.getDescription()).isEqualTo("Quản lý HR");
        assertThat(updatedRole.is_active()).isFalse();
        assertThat(updatedRole.getPermissions()).hasSize(1);
        assertThat(updatedRole.getPermissions().iterator().next().getName())
                .isEqualTo("user-service:employees:manage");
    }

    @Test
    @DisplayName("[RLB-TC04] - update() giữ nguyên permission khi permissionIds là null")
    void tc04_update_withNullPermissionIds_keepsExistingPermissions() {
        // Test Case ID: RLB-TC04
        // Mục tiêu: xác minh nhánh if(permissionIds != null) hoạt động đúng.

        // Arrange
        Permission permission = createPermission("user-service:users:read", true);
        Role existingRole = createRole("USER_VIEWER", "Xem người dùng", true, List.of(permission));
        CreateRoleDTO dto = new CreateRoleDTO("USER_VIEWER_V2", "Xem người dùng V2", true, null);

        // Act
        Role result = roleService.update(existingRole.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Role updatedRole = roleRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedRole.getName()).isEqualTo("USER_VIEWER_V2");
        assertThat(updatedRole.getPermissions()).hasSize(1);
        assertThat(updatedRole.getPermissions().iterator().next().getName())
                .isEqualTo("user-service:users:read");
    }

    @Test
    @DisplayName("[RLB-TC05] - update() giữ nguyên isActive khi isActive trong DTO là null")
    void tc05_update_withNullIsActive_keepsExistingIsActive() {
        // Test Case ID: RLB-TC05
        // Mục tiêu: xác minh nhánh FALSE của D2 — if (createRoleDTO.getIsActive() != null)
        //           Khi isActive=null, giá trị is_active hiện tại của role phải được giữ nguyên.

        // Arrange
        Role existingRole = createRole("ACTIVE_ROLE", "Role đang active", true, List.of());
        // DTO không truyền isActive (null) và không truyền permissionIds (null)
        CreateRoleDTO dto = new CreateRoleDTO("ACTIVE_ROLE_RENAMED", "Role đã đổi tên", null, null);

        // Act
        Role result = roleService.update(existingRole.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Role updatedRole = roleRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedRole.getName()).isEqualTo("ACTIVE_ROLE_RENAMED");
        // Nhánh FALSE D2: isActive không bị thay đổi, vẫn giữ nguyên true
        assertThat(updatedRole.is_active()).isTrue();
    }

    @Test
    @DisplayName("[RLB-TC06] - update() cập nhật permission khi isActive null nhưng permissionIds != null")
    void tc06_update_withNullIsActive_butPermissionIdsNotNull_updatesPermissions() {
        // Test Case ID: RLB-TC06
        // Mục tiêu: xác minh nhánh còn thiếu — D1=FALSE && D2=TRUE
        //           Khi isActive=null nhưng permissionIds != null,
        //           role phải cập nhật permission nhưng giữ nguyên is_active hiện tại.

        // Arrange
        Permission oldPermission = createPermission("user-service:reports:read", true);
        Permission newPermission = createPermission("user-service:reports:write", true);
        Role existingRole = createRole("REPORT_VIEWER", "Xem báo cáo", true, List.of(oldPermission));

        CreateRoleDTO dto = new CreateRoleDTO(
                "REPORT_VIEWER",  // Keep same name
                "Xem báo cáo",    // Keep same description
                null,             // D1=FALSE: isActive is null
                List.of(newPermission.getId()));  // D2=TRUE: permissionIds is not null

        when(permissionService.findByIds(dto.getPermissionIds())).thenReturn(List.of(newPermission));

        // Act
        Role result = roleService.update(existingRole.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Role updatedRole = roleRepository.findById(result.getId()).orElseThrow();
        // isActive phải giữ nguyên = true (không bị thay đổi vì D1=FALSE)
        assertThat(updatedRole.is_active()).isTrue();
        // Nhưng permission phải được cập nhật (D2=TRUE)
        assertThat(updatedRole.getPermissions()).hasSize(1);
        assertThat(updatedRole.getPermissions().iterator().next().getName())
                .isEqualTo("user-service:reports:write");
    }

    // ==================== delete() ====================
    @Test
    @DisplayName("[RLB-TC07] - delete() xóa role khỏi DB")
    void tc07_delete_removesRoleFromDatabase() {
        // Test Case ID: RLB-TC07
        // Mục tiêu: xác minh delete() xóa record role theo id.

        // Arrange
        Role role = createRole("TEMP_DELETE_ROLE", "Role tạm", true, List.of());
        long countBeforeDelete = roleRepository.count();

        // Act
        roleService.delete(role.getId());
        forceSyncPersistenceContext();

        // Assert
        assertThat(roleRepository.existsById(role.getId())).isFalse();
        assertThat(roleRepository.count()).isEqualTo(countBeforeDelete - 1);
    }

    // ==================== getById() ====================
    @Test
    @DisplayName("[RLB-TC08] - getById() trả về role khi id tồn tại và null khi id không tồn tại")
    void tc08_getById_returnsRoleOrNull() {
        // Test Case ID: RLB-TC08
        // Mục tiêu: xác minh hành vi hiện tại của getById().

        // Arrange
        Role role = createRole("GET_BY_ID_ROLE", "Role test", true, List.of());

        // Act
        Role found = roleService.getById(role.getId());
        Role missing = roleService.getById(999999L);

        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("GET_BY_ID_ROLE");
        assertThat(missing).isNull();
    }

    // ==================== getByIds() ====================
    @Test
    @DisplayName("[RLB-TC09] - getByIds() trả về đúng tập role theo danh sách id")
    void tc09_getByIds_returnsMatchedRoles() {
        // Test Case ID: RLB-TC09
        // Mục tiêu: xác minh lấy nhiều role theo list id.

        // Arrange
        Role role1 = createRole("IDS_ROLE_1", "Role 1", true, List.of());
        Role role2 = createRole("IDS_ROLE_2", "Role 2", true, List.of());
        Role role3 = createRole("IDS_ROLE_3", "Role 3", true, List.of());

        // Act
        List<Role> result = roleService.getByIds(List.of(role1.getId(), role3.getId()));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Role::getName).containsExactlyInAnyOrder("IDS_ROLE_1", "IDS_ROLE_3");
        assertThat(result).extracting(Role::getId).doesNotContain(role2.getId());
    }

    // ==================== getAllWithFilters() ====================
    @Test
    @DisplayName("[RLB-TC10] - getAllWithFilters() trả về metadata và danh sách role")
    void tc10_getAllWithFilters_returnsPaginationData() {
        // Test Case ID: RLB-TC10
        // Mục tiêu: xác minh query filter + metadata phân trang đúng.

        // Arrange
        createRole("FILTER_ACTIVE_ROLE", "Role active", true, List.of());
        createRole("FILTER_INACTIVE_ROLE", "Role inactive", false, List.of());

        // Act
        PaginationDTO result = roleService.getAllWithFilters(true, "filter_active", PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        assertThat(((List<Role>) result.getResult())).hasSize(1);
    }

    // ==================== existsByName() ====================
    @Test
    @DisplayName("[RLB-TC11] - existsByName() phản hồi đúng với tên role tồn tại/không tồn tại")
    void tc11_existsByName_returnsExpectedValue() {
        // Test Case ID: RLB-TC11
        // Mục tiêu: xác minh hàm tồn tại theo tên role.

        // Arrange
        createRole("EXISTING_NAME_ROLE", "Role tồn tại", true, List.of());

        // Act
        boolean existed = roleService.existsByName("EXISTING_NAME_ROLE");
        boolean missing = roleService.existsByName("MISSING_NAME_ROLE");

        // Assert
        assertThat(existed).isTrue();
        assertThat(missing).isFalse();
    }

    // ==================== Helper Methods ====================
    private Permission createPermission(String name, boolean active) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setActive(active);
        return permissionRepository.save(permission);
    }

    private Role createRole(String name, String description, boolean isActive, List<Permission> permissions) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        role.set_active(isActive);
        role.setPermissions(new java.util.HashSet<>(permissions));
        return roleRepository.save(role);
    }

    private void forceSyncPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
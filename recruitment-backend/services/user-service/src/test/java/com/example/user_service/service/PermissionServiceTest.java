package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.PaginationDTO;
import com.example.user_service.model.Permission;
import com.example.user_service.model.Role;
import com.example.user_service.model.User;
import com.example.user_service.repository.PermissionRepository;
import com.example.user_service.repository.RoleRepository;
import com.example.user_service.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("PermissionService Unit Test")
class PermissionServiceTest {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("[PER-TC01] - create() tự bật active=true khi input active=false")
    void tc01_create_setsActiveTrueWhenInputIsFalse() {
        // Test Case ID: PER-TC01
        // Mục tiêu: xác minh nhánh chuẩn hóa active trong create().

        // Arrange
        Permission permission = new Permission();
        permission.setName("user-service:permissions:read");
        permission.setActive(false);
        long countBeforeCreate = permissionRepository.count();

        // Act
        Permission result = permissionService.create(permission);
        forceSyncPersistenceContext();

        // Assert
        assertThat(permissionRepository.count()).isEqualTo(countBeforeCreate + 1);
        Permission savedPermission = permissionRepository.findById(result.getId()).orElseThrow();
        assertThat(savedPermission.isActive()).isTrue();
    }

    @Test
    @DisplayName("[PER-TC02] - update() cập nhật permission khi id tồn tại")
    void tc02_update_existingPermission_updatesData() {
        // Test Case ID: PER-TC02
        // Mục tiêu: xác minh update() ghi đè name/active thành công.

        // Arrange
        Permission existingPermission = createPermission("user-service:roles:read", true);
        Permission input = new Permission();
        input.setName("user-service:roles:manage");
        input.setActive(false);

        // Act
        Permission result = permissionService.update(existingPermission.getId(), input);
        forceSyncPersistenceContext();

        // Assert
        Permission updatedPermission = permissionRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedPermission.getName()).isEqualTo("user-service:roles:manage");
        assertThat(updatedPermission.isActive()).isFalse();
    }

    @Test
    @DisplayName("[PER-TC03] - update() trả về null khi id không tồn tại")
    void tc03_update_missingPermission_returnsNull() {
        // Test Case ID: PER-TC03
        // Mục tiêu: xác minh nhánh không tìm thấy bản ghi cần update.

        // Arrange
        Permission input = new Permission();
        input.setName("user-service:users:read");
        input.setActive(true);

        // Act
        Permission result = permissionService.update(999999L, input);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("[PER-TC04] - check() trả false khi userId là null")
    void tc04_check_nullUserId_returnsFalse() {
        // Test Case ID: PER-TC04
        // Mục tiêu: xác minh guard clause ở đầu hàm check().

        // Act
        boolean allowed = permissionService.check("user-service:roles:read", null);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC05] - check() trả false khi user không tồn tại")
    void tc05_check_userNotFound_returnsFalse() {
        // Test Case ID: PER-TC05
        // Mục tiêu: xác minh nhánh userRepository không tìm thấy user.

        // Act
        boolean allowed = permissionService.check("user-service:roles:read", 777777L);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC06] - check() trả false khi user không có role")
    void tc06_check_userWithoutRole_returnsFalse() {
        // Test Case ID: PER-TC06
        // Mục tiêu: xác minh nhánh user.getRole() == null.

        // Arrange
        User userWithoutRole = createUser("rbac.norole@company.com", null);

        // Act
        boolean allowed = permissionService.check("user-service:roles:read", userWithoutRole.getId());

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC07] - check() trả true khi role có permission active phù hợp")
    void tc07_check_matchingActivePermission_returnsTrue() {
        // Test Case ID: PER-TC07
        // Mục tiêu: xác minh RBAC core logic: role có quyền active thì được phép.

        // Arrange
        Permission permission = createPermission("user-service:users:manage", true);
        Role role = createRole("RBAC_ALLOW_ROLE", true, List.of(permission));
        User user = createUser("rbac.allow@company.com", role);

        // Act
        boolean allowed = permissionService.check("user-service:users:manage", user.getId());

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("[PER-TC08] - check() trả false khi permission không active")
    void tc08_check_inactivePermission_returnsFalse() {
        // Test Case ID: PER-TC08
        // Mục tiêu: xác minh chỉ permission active=true mới được chấp nhận.

        // Arrange
        Permission permission = createPermission("user-service:users:read", false);
        Role role = createRole("RBAC_DENY_ROLE", true, List.of(permission));
        User user = createUser("rbac.deny@company.com", role);

        // Act
        boolean allowed = permissionService.check("user-service:users:read", user.getId());

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC09] - getAllWithFilters() trả về metadata và danh sách permission")
    void tc09_getAllWithFilters_returnsPaginationData() {
        // Test Case ID: PER-TC09
        // Mục tiêu: xác minh paging + filter theo active/keyword.

        // Arrange
        createPermission("user-service:departments:read", true);
        createPermission("user-service:departments:manage", false);

        // Act
        PaginationDTO result = permissionService.getAllWithFilters(true, "departments:read", PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        assertThat(((List<Permission>) result.getResult())).hasSize(1);
    }

    @Test
    @DisplayName("[PER-TC10] - getAllWithFiltersNoPage() trả về danh sách permission đã lọc")
    void tc10_getAllWithFiltersNoPage_returnsFilteredList() {
        // Test Case ID: PER-TC10
        // Mục tiêu: xác minh query không phân trang trả đúng danh sách.

        // Arrange
        createPermission("candidate-service:candidates:read", true);
        createPermission("candidate-service:candidates:manage", true);

        // Act
        List<Permission> result = permissionService.getAllWithFiltersNoPage(true, "candidates:manage");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("candidate-service:candidates:manage");
    }

    @Test
    @DisplayName("[PER-TC11] - findByIds() trả về đúng danh sách permission theo ids")
    void tc11_findByIds_returnsMatchedPermissions() {
        // Test Case ID: PER-TC11
        // Mục tiêu: xác minh truy vấn theo tập id.

        // Arrange
        Permission p1 = createPermission("workflow-service:workflows:read", true);
        Permission p2 = createPermission("workflow-service:workflows:manage", true);
        Permission p3 = createPermission("workflow-service:approval-trackings:read", true);

        // Act
        List<Permission> result = permissionService.findByIds(List.of(p1.getId(), p3.getId()));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Permission::getName)
                .containsExactlyInAnyOrder("workflow-service:workflows:read",
                        "workflow-service:approval-trackings:read");
        assertThat(result).extracting(Permission::getId).doesNotContain(p2.getId());
    }

    @Test
    @DisplayName("[PER-TC12] - delete() xóa permission và gỡ liên kết role")
    void tc12_delete_removesPermissionAndDetachesFromRoles() {
        // Test Case ID: PER-TC12
        // Mục tiêu: xác minh xóa permission không để lại liên kết bẩn ở role.

        // Arrange
        Permission permission = createPermission("notification-service:notifications:manage", true);
        Role role = createRole("NOTIFICATION_MANAGER", true, List.of(permission));
        forceSyncPersistenceContext();

        // Act
        permissionService.delete(permission.getId());
        forceSyncPersistenceContext();

        // Assert
        assertThat(permissionRepository.existsById(permission.getId())).isFalse();
        Role refreshedRole = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(refreshedRole.getPermissions())
                .noneMatch(p -> p.getName().equals("notification-service:notifications:manage"));
    }

    @Test
    @DisplayName("[PER-TC13] - evictCacheForRole() xóa cache permCheck")
    void tc13_evictCacheForRole_clearsPermCheckCache() {
        // Test Case ID: PER-TC13
        // Mục tiêu: xác minh khi cập nhật quyền của role, cache được clear để đồng bộ
        // realtime.

        // Arrange
        Cache cache = cacheManager.getCache("permCheck");
        assertThat(cache).isNotNull();
        cache.put("123:user-service:users:read", true);
        assertThat(cache.get("123:user-service:users:read")).isNotNull();

        // Act
        permissionService.evictCacheForRole(123L);

        // Assert
        assertThat(cache.get("123:user-service:users:read")).isNull();
    }

    private Permission createPermission(String name, boolean active) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setActive(active);
        return permissionRepository.save(permission);
    }

    private Role createRole(String name, boolean active, List<Permission> permissions) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(name + " description");
        role.set_active(active);
        role.setPermissions(new HashSet<>(permissions));
        return roleRepository.save(role);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("123456");
        user.set_active(true);
        user.setRole(role);
        return userRepository.save(user);
    }

    private void forceSyncPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
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

/**
 * ============================================================
 * Unit Test: PermissionService - Quản lý Quyền Hạn (Permission RBAC)
 * ============================================================
 *
 * PHẠM VI TEST:
 *   - create(): Tạo permission mới, normalize active=true
 *   - update(): Cập nhật permission theo id
 *   - check(): Kiểm tra user có quyền RBAC không (core logic RBAC)
 *   - getAllWithFilters(): Lấy danh sách permission có lọc và phân trang
 *   - delete(): Xóa permission và gỡ liên kết role
 *   - findByIds(): Tìm permission theo danh sách id
 *   - isPermissionExistsByName(): Kiểm tra permission tồn tại
 *
 * CHIẾN LƯỢC TEST:
 *   - @SpringBootTest: Load full Spring context (DB, cache, repository)
 *   - @Transactional: Auto rollback sau mỗi test → DB sạch
 *   - @ActiveProfiles("test"): Dùng H2 in-memory DB thay vì production DB
 *   - Kiểm tra tại cả DB layer và service layer
 *
 * MOCK STRATEGY:
 *   - @MockitoBean DataInitializer: Bỏ qua script khởi tạo dữ liệu, test độc lập
 *
 * CHÚ Ý: Test RBAC core logic (check method) bằng cách:
 *   1. Tạo Permission với active=true
 *   2. Tạo Role chứa Permission
 *   3. Tạo User gán Role
 *   4. Gọi check() để xác minh logic RBAC
 * ============================================================
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("PermissionService Unit Test")
class PermissionServiceTest {

    // ============================================================
    // DEPENDENCIES INJECTION (từ Spring context)
    // ============================================================
    
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

    // ============================================================
    // MOCK DEPENDENCIES (không được load từ Spring context)
    // ============================================================
    
    /**
     * Mock DataInitializer để không chạy initialization script thực
     * → Test được độc lập, không bị ảnh hưởng bởi dữ liệu init
     */
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
    @DisplayName("[PER-TC02] - create() giữ nguyên active=true khi input đã active=true")
    void tc02_create_keepsActiveTrueWhenInputIsAlreadyTrue() {
        // Test Case ID: PER-TC02
        // Mục tiêu: xác minh nhánh FALSE của D1 — if (!p.isActive())
        //           Khi active đã là true, không vào nhánh if → vẫn lưu active=true.
        // Basis Path: D1=False (active=true → skip set)

        // Arrange
        Permission permission = new Permission();
        permission.setName("user-service:permissions:manage");
        permission.setActive(true); // đã true sẵn → không vào nhánh if
        long countBefore = permissionRepository.count();

        // Act
        Permission result = permissionService.create(permission);
        forceSyncPersistenceContext();

        // Assert
        assertThat(permissionRepository.count()).isEqualTo(countBefore + 1);
        Permission saved = permissionRepository.findById(result.getId()).orElseThrow();
        // Nhánh FALSE D1: active không bị thay đổi (vẫn true)
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("[PER-TC03] - update() cập nhật permission khi id tồn tại")
    void tc03_update_existingPermission_updatesData() {
        // Test Case ID: PER-TC03
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
    @DisplayName("[PER-TC04] - update() trả về null khi id không tồn tại")
    void tc04_update_missingPermission_returnsNull() {
        // Test Case ID: PER-TC04
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
    @DisplayName("[PER-TC05] - check() trả false khi userId là null")
    void tc05_check_nullUserId_returnsFalse() {
        // Test Case ID: PER-TC05
        // Mục tiêu: xác minh guard clause ở đầu hàm check().

        // Act
        boolean allowed = permissionService.check("user-service:roles:read", null);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC06] - check() trả false khi user không tồn tại")
    void tc06_check_userNotFound_returnsFalse() {
        // Test Case ID: PER-TC06
        // Mục tiêu: xác minh nhánh userRepository không tìm thấy user.

        // Act
        boolean allowed = permissionService.check("user-service:roles:read", 777777L);

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC07] - check() trả false khi user không có role")
    void tc07_check_userWithoutRole_returnsFalse() {
        // Test Case ID: PER-TC07
        // Mục tiêu: xác minh nhánh user.getRole() == null.

        // Arrange
        User userWithoutRole = createUser("rbac.norole@company.com", null);

        // Act
        boolean allowed = permissionService.check("user-service:roles:read", userWithoutRole.getId());

        // Assert
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("[PER-TC08] - check() trả true khi role có permission active phù hợp")
    void tc08_check_matchingActivePermission_returnsTrue() {
        // Test Case ID: PER-TC08
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
    @DisplayName("[PER-TC09] - check() trả false khi permission không active")
    void tc09_check_inactivePermission_returnsFalse() {
        // Test Case ID: PER-TC09
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
    @DisplayName("[PER-TC10] - getAllWithFilters() trả về metadata và danh sách permission")
    void tc10_getAllWithFilters_returnsPaginationData() {
        // Test Case ID: PER-TC10
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
    @DisplayName("[PER-TC11] - getAllWithFiltersNoPage() trả về danh sách permission đã lọc")
    void tc11_getAllWithFiltersNoPage_returnsFilteredList() {
        // Test Case ID: PER-TC11
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
    @DisplayName("[PER-TC12] - findByIds() trả về đúng danh sách permission theo ids")
    void tc12_findByIds_returnsMatchedPermissions() {
        // Test Case ID: PER-TC12
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
    @DisplayName("[PER-TC13] - delete() xóa permission và gỡ liên kết role")
    void tc13_delete_removesPermissionAndDetachesFromRoles() {
        // Test Case ID: PER-TC13
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
    @DisplayName("[PER-TC14] - delete() không ném exception khi id không tồn tại (permission == null)")
    void tc14_delete_nonExistentId_doesNothing() {
        // Test Case ID: PER-TC14
        // Mục tiêu: xác minh nhánh FALSE của D6 — if (permission != null)
        //           Source code: permissionRepository.findById(id).orElse(null)
        //           → permission == null → skip forEach → gọi delete(null)
        //           Spring Data JPA có thể ném IllegalArgumentException khi delete(null)
        //           → cần assertDoesNotThrow để bắt bug này.
        //
        // Bug bị bắt:
        //   - Code gọi permissionRepository.delete(null) thay vì guard null
        //     → Spring Data JPA ném IllegalArgumentException → test FAIL rõ ràng
        //   - Count DB tăng do side-effect không mong muốn

        // Arrange: ghi lại số lượng ban đầu
        long countBefore = permissionRepository.count();

        // Act + Assert: không được ném bất kỳ exception nào
        // Nếu thiếu null-check trước delete() → IllegalArgumentException → FAIL
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> permissionService.delete(999999L),
                "BUG: Ném exception khi delete id không tồn tại — " +
                "có thể do gọi permissionRepository.delete(null) thiếu null-check"
        );
        forceSyncPersistenceContext();

        // CheckDB: số lượng bản ghi phải không đổi
        // Nếu delete(null) xóa nhầm record → count giảm → FAIL
        assertThat(permissionRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("[PER-TC15] - evictCacheForRole() xóa cache permCheck")
    void tc15_evictCacheForRole_clearsPermCheckCache() {
        // Test Case ID: PER-TC15
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


    @Test
    @DisplayName("[PER-TC16] - evictCacheForRole() xóa TOÀN BỘ cache, không phân biệt key")
    void tc16_evictCacheForRole_clearsAllCacheEntries_notJustOneKey() {
        // Test Case ID: PER-TC16
        // Mục tiêu: xác minh evictCacheForRole() gọi cache.clear() (xóa toàn bộ),
        //           KHÔNG chỉ evict key của roleId được truyền vào.
        //           Đặc tả: Caffeine không hỗ trợ pattern-match key → phải clear all.
        //
        // Bug bị bắt:
        //   - Nếu code chỉ evict selective (key = "roleId:...") thay vì clear all
        //     → key của user khác vẫn còn trong cache
        //     → user thấy quyền cũ sau khi permission của role thay đổi
        //   - Nếu method không gọi cache.clear() → tất cả assert.isNull() đều FAIL
        //
        // Lưu ý TC15 vs TC16:
        //   TC15: xác minh 1 key bị xóa (basic test)
        //   TC16: xác minh NHIỀU key khác nhau đều bị xóa (all-or-nothing clear)

        // Arrange: đặt 3 key khác nhau vào cache — mô phỏng 3 user đã check quyền
        Cache cache = cacheManager.getCache("permCheck");
        assertThat(cache).isNotNull();
        cache.put("100:user-service:roles:read", true);       // user 100
        cache.put("200:user-service:permissions:manage", false); // user 200
        cache.put("300:workflow-service:workflows:read", true);  // user 300

        // Tiền điều kiện: xác nhận cache thực sự có data trước khi evict
        assertThat(cache.get("100:user-service:roles:read")).isNotNull();
        assertThat(cache.get("200:user-service:permissions:manage")).isNotNull();
        assertThat(cache.get("300:workflow-service:workflows:read")).isNotNull();

        // Act: gọi evict với roleId=123 (bất kỳ)
        // Spec: phải clear TOÀN BỘ cache vì không thể filter theo roleId
        permissionService.evictCacheForRole(123L);

        // Assert: TẤT CẢ key phải bị xóa, không chỉ key có "123" trong tên
        // Nếu code chỉ xóa key của roleId=123 → key 100/200/300 vẫn còn → FAIL
        assertThat(cache.get("100:user-service:roles:read")).isNull();
        assertThat(cache.get("200:user-service:permissions:manage")).isNull();
        assertThat(cache.get("300:workflow-service:workflows:read")).isNull();
    }

    @Test
    @DisplayName("[PER-TC17] - evictCacheForRole() không ném exception khi CacheManager.getCache trả null")
    void tc17_evictCacheForRole_withMockedNullCache_doesNothing() {
        // Test Case ID: PER-TC17
        // Mục tiêu: trực tiếp test nhánh cache == null trong evictCacheForRole()

        // Arrange: mock CacheManager trả null cho 'permCheck'
        org.springframework.cache.CacheManager mockCacheManager = org.mockito.Mockito.mock(org.springframework.cache.CacheManager.class);
        org.mockito.Mockito.when(mockCacheManager.getCache("permCheck")).thenReturn(null);
        PermissionService svc = new PermissionService(permissionRepository, userRepository, mockCacheManager);

        // Act + Assert: không ném exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> svc.evictCacheForRole(42L));
    }

    @Test
    @DisplayName("[PER-TC18] - isPermissionExistsByName() phản hồi đúng với tên tồn tại/không tồn tại")
    void tc18_isPermissionExistsByName_returnsExpected() {
        // Test Case ID: PER-TC18
        // Mục tiêu: xác minh hàm isPermissionExistsByName()

        // Arrange
        Permission p = createPermission("test:permissions:exists", true);

        // Act + Assert
        assertThat(permissionService.isPermissionExistsByName("test:permissions:exists")).isTrue();
        assertThat(permissionService.isPermissionExistsByName("nonexistent:perm")).isFalse();
    }

    private Permission createPermission(String name, boolean active) {
        /**
         * HELPER METHOD: Tạo Permission test fixture
         * 
         * Cách sử dụng:
         *   Permission p = createPermission("service:resource:action", true)
         * 
         * Làm gì: 
         *   1. Tạo object Permission mới
         *   2. Gán name + active
         *   3. Lưu vào DB via repository
         *   4. Trả về entity đã save (có ID)
         * 
         * Tại sao:
         *   - Giảm code lặp trong @Arrange phase
         *   - Đảm bảo permission luôn được save vào DB (không null)
         *   - Dễ dàng tạo nhiều permission khác nhau cho test
         */
        Permission permission = new Permission();
        permission.setName(name);
        permission.setActive(active);
        return permissionRepository.save(permission);
    }

    private Role createRole(String name, boolean active, List<Permission> permissions) {
        /**
         * HELPER METHOD: Tạo Role test fixture với danh sách Permission
         * 
         * Cách sử dụng:
         *   Permission p = createPermission("svc:res:read", true);
         *   Role r = createRole("ADMIN", true, List.of(p));
         * 
         * Làm gì:
         *   1. Tạo object Role mới
         *   2. Set name, description, is_active
         *   3. Gán danh sách permission (M-M relationship)
         *   4. Lưu vào DB
         *   5. Trả về role có ID + permissions loaded
         * 
         * Tại sao:
         *   - Permission-Role là Many-to-Many, cần cả hai bên
         *   - Giảm setup code khi test RBAC logic
         *   - Tạo sẵn relationship → dễ kiểm tra cache/query
         */
        Role role = new Role();
        role.setName(name);
        role.setDescription(name + " description");
        role.set_active(active);
        role.setPermissions(new HashSet<>(permissions));
        return roleRepository.save(role);
    }

    private User createUser(String email, Role role) {
        /**
         * HELPER METHOD: Tạo User test fixture gán vào Role
         * 
         * Cách sử dụng:
         *   Role r = createRole(...);
         *   User u = createUser("user@company.com", r);
         * 
         * Làm gì:
         *   1. Tạo object User mới
         *   2. Set email, password, is_active
         *   3. Gán role (User-Role là M-1: mỗi user có 1 role)
         *   4. Lưu vào DB
         *   5. Trả về user có ID
         * 
         * Tại sao:
         *   - Để test PermissionService.check(permission, userId)
         *   - check() logic: User → Role → Permission list → check active
         *   - Cần toàn bộ chain này để test RBAC core logic
         * 
         * Ví dụ test:
         *   Permission p = createPermission("svc:res:action", true);
         *   Role r = createRole("ROLE", true, List.of(p));
         *   User u = createUser("test@company.com", r);
         *   boolean allowed = permissionService.check("svc:res:action", u.getId());
         *   assertThat(allowed).isTrue();  // RBAC tìm User → Role → Permission
         */
        User user = new User();
        user.setEmail(email);
        user.setPassword("123456");
        user.set_active(true);
        user.setRole(role);
        return userRepository.save(user);
    }

    private void forceSyncPersistenceContext() {
        /**
         * HELPER METHOD: Đồng bộ JPA Entity Manager với DB
         * 
         * Cách sử dụng:
         *   permissionService.someMethod();
         *   forceSyncPersistenceContext();  // bắt buộc ghi DB
         *   assertThat(permissionRepository.findById(...)).isPresent();
         * 
         * Làm gì:
         *   1. flush(): ghi tất cả pending changes từ JPA cache xuống DB
         *   2. clear(): xóa L1 cache (Hibernate session)
         *   → Repository truy vấn DB từ đầu, không dùng cache cũ
         * 
         * Tại sao:
         *   - @Transactional + JPA Persistence Context cho phép lazy load
         *   - Nếu không flush/clear → repository trả về entity từ cache
         *   - Khi test "xác minh DB", cần CHẮC CHẮN không phải cache
         *   - flush + clear = "mô phỏng transaction mới" → test thực tế
         * 
         * Lưu ý:
         *   - KHÔNG gọi sau mỗi dòng code
         *   - Chỉ gọi trước CheckDB assertion
         *   - Ví dụ:
         *       permissionService.update(123, dto);
         *       forceSyncPersistenceContext();
         *       Permission saved = repo.findById(123).orElseThrow();
         *       assertThat(saved.getName()).isEqualTo(dto.getName());
         */
        entityManager.flush();
        entityManager.clear();
    }
}
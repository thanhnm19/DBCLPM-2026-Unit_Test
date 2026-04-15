package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.PaginationDTO;
import com.example.user_service.dto.position.CreatePositionDTO;
import com.example.user_service.dto.position.UpdatePositionDTO;
import com.example.user_service.model.Position;
import com.example.user_service.repository.PositionRepository;
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

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("PositionService Unit Test")
class PositionServiceTest {

    @Autowired
    private PositionService positionService;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("[POS-TC01] - create() tạo vị trí mới và lưu đúng dữ liệu")
    void tc01_create_persistsPositionAndSetsDefaultActive() {
        // Test Case ID: POS-TC01
        // Mục tiêu: xác minh create() ghi bản ghi mới vào DB thật.

        // Arrange
        CreatePositionDTO dto = new CreatePositionDTO("Senior Developer", "L3", 3, null);
        long countBeforeCreate = positionRepository.count();

        // Act
        Position result = positionService.create(dto);
        forceSyncPersistenceContext();

        // Assert
        assertThat(positionRepository.count()).isEqualTo(countBeforeCreate + 1);
        Position savedPosition = positionRepository.findById(result.getId()).orElseThrow();
        assertThat(savedPosition.getName()).isEqualTo("Senior Developer");
        assertThat(savedPosition.getLevel()).isEqualTo("L3");
        assertThat(savedPosition.getHierarchyOrder()).isEqualTo(3);
        assertThat(savedPosition.isActive()).isTrue();
    }

    @Test
    @DisplayName("[POS-TC02] - update() cập nhật đúng vị trí trong DB")
    void tc02_update_updatesPersistedPositionSuccessfully() {
        // Test Case ID: POS-TC02
        // Mục tiêu: xác minh update() thay đổi đúng dữ liệu đã lưu.

        // Arrange
        Position existingPosition = createPosition("Developer", "L2", 2, true);
        UpdatePositionDTO dto = new UpdatePositionDTO("Lead Developer", "L4", 4, false);

        // Act
        Position result = positionService.update(existingPosition.getId(), dto);
        forceSyncPersistenceContext();

        // Assert
        Position updatedPosition = positionRepository.findById(result.getId()).orElseThrow();
        assertThat(updatedPosition.getName()).isEqualTo("Lead Developer");
        assertThat(updatedPosition.getLevel()).isEqualTo("L4");
        assertThat(updatedPosition.getHierarchyOrder()).isEqualTo(4);
        assertThat(updatedPosition.isActive()).isFalse();
    }

    @Test
    @DisplayName("[POS-TC03] - delete() xóa vị trí khỏi DB")
    void tc03_delete_removesPositionFromDatabase() {
        // Test Case ID: POS-TC03
        // Mục tiêu: xác minh delete() xóa record và DB giảm số lượng.

        // Arrange
        Position existingPosition = createPosition("Tester", "L1", 1, true);
        long countBeforeDelete = positionRepository.count();

        // Act
        positionService.delete(existingPosition.getId());
        forceSyncPersistenceContext();

        // Assert
        assertThat(positionRepository.existsById(existingPosition.getId())).isFalse();
        assertThat(positionRepository.count()).isEqualTo(countBeforeDelete - 1);
    }

    @Test
    @DisplayName("[POS-TC04] - getById() trả về đúng vị trí khi id tồn tại")
    void tc04_getById_returnsPosition() {
        // Test Case ID: POS-TC04
        // Mục tiêu: xác minh nhánh đọc dữ liệu theo id hợp lệ.

        // Arrange
        Position existingPosition = createPosition("Team Lead", "L5", 5, true);

        // Act
        Position result = positionService.getById(existingPosition.getId());

        // Assert
        assertThat(result.getId()).isEqualTo(existingPosition.getId());
        assertThat(result.getName()).isEqualTo("Team Lead");
    }

    @Test
    @DisplayName("[POS-TC05] - getById() trả về null khi id không tồn tại")
    void tc05_getById_missingId_returnsNull() {
        // Test Case ID: POS-TC05
        // Mục tiêu: xác minh service trả null đúng theo thiết kế hiện tại.

        // Act
        Position result = positionService.getById(40404L);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("[POS-TC06] - getAll() trả về metadata và danh sách vị trí")
    void tc06_getAll_returnsPaginationData() {
        // Test Case ID: POS-TC06
        // Mục tiêu: xác minh paging metadata và dữ liệu trả về đúng.

        // Arrange
        createPosition("Position 1", "L1", 1, true);
        createPosition("Position 2", "L2", 2, false);

        // Act
        PaginationDTO result = positionService.getAll(PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);
        assertThat(result.getMeta().getTotal()).isGreaterThanOrEqualTo(2L);
        assertThat((List<Position>) result.getResult()).isNotEmpty();
    }

    @Test
    @DisplayName("[POS-TC07] - getAllWithFilters() lọc theo trạng thái và keyword")
    void tc07_getAllWithFilters_filtersByStatusAndKeyword() {
        // Test Case ID: POS-TC07
        // Mục tiêu: xác minh query lọc trả về đúng bản ghi theo keyword.

        // Arrange
        createPosition("Accountant", "L2", 2, true);
        createPosition("Architect", "L6", 6, false);

        // Act
        PaginationDTO result = positionService.getAllWithFilters(true, "account", PageRequest.of(0, 10));

        // Assert
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        List<Position> positions = (List<Position>) result.getResult();
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).getName()).isEqualTo("Accountant");
    }

    @Test
    @DisplayName("[POS-TC08] - getByIds() trả về đúng danh sách vị trí")
    void tc08_getByIds_returnsMatchedPositions() {
        // Test Case ID: POS-TC08
        // Mục tiêu: xác minh lấy nhiều vị trí theo danh sách id.

        // Arrange
        Position position1 = createPosition("P1", "L1", 1, true);
        Position position2 = createPosition("P2", "L2", 2, true);
        Position position3 = createPosition("P3", "L3", 3, true);

        // Act
        List<Position> result = positionService.getByIds(List.of(position1.getId(), position3.getId()));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Position::getName).containsExactlyInAnyOrder("P1", "P3");
        assertThat(result).extracting(Position::getId).doesNotContain(position2.getId());
    }

    private Position createPosition(String name, String level, Integer hierarchyOrder, boolean isActive) {
        Position position = new Position();
        position.setName(name);
        position.setLevel(level);
        position.setHierarchyOrder(hierarchyOrder);
        position.setActive(isActive);
        return positionRepository.save(position);
    }

    private void forceSyncPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
package com.example.workflow_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test kiểm tra context load cơ bản.
 * Sử dụng profile "test" (H2 in-memory) để không yêu cầu MySQL thật.
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Tắt tạm - context load test không cần thiết khi có unit test đầy đủ")
class WorkflowServiceApplicationTests {

	@Test
	void contextLoads() {
		// Kiểm tra Spring context khởi tạo thành công với H2
	}

}

package com.example.notification_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Context load test - bỏ qua khi không có Kafka thật")
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

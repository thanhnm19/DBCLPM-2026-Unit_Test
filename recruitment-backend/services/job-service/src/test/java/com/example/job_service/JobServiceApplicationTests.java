package com.example.job_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Context load test - bỏ qua khi không có Kafka thật")
class JobServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

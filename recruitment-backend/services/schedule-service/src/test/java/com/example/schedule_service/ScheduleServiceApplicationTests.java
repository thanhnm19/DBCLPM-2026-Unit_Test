package com.example.schedule_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Disabled("Disabled to avoid starting full Spring context and real DB during unit tests")
class ScheduleServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}

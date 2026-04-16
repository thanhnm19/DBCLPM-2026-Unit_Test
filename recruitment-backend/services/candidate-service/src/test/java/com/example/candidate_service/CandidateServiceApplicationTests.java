package com.example.candidate_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify Spring context wiring.
 *
 * Uses the 'test' profile so tests do not depend on local MySQL credentials.
 */
@SpringBootTest
@ActiveProfiles("test")
class CandidateServiceApplicationTests {

	@MockBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void contextLoads() {
	}

}

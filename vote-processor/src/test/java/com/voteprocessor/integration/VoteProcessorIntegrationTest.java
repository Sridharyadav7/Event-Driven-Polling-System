package com.voteprocessor.integration;

import com.voteprocessor.event.VoteCastEvent;
import com.voteprocessor.repository.ProcessedEventRepository;
import com.voteprocessor.repository.VoteRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test for the VoteProcessor:
 * - Publishes VoteCastEvents to Kafka.
 * - Verifies VoteProcessor processes them, writes to DB, and updates Redis.
 * - Verifies idempotency: duplicate events are ignored.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoteProcessorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired ProcessedEventRepository processedEventRepository;
    @Autowired VoteRepository voteRepository;
    @Autowired org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    @Autowired KafkaTemplate<String, VoteCastEvent> kafkaTemplate;

    static final UUID POLL_ID   = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID VOTER_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final String OPTION  = "opt-blue";

    @Test
    @Order(1)
    void processesNewVoteEvent() {
        UUID eventId = UUID.randomUUID();
        VoteCastEvent event = VoteCastEvent.builder()
                .eventId(eventId)
                .pollId(POLL_ID)
                .voterId(VOTER_ID)
                .optionId(OPTION)
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send("vote_cast", POLL_ID.toString(), event);

        // Wait up to 15 seconds for the consumer to process
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsByEventId(eventId)).isTrue());

        // Verify Redis counter was incremented
        Object redisCount = redisTemplate.opsForHash()
                .get("poll:" + POLL_ID + ":results", OPTION);
        assertThat(redisCount).isNotNull();
        assertThat(Long.parseLong(redisCount.toString())).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @Order(2)
    void duplicateEventIsIgnored_onlyOneProcessedEventRecord() {
        UUID eventId = UUID.randomUUID();
        UUID newVoter = UUID.randomUUID();
        VoteCastEvent event = VoteCastEvent.builder()
                .eventId(eventId)
                .pollId(POLL_ID)
                .voterId(newVoter)
                .optionId(OPTION)
                .timestamp(Instant.now())
                .build();

        // Send the same event twice — idempotency must handle this
        kafkaTemplate.send("vote_cast", POLL_ID.toString(), event);
        kafkaTemplate.send("vote_cast", POLL_ID.toString(), event);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsByEventId(eventId)).isTrue());

        // Should only appear once in processed_events
        long count = processedEventRepository.findAll().stream()
                .filter(e -> e.getEventId().equals(eventId)).count();
        assertThat(count).isEqualTo(1);
    }
}

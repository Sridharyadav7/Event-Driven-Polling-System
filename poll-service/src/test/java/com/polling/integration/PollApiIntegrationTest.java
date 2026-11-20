package com.polling.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polling.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test running against real PostgreSQL, Kafka, and Redis.
 * Testcontainers spins up Docker containers for the test suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PollApiIntegrationTest {

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
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> "TestSecretKeyLongEnoughForHS256AlgorithmRequirement1");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // State shared across ordered tests
    static String jwt;
    static String pollId;
    static String optionId;

    @Test
    @Order(1)
    void register_returnsJwt() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Test User");
        req.setEmail("testuser@integration.com");
        req.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        jwt = auth.getToken();
        assertThat(jwt).isNotBlank();
    }

    @Test
    @Order(2)
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Dup");
        req.setEmail("testuser@integration.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    void createPoll_authenticated_success() throws Exception {
        CreatePollRequest req = new CreatePollRequest();
        req.setQuestion("Integration test poll?");
        req.setOptions(List.of("Yes", "No", "Maybe"));
        req.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));

        MvcResult result = mockMvc.perform(post("/api/polls")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pollId").isNotEmpty())
                .andExpect(jsonPath("$.options.length()").value(3))
                .andReturn();

        PollResponse poll = objectMapper.readValue(result.getResponse().getContentAsString(), PollResponse.class);
        pollId = poll.getPollId().toString();
        optionId = poll.getOptions().get(0).getOptionId();
        assertThat(pollId).isNotBlank();
    }

    @Test
    @Order(4)
    void createPoll_unauthenticated_returns401() throws Exception {
        CreatePollRequest req = new CreatePollRequest();
        req.setQuestion("Q?");
        req.setOptions(List.of("A", "B"));
        req.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(post("/api/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void listPolls_public_success() throws Exception {
        mockMvc.perform(get("/api/polls?page=0&size=20"))
                .andExpect(status().isOk())
                // Spring's Page response has a "content" array
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(6)
    void getPoll_public_success() throws Exception {
        mockMvc.perform(get("/api/polls/" + pollId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollId").value(pollId));
    }

    @Test
    @Order(7)
    void castVote_success_returns200() throws Exception {
        VoteRequest req = new VoteRequest();
        req.setOptionId(optionId);

        mockMvc.perform(post("/api/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void castVote_duplicate_returns409() throws Exception {
        // Same user voting again — should be rejected
        VoteRequest req = new VoteRequest();
        req.setOptionId(optionId);

        mockMvc.perform(post("/api/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(9)
    void castVote_invalidOption_returns400() throws Exception {
        // Register a second user to avoid "already voted" masking the invalid option error
        RegisterRequest reg = new RegisterRequest();
        reg.setName("User2");
        reg.setEmail("user2@integration.com");
        reg.setPassword("password123");
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        String jwt2 = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class).getToken();

        VoteRequest req = new VoteRequest();
        req.setOptionId("not-a-real-option-id");

        mockMvc.perform(post("/api/polls/" + pollId + "/vote")
                        .header("Authorization", "Bearer " + jwt2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    void actuatorHealth_isUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(11)
    void actuatorPrometheus_isAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_")));
    }
}

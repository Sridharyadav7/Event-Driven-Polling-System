package com.polling.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private static final String SECRET = "ThisIsATestSecretKeyLongEnoughForHS256AlgorithmRequirement";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 3600000L); // 1 hour
    }

    @Test
    void generateAndValidate() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateToken(userId, "test@test.com");

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo("test@test.com");
    }

    @Test
    void invalidToken_returnsFalse() {
        assertThat(tokenProvider.validateToken("not.a.jwt")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }

    @Test
    void expiredToken_returnsFalse() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1L); // 1ms expiry
        UUID userId = UUID.randomUUID();
        String token = shortLived.generateToken(userId, "test@test.com");

        // Sleep briefly to ensure expiry
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortLived.validateToken(token)).isFalse();
    }
}

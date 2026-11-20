package com.voteprocessor.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Isolated Redis counter service with its own circuit breaker.
 * Keeping it in a separate Spring bean is required for Resilience4j AOP to intercept
 * the @CircuitBreaker annotation (AOP doesn't work on self-invocation within the same bean).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCounterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Increment the live vote tally in Redis: HINCRBY poll:{pollId}:results {optionId} 1
     * Circuit breaker: if Redis is unavailable, falls back gracefully.
     * The DB already has the vote, so counters will be rebuilt on next read via DB fallback.
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "incrementFallback")
    public void incrementCounter(UUID pollId, String optionId) {
        String key = "poll:" + pollId + ":results";
        redisTemplate.opsForHash().increment(key, optionId, 1L);
        log.debug("Redis HINCRBY {} {} 1", key, optionId);
    }

    @SuppressWarnings("unused")
    public void incrementFallback(UUID pollId, String optionId, Throwable t) {
        log.warn("Redis circuit OPEN — counter for poll {} option {} not updated: {}",
                pollId, optionId, t.getMessage());
        Counter.builder("votes.redis.fallback.total")
                .description("Redis increment fallbacks due to circuit breaker")
                .register(meterRegistry)
                .increment();
    }
}

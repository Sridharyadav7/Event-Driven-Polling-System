package com.polling.service;

import com.polling.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fetches live vote tallies from Redis.
 * Falls back to DB aggregation if Redis is unavailable or has no data yet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsService {

    private final RedisTemplate<String, String> redisTemplate;
    private final VoteRepository voteRepository;

    /**
     * Returns vote counts per optionId for the given poll.
     * Primary source: Redis HGETALL poll:{pollId}:results
     * Fallback: SQL aggregation query.
     */
    public Map<String, Long> getVoteCounts(UUID pollId) {
        String redisKey = "poll:" + pollId + ":results";
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);
            if (entries != null && !entries.isEmpty()) {
                Map<String, Long> counts = new HashMap<>();
                entries.forEach((k, v) -> counts.put(k.toString(), Long.parseLong(v.toString())));
                return counts;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for poll {}, falling back to DB: {}", pollId, e.getMessage());
        }

        // DB fallback: aggregate votes from the votes table
        return aggregateFromDb(pollId);
    }

    private Map<String, Long> aggregateFromDb(UUID pollId) {
        List<Object[]> rows = voteRepository.countVotesByOption(pollId);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }
}

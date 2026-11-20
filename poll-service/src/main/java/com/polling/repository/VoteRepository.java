package com.polling.repository;

import com.polling.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

    boolean existsByPollIdAndVoterId(UUID pollId, UUID voterId);

    Optional<Vote> findByPollIdAndVoterId(UUID pollId, UUID voterId);

    List<Vote> findByPollId(UUID pollId);

    /** Aggregate vote counts per option for a given poll. Used as fallback when Redis is down. */
    @Query("SELECT v.optionId, COUNT(v) FROM Vote v WHERE v.pollId = :pollId GROUP BY v.optionId")
    List<Object[]> countVotesByOption(UUID pollId);
}

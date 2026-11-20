package com.voteprocessor.repository;

import com.voteprocessor.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

    /**
     * INSERT ... ON CONFLICT DO NOTHING — idempotent upsert.
     * Returns 1 if inserted, 0 if the unique constraint (poll_id, voter_id) prevented it.
     */
    @Modifying
    @Query(value = """
            INSERT INTO votes (vote_id, poll_id, voter_id, option_id, created_at)
            VALUES (:voteId, :pollId, :voterId, :optionId, NOW())
            ON CONFLICT (poll_id, voter_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreDuplicate(
            @Param("voteId")   UUID voteId,
            @Param("pollId")   UUID pollId,
            @Param("voterId")  UUID voterId,
            @Param("optionId") String optionId);

    boolean existsByPollIdAndVoterId(UUID pollId, UUID voterId);
}

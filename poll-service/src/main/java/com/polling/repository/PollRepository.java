package com.polling.repository;

import com.polling.entity.Poll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PollRepository extends JpaRepository<Poll, UUID> {

    Page<Poll> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Find polls past their end time that are still marked ACTIVE — for the closure scheduler. */
    @Query("SELECT p FROM Poll p WHERE p.endTime <= :now AND p.status = 'ACTIVE'")
    List<Poll> findExpiredActivePolls(Instant now);

    /** Bulk close expired polls. */
    @Modifying
    @Query("UPDATE Poll p SET p.status = 'CLOSED' WHERE p.endTime <= :now AND p.status = 'ACTIVE'")
    int closeExpiredPolls(Instant now);
}

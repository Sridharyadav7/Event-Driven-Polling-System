package com.polling.controller;

import com.polling.dto.CreatePollRequest;
import com.polling.dto.PollResponse;
import com.polling.dto.VoteRequest;
import com.polling.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for poll management and vote casting.
 * All write operations require a valid JWT (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    /** Create a new poll. Requires authentication. */
    @PostMapping
    public ResponseEntity<PollResponse> createPoll(
            @AuthenticationPrincipal UUID creatorId,
            @Valid @RequestBody CreatePollRequest request) {
        PollResponse response = pollService.createPoll(creatorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** List all polls with pagination. Public endpoint. */
    @GetMapping
    public ResponseEntity<Page<PollResponse>> listPolls(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(pollService.listPolls(pageable));
    }

    /** Get a poll with live vote counts. Public endpoint. */
    @GetMapping("/{pollId}")
    public ResponseEntity<PollResponse> getPoll(@PathVariable UUID pollId) {
        return ResponseEntity.ok(pollService.getPoll(pollId));
    }

    /**
     * Cast a vote on a poll. Requires authentication.
     * Returns 200 if accepted (event queued), 409 if duplicate or poll is closed.
     */
    @PostMapping("/{pollId}/vote")
    public ResponseEntity<Void> castVote(
            @PathVariable UUID pollId,
            @AuthenticationPrincipal UUID voterId,
            @Valid @RequestBody VoteRequest request) {
        pollService.castVote(pollId, voterId, request);
        return ResponseEntity.ok().build();
    }

    /** Get live vote results for a poll. Public endpoint. */
    @GetMapping("/{pollId}/results")
    public ResponseEntity<PollResponse> getResults(@PathVariable UUID pollId) {
        return ResponseEntity.ok(pollService.getPoll(pollId));
    }
}

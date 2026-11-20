package com.polling.service;

import com.polling.dto.CreatePollRequest;
import com.polling.dto.PollResponse;
import com.polling.dto.VoteRequest;
import com.polling.entity.Poll;
import com.polling.entity.User;
import com.polling.event.VoteCastEvent;
import com.polling.exception.*;
import com.polling.repository.PollRepository;
import com.polling.repository.UserRepository;
import com.polling.repository.VoteRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock PollRepository pollRepository;
    @Mock UserRepository userRepository;
    @Mock VoteRepository voteRepository;
    @Mock KafkaTemplate<String, VoteCastEvent> kafkaTemplate;
    @Mock ResultsService resultsService;
    @Spy  MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @InjectMocks PollService pollService;

    private User sampleUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("alice@test.com")
                .name("Alice")
                .build();
    }

    private Poll samplePoll(User creator) {
        return Poll.builder()
                .pollId(UUID.randomUUID())
                .creator(creator)
                .question("Favorite color?")
                .options(List.of(
                        new Poll.PollOption("opt-1", "Red"),
                        new Poll.PollOption("opt-2", "Blue")))
                .startTime(Instant.now().minusSeconds(60))
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(Poll.Status.ACTIVE)
                .build();
    }

    @Test
    void createPoll_success() {
        User creator = sampleUser();
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));

        Poll savedPoll = samplePoll(creator);
        when(pollRepository.save(any(Poll.class))).thenReturn(savedPoll);

        CreatePollRequest req = new CreatePollRequest();
        req.setQuestion("Favorite color?");
        req.setOptions(List.of("Red", "Blue"));
        req.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));

        PollResponse response = pollService.createPoll(creator.getId(), req);

        assertThat(response.getQuestion()).isEqualTo("Favorite color?");
        assertThat(response.getOptions()).hasSize(2);
        verify(pollRepository).save(any(Poll.class));
    }

    @Test
    void castVote_publishesKafkaEvent() {
        User voter = sampleUser();
        Poll poll = samplePoll(voter);
        UUID pollId = poll.getPollId();
        UUID voterId = voter.getId();

        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(voteRepository.existsByPollIdAndVoterId(pollId, voterId)).thenReturn(false);

        VoteRequest req = new VoteRequest();
        req.setOptionId("opt-1");

        pollService.castVote(pollId, voterId, req);

        ArgumentCaptor<VoteCastEvent> captor = ArgumentCaptor.forClass(VoteCastEvent.class);
        verify(kafkaTemplate).send(eq("vote_cast"), eq(pollId.toString()), captor.capture());
        assertThat(captor.getValue().getPollId()).isEqualTo(pollId);
        assertThat(captor.getValue().getVoterId()).isEqualTo(voterId);
        assertThat(captor.getValue().getOptionId()).isEqualTo("opt-1");
        assertThat(captor.getValue().getEventId()).isNotNull();
    }

    @Test
    void castVote_duplicateVote_throws() {
        User voter = sampleUser();
        Poll poll = samplePoll(voter);
        UUID pollId = poll.getPollId();
        UUID voterId = voter.getId();

        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(voteRepository.existsByPollIdAndVoterId(pollId, voterId)).thenReturn(true);

        VoteRequest req = new VoteRequest();
        req.setOptionId("opt-1");

        assertThatThrownBy(() -> pollService.castVote(pollId, voterId, req))
                .isInstanceOf(DuplicateVoteException.class)
                .hasMessageContaining("already voted");

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void castVote_closedPoll_throws() {
        User voter = sampleUser();
        Poll poll = Poll.builder()
                .pollId(UUID.randomUUID())
                .creator(voter)
                .question("Q?")
                .options(List.of(new Poll.PollOption("opt-1", "Yes")))
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().minusSeconds(1))  // expired
                .status(Poll.Status.ACTIVE)
                .build();

        when(pollRepository.findById(poll.getPollId())).thenReturn(Optional.of(poll));

        VoteRequest req = new VoteRequest();
        req.setOptionId("opt-1");

        assertThatThrownBy(() -> pollService.castVote(poll.getPollId(), voter.getId(), req))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    void castVote_invalidOption_throws() {
        User voter = sampleUser();
        Poll poll = samplePoll(voter);
        UUID pollId = poll.getPollId();
        UUID voterId = voter.getId();

        when(pollRepository.findById(pollId)).thenReturn(Optional.of(poll));
        when(voteRepository.existsByPollIdAndVoterId(pollId, voterId)).thenReturn(false);

        VoteRequest req = new VoteRequest();
        req.setOptionId("INVALID_OPTION");

        assertThatThrownBy(() -> pollService.castVote(pollId, voterId, req))
                .isInstanceOf(InvalidOptionException.class);
    }

    @Test
    void getPoll_notFound_throws() {
        UUID pollId = UUID.randomUUID();
        when(pollRepository.findById(pollId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pollService.getPoll(pollId))
                .isInstanceOf(PollNotFoundException.class);
    }
}

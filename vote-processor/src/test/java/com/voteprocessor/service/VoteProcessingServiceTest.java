package com.voteprocessor.service;

import com.voteprocessor.entity.ProcessedEvent;
import com.voteprocessor.event.VoteCastEvent;
import com.voteprocessor.repository.ProcessedEventRepository;
import com.voteprocessor.repository.VoteRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteProcessingServiceTest {

    @Mock VoteRepository voteRepository;
    @Mock ProcessedEventRepository processedEventRepository;
    @Mock RedisCounterService redisCounterService;
    @Spy  SimpleMeterRegistry meterRegistry;
    @InjectMocks VoteProcessingService service;

    private VoteCastEvent sampleEvent() {
        return VoteCastEvent.builder()
                .eventId(UUID.randomUUID())
                .pollId(UUID.randomUUID())
                .voterId(UUID.randomUUID())
                .optionId("opt-1")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void processVote_newEvent_insertsVoteAndUpdatesRedis() {
        VoteCastEvent event = sampleEvent();
        when(processedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(voteRepository.insertIgnoreDuplicate(
                any(UUID.class), eq(event.getPollId()), eq(event.getVoterId()), eq("opt-1")))
                .thenReturn(1);

        service.processVote(event);

        verify(voteRepository).insertIgnoreDuplicate(
                any(UUID.class), eq(event.getPollId()), eq(event.getVoterId()), eq("opt-1"));
        verify(redisCounterService).incrementCounter(event.getPollId(), "opt-1");

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    void processVote_duplicateEvent_skipsAll() {
        VoteCastEvent event = sampleEvent();
        when(processedEventRepository.existsByEventId(event.getEventId())).thenReturn(true);

        service.processVote(event);

        verifyNoInteractions(voteRepository);
        verifyNoInteractions(redisCounterService);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void processVote_dbConflict_zeroRowsInserted_skipRedisButMarkProcessed() {
        VoteCastEvent event = sampleEvent();
        when(processedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        // ON CONFLICT DO NOTHING — 0 rows inserted
        when(voteRepository.insertIgnoreDuplicate(any(), any(), any(), any())).thenReturn(0);

        service.processVote(event);

        // Redis counter should NOT be incremented when no DB row was inserted
        verifyNoInteractions(redisCounterService);
        // Event must still be marked processed to prevent retry storms
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void processVote_dataIntegrityViolation_marksProcessed() {
        VoteCastEvent event = sampleEvent();
        when(processedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        when(voteRepository.insertIgnoreDuplicate(any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique constraint"));

        service.processVote(event);

        verifyNoInteractions(redisCounterService);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}

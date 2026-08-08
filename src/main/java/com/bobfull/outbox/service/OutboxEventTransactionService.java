package com.bobfull.outbox.service;

import com.bobfull.outbox.entity.OutboxEvent;
import com.bobfull.outbox.entity.OutboxEventStatus;
import com.bobfull.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Outbox 상태 전이만 짧은 독립 트랜잭션으로 수행해 ChatRoom 저장 동안 행 잠금을 유지하지 않는다. */
@Service
public class OutboxEventTransactionService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventTransactionService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedOutboxEvent> claim(Long eventId, Instant now) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null) return Optional.empty();

        String token = UUID.randomUUID().toString();
        if (outboxEventRepository.claim(eventId, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING, now, token) == 0) {
            return Optional.empty();
        }
        return Optional.of(new ClaimedOutboxEvent(eventId, event.getEventType().name(), event.getAggregateId(),
                event.getAttemptCount(), token));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(ClaimedOutboxEvent event, Instant now) {
        return outboxEventRepository.complete(event.id(), OutboxEventStatus.PROCESSING, OutboxEventStatus.COMPLETED,
                event.token(), now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureResult fail(ClaimedOutboxEvent event, String errorCode, Instant now, int maxAttempts) {
        int attemptCount = event.attemptCount() + 1;
        boolean failed = attemptCount >= maxAttempts;
        Instant nextAttemptAt = failed ? now : now.plusSeconds(1L << (attemptCount - 1));
        int updated = outboxEventRepository.fail(event.id(), OutboxEventStatus.PROCESSING,
                failed ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING, event.token(), attemptCount,
                nextAttemptAt, errorCode);
        return new FailureResult(updated == 1, failed, attemptCount, nextAttemptAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStale(Long eventId, Instant cutoff, Instant now) {
        return outboxEventRepository.recoverStale(eventId, OutboxEventStatus.PROCESSING, OutboxEventStatus.PENDING,
                cutoff, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryManually(Long eventId, Instant now) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox 이벤트를 찾을 수 없습니다."));
        event.retryManually(now);
    }

    public record ClaimedOutboxEvent(Long id, String eventType, Long aggregateId, int attemptCount, String token) {
    }

    public record FailureResult(boolean updated, boolean failed, int attemptCount, Instant nextAttemptAt) {
    }
}

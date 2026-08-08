package com.bobfull.outbox.repository;

import com.bobfull.outbox.entity.OutboxEvent;
import com.bobfull.outbox.entity.OutboxEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("select e.id from OutboxEvent e where e.status = :status and e.nextAttemptAt <= :now "
            + "order by e.nextAttemptAt asc, e.id asc")
    List<Long> findDueEventIds(@Param("status") OutboxEventStatus status, @Param("now") Instant now, Pageable pageable);

    @Query("select e.id from OutboxEvent e where e.status = :status and e.processingStartedAt <= :cutoff "
            + "order by e.processingStartedAt asc, e.id asc")
    List<Long> findStaleProcessingEventIds(@Param("status") OutboxEventStatus status, @Param("cutoff") Instant cutoff,
                                            Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = :processing, e.processingStartedAt = :now, "
            + "e.processingToken = :token where e.id = :id and e.status = :pending and e.nextAttemptAt <= :now")
    int claim(@Param("id") Long id, @Param("pending") OutboxEventStatus pending,
              @Param("processing") OutboxEventStatus processing, @Param("now") Instant now, @Param("token") String token);

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = :completed, e.processedAt = :now, e.processingStartedAt = null, "
            + "e.processingToken = null, e.lastErrorCode = null where e.id = :id and e.status = :processing "
            + "and e.processingToken = :token")
    int complete(@Param("id") Long id, @Param("processing") OutboxEventStatus processing,
                 @Param("completed") OutboxEventStatus completed, @Param("token") String token, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = :nextStatus, e.attemptCount = :attemptCount, "
            + "e.nextAttemptAt = :nextAttemptAt, e.lastErrorCode = :errorCode, e.processingStartedAt = null, "
            + "e.processingToken = null where e.id = :id and e.status = :processing and e.processingToken = :token")
    int fail(@Param("id") Long id, @Param("processing") OutboxEventStatus processing,
             @Param("nextStatus") OutboxEventStatus nextStatus, @Param("token") String token,
             @Param("attemptCount") int attemptCount, @Param("nextAttemptAt") Instant nextAttemptAt,
             @Param("errorCode") String errorCode);

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent e set e.status = :pending, e.nextAttemptAt = :now, e.processingStartedAt = null, "
            + "e.processingToken = null where e.id = :id and e.status = :processing and e.processingStartedAt <= :cutoff")
    int recoverStale(@Param("id") Long id, @Param("processing") OutboxEventStatus processing,
                     @Param("pending") OutboxEventStatus pending, @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    Optional<OutboxEvent> findByEventId(String eventId);
}

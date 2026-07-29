package com.bobfull.timeslot.repository;

import com.bobfull.timeslot.entity.TimeSlot;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Optional<TimeSlot> findByIdAndDeletedAtIsNull(Long id);

    boolean existsBySharedTableIdAndDeletedAtIsNull(Long sharedTableId);

    boolean existsBySharedTableIdAndStartAtAndDeletedAtIsNull(Long sharedTableId, Instant startAt);

    boolean existsBySharedTableIdAndStartAtAndDeletedAtIsNullAndIdNot(
            Long sharedTableId,
            Instant startAt,
            Long id
    );

    Page<TimeSlot> findAllBySharedTableIdInAndDeletedAtIsNullOrderByStartAtAsc(
            Collection<Long> sharedTableIds,
            Pageable pageable
    );

    Page<TimeSlot> findAllBySharedTableIdInAndStartAtGreaterThanEqualAndStartAtLessThanAndDeletedAtIsNullOrderByStartAtAsc(
            Collection<Long> sharedTableIds,
            Instant startAtInclusive,
            Instant startAtExclusive,
            Pageable pageable
    );

    List<TimeSlot> findAllBySharedTableIdInAndStartAtGreaterThanEqualAndStartAtLessThanAndDeletedAtIsNullOrderByStartAtAsc(
            Collection<Long> sharedTableIds,
            Instant startAtInclusive,
            Instant startAtExclusive
    );
}

package com.bobfull.payment.repository;

import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPayment_Id(Long paymentId);

    Optional<Refund> findByCancellationId(String cancellationId);

    /**
     * Refund 상태 전이(REQUESTED/PROCESSING/COMPLETED/FAILED) 경로를 직렬화하기 위한 비관적 락
     * 조회다. 즉시 응답·CancelPending·Cancelled 웹훅이 같은 Refund를 동시에 갱신하려 할 때, 락 없는
     * 조회는 각 트랜잭션이 읽은 메모리 스냅샷만으로 판단해 나중에 커밋된 값이 앞선 완료 상태를
     * 덮어쓰는 lost-update를 막지 못한다. 이 락 조회는 뒤 트랜잭션이 앞 트랜잭션의 커밋을 기다린 뒤
     * 최신 상태를 다시 읽게 강제한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Refund> findWithLockById(Long refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Refund> findWithLockByCancellationId(String cancellationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Refund> findWithLockByPayment_Id(Long paymentId);

    @EntityGraph(attributePaths = "payment")
    @org.springframework.data.jpa.repository.Query("select r from Refund r "
            + "where r.status in :statuses and r.updatedAt <= :updatedBefore "
            + "and (r.lastPgCheckedAt is null or r.lastPgCheckedAt <= :checkedBefore) "
            + "order by coalesce(r.lastPgCheckedAt, r.updatedAt) asc, r.id asc")
    List<Refund> findReconciliationCandidates(
            @org.springframework.data.repository.query.Param("statuses") List<RefundStatus> statuses,
            @org.springframework.data.repository.query.Param("updatedBefore") Instant updatedBefore,
            @org.springframework.data.repository.query.Param("checkedBefore") Instant checkedBefore,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Page<Refund> findAllByPayment_MemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Page<Refund> findAllByStatus(RefundStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Page<Refund> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Page<Refund> findAllByPayment_MemberIdAndStatus(Long memberId, RefundStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Optional<Refund> findByIdAndPayment_MemberId(Long refundId, Long memberId);

    @EntityGraph(attributePaths = "payment")
    java.util.List<Refund> findAllByPayment_ReservationId(Long reservationId);

    @EntityGraph(attributePaths = "payment")
    java.util.List<Refund> findAllByPayment_ReservationIdIn(java.util.Collection<Long> reservationIds);
}

package com.bobfull.payment.repository;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Collection;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByPaymentIdAndMemberId(String paymentId, Long memberId);

    Page<Payment> findAllByMemberId(Long memberId, Pageable pageable);

    Page<Payment> findAllByMemberIdAndStatus(Long memberId, PaymentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockByPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockById(Long id);

    @Query("select p.id from Payment p where p.status = :status and p.expiresAt <= :cutoff order by p.expiresAt asc, p.id asc")
    List<Long> findExpirationCandidateIds(@Param("status") PaymentStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);

    boolean existsByTimeSlotIdAndPurposeAndStatusAndExpiresAtAfter(
            Long timeSlotId, PaymentPurpose purpose, PaymentStatus status, Instant now);

    @Query("select coalesce(sum(p.partySize), 0) from Payment p "
            + "where p.timeSlotId = :timeSlotId and p.status = :status and p.expiresAt > :now")
    int sumPartySizeByTimeSlotIdAndStatusAndExpiresAtAfter(
            @Param("timeSlotId") Long timeSlotId, @Param("status") PaymentStatus status, @Param("now") Instant now);

    boolean existsByReservationIdAndMemberIdAndPurposeAndStatusAndExpiresAtAfter(
            Long reservationId, Long memberId, PaymentPurpose purpose, PaymentStatus status, Instant now);

    List<Payment> findAllByReservationIdAndPaidAtIsNotNull(Long reservationId);

    List<Payment> findAllByReservationIdInAndPaidAtIsNotNull(Collection<Long> reservationIds);

    @Query("select coalesce(sum(p.amount), 0), coalesce(sum(case when f.status = :completedStatus then f.amount else 0 end), 0) "
            + "from Payment p join TimeSlot ts on p.timeSlotId = ts.id join SharedTable st on ts.sharedTableId = st.id "
            + "left join Refund f on f.payment = p "
            + "where st.restaurantId = :restaurantId and p.paidAt is not null "
            + "and (:startAt is null or ts.startAt >= :startAt) and (:endAt is null or ts.startAt < :endAt)")
    List<Object[]> sumSettlementAmounts(
            @Param("restaurantId") Long restaurantId,
            @Param("completedStatus") com.bobfull.payment.entity.RefundStatus completedStatus,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );
}

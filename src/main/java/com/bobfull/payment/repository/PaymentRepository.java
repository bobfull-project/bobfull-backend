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
import java.util.List;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

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
}

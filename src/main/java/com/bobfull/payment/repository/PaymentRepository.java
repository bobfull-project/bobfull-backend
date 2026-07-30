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
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockByPaymentId(String paymentId);

    boolean existsByTimeSlotIdAndPurposeAndStatusAndExpiresAtAfter(
            Long timeSlotId, PaymentPurpose purpose, PaymentStatus status, Instant now);

    @Query("select coalesce(sum(p.partySize), 0) from Payment p "
            + "where p.timeSlotId = :timeSlotId and p.status = :status and p.expiresAt > :now")
    int sumPartySizeByTimeSlotIdAndStatusAndExpiresAtAfter(
            @Param("timeSlotId") Long timeSlotId, @Param("status") PaymentStatus status, @Param("now") Instant now);

    boolean existsByReservationIdAndMemberIdAndPurposeAndStatusAndExpiresAtAfter(
            Long reservationId, Long memberId, PaymentPurpose purpose, PaymentStatus status, Instant now);
}

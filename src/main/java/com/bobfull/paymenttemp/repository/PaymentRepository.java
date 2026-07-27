package com.bobfull.paymenttemp.repository;

import com.bobfull.paymenttemp.entity.Payment;
import com.bobfull.paymenttemp.entity.PaymentPurpose;
import com.bobfull.paymenttemp.entity.PaymentStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** 회차당 만료되지 않은 CREATE READY Payment는 최대 1건이다(ERD 4.7, ADR 0001). */
    boolean existsByTimeSlotIdAndPaymentPurposeAndPaymentStatusAndExpiresAtAfter(
            Long timeSlotId, PaymentPurpose paymentPurpose, PaymentStatus paymentStatus, Instant now);

    /** 같은 회원의 동일 예약에 대한 중복 JOIN 결제 준비를 막기 위한 확인이다. */
    boolean existsByReservationIdAndMemberIdAndPaymentStatusAndExpiresAtAfter(
            Long reservationId, Long memberId, PaymentStatus paymentStatus, Instant now);

    @Query("select coalesce(sum(p.partySize), 0) from Payment p "
            + "where p.reservationId = :reservationId and p.paymentStatus = :paymentStatus and p.expiresAt > :now")
    int sumPartySizeByReservationIdAndPaymentStatusAndExpiresAtAfter(
            @Param("reservationId") Long reservationId,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("now") Instant now);
}

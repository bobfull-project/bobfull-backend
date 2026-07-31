package com.bobfull.payment.repository;

import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @EntityGraph(attributePaths = "payment")
    Page<Refund> findAllByPayment_MemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Page<Refund> findAllByPayment_MemberIdAndStatus(Long memberId, RefundStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "payment")
    Optional<Refund> findByIdAndPayment_MemberId(Long refundId, Long memberId);

    @EntityGraph(attributePaths = "payment")
    java.util.List<Refund> findAllByPayment_ReservationId(Long reservationId);

    @EntityGraph(attributePaths = "payment")
    java.util.List<Refund> findAllByPayment_ReservationIdIn(java.util.Collection<Long> reservationIds);
}

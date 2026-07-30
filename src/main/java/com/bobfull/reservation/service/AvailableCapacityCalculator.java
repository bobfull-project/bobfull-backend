package com.bobfull.reservation.service;

import com.bobfull.payment.service.PaymentHoldReader;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 테이블 정원에서 결제 완료 참여 인원과 만료되지 않은 READY 임시 선점 인원을 차감해
 * 남은 참여 가능 인원을 계산한다(ADR 0001, docs/DOMAIN_DEPENDENCIES.md §4).
 */
@Service
public class AvailableCapacityCalculator {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final PaymentHoldReader paymentHoldReader;

    public AvailableCapacityCalculator(
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            PaymentHoldReader paymentHoldReader
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.paymentHoldReader = paymentHoldReader;
    }

    public int calculate(Long timeSlotId, Integer tableCapacity) {
        int currentParticipantCount = reservationRepository
                .findByTimeSlotIdAndReservationStatusIn(timeSlotId, ACTIVE_STATUSES)
                .map(this::activeParticipantCount)
                .orElse(0);
        int pendingHoldCount = paymentHoldReader.sumActiveReadyPartySize(timeSlotId);
        return Math.max(0, tableCapacity - currentParticipantCount - pendingHoldCount);
    }

    private int activeParticipantCount(Reservation reservation) {
        return reservationParticipantRepository.sumPartySize(reservation.getId(), ParticipationStatus.RESERVED);
    }
}

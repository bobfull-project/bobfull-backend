package com.bobfull.reservation.service;

import com.bobfull.payment.service.PaymentHoldReader;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.policy.ReservationCapacityPolicy;
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
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLING);
    private static final List<ReservationStatus> CLOSED_STATUS = List.of(ReservationStatus.CLOSED);
    private static final List<ParticipationStatus> OCCUPYING_STATUSES =
            List.of(ParticipationStatus.RESERVED, ParticipationStatus.CANCEL_REQUESTED);

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

    /**
     * {@code CLOSED}(식사 종료로 생명주기가 끝난 예약)가 있으면 참여자 상태와 무관하게 0을
     * 반환한다(PR #178 리뷰 반영, Issue #175). 노쇼 처리로 `RESERVED` 참여자가 `NO_SHOW`로
     * 빠져 점유 합계가 줄어도, 이미 끝난 회차의 좌석이 다시 열려 재예약으로 이어지면 안 된다.
     */
    public int calculate(Long timeSlotId, Integer tableCapacity) {
        if (reservationRepository.existsByTimeSlotIdAndReservationStatusIn(timeSlotId, CLOSED_STATUS)) {
            return 0;
        }
        int currentParticipantCount = reservationRepository
                .findByTimeSlotIdAndReservationStatusIn(timeSlotId, ACTIVE_STATUSES)
                .map(this::activeParticipantCount)
                .orElse(0);
        int pendingHoldCount = paymentHoldReader.sumActiveReadyPartySize(timeSlotId);
        return ReservationCapacityPolicy.availableCapacity(tableCapacity, currentParticipantCount, pendingHoldCount);
    }

    private int activeParticipantCount(Reservation reservation) {
        return reservationParticipantRepository.sumPartySizeByStatuses(reservation.getId(), OCCUPYING_STATUSES);
    }
}

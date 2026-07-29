package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;

/**
 * 결제 완료 시 실제 Reservation·ReservationParticipant를 생성·갱신하는 예약 도메인의 확정 서비스다.
 * Payment 도메인의 결제 완료 트랜잭션(PaymentCompletionTransactionService) 안에서 호출되도록
 * 설계되었으므로 자체 {@code @Transactional}을 선언하지 않고 호출자의 트랜잭션에 참여한다.
 * 실제 {@code ReservationConfirmationPort} 구현과 웹훅 연결은 #93에서 이 서비스를 호출해 수행한다.
 */
@Service
public class ReservationConfirmationService {

    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SharedTableRepository sharedTableRepository;

    public ReservationConfirmationService(
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            TimeSlotRepository timeSlotRepository,
            SharedTableRepository sharedTableRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.sharedTableRepository = sharedTableRepository;
    }

    /**
     * CREATE는 새 Reservation과 최초 ReservationParticipant를, JOIN은 기존 Reservation에
     * ReservationParticipant를 추가로 생성한다. 정원에 도달하면 Reservation을 CONFIRMED로 전이한다.
     */
    public ReservationConfirmationResult confirm(
            PaymentPurpose purpose, Long timeSlotId, Long reservationId, Long memberId, Integer partySize
    ) {
        Reservation reservation = (purpose == PaymentPurpose.CREATE)
                ? reservationRepository.save(Reservation.create(timeSlotId, memberId))
                : findReservationOrThrow(reservationId);

        ReservationParticipant participant = reservationParticipantRepository.save(
                ReservationParticipant.create(reservation.getId(), memberId, partySize));

        confirmIfTableIsFull(reservation, timeSlotId);
        return new ReservationConfirmationResult(reservation.getId(), participant.getId());
    }

    private void confirmIfTableIsFull(Reservation reservation, Long timeSlotId) {
        int tableCapacity = tableCapacityOf(timeSlotId);
        int currentParticipantCount = reservationParticipantRepository.sumPartySize(
                reservation.getId(), ParticipationStatus.RESERVED);
        if (currentParticipantCount >= tableCapacity) {
            reservation.confirm();
        }
    }

    private int tableCapacityOf(Long timeSlotId) {
        TimeSlot timeSlot = timeSlotRepository.findByIdAndDeletedAtIsNull(timeSlotId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
        SharedTable sharedTable = sharedTableRepository.findByIdAndDeletedAtIsNull(timeSlot.getSharedTableId())
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
        return sharedTable.getCapacity();
    }

    private Reservation findReservationOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }

    public record ReservationConfirmationResult(Long reservationId, Long reservationParticipantId) {
    }
}

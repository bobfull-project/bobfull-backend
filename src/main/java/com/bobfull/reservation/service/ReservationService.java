package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.ReservationPrepareRequest;
import com.bobfull.reservation.dto.ReservationPrepareResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.port.CreateReadyPaymentCommand;
import com.bobfull.reservation.port.PaymentPurpose;
import com.bobfull.reservation.port.PaymentReadyCreator;
import com.bobfull.reservation.port.ReadyPaymentResult;
import com.bobfull.reservation.port.TimeSlotLockPort;
import com.bobfull.reservation.port.TimeSlotLockResult;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 결제 준비(#35)를 담당한다. 결제 성공 전에는 Reservation·ReservationParticipant를
 * 생성하지 않으며(ADR 0001), TimeSlot 잠금을 트랜잭션 종료까지 유지한 채 좌석 정합성을
 * 확인한 뒤 Payment 도메인의 READY 생성 계약만 호출한다.
 * TimeSlot(#33)·Payment(#91)의 실제 구현은 각 도메인이 소유하며, 이 서비스는
 * {@link TimeSlotLockPort}·{@link PaymentReadyCreator} 계약에만 의존한다.
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);
    private static final List<ParticipationStatus> OCCUPYING_PARTICIPATION_STATUSES =
            List.of(ParticipationStatus.RESERVED, ParticipationStatus.NO_SHOW);

    private final TimeSlotLockPort timeSlotLockPort;
    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final PaymentReadyCreator paymentReadyCreator;
    private final Clock clock;

    public ReservationService(
            TimeSlotLockPort timeSlotLockPort,
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            PaymentReadyCreator paymentReadyCreator,
            Clock clock
    ) {
        this.timeSlotLockPort = timeSlotLockPort;
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.paymentReadyCreator = paymentReadyCreator;
        this.clock = clock;
    }

    @Transactional
    public ReservationPrepareResponse prepare(Long memberId, ReservationPrepareRequest request) {
        if (request.type() == PaymentPurpose.CREATE) {
            return prepareCreate(memberId, request.targetId(), request.partySize());
        }
        return prepareJoin(memberId, request.targetId(), request.partySize());
    }

    private ReservationPrepareResponse prepareCreate(Long memberId, Long timeSlotId, int partySize) {
        TimeSlotLockResult timeSlot = lockTimeSlotOrThrow(timeSlotId);

        if (partySize > timeSlot.capacity()) {
            throw new CustomException(ReservationErrorCode.INVALID_PARTY_SIZE);
        }

        boolean hasActiveReservation = reservationRepository
                .existsByTimeSlotIdAndReservationStatusIn(timeSlotId, ACTIVE_RESERVATION_STATUSES);
        boolean hasValidCreateReady = paymentReadyCreator.existsValidCreateReady(timeSlotId, clock.instant());
        if (hasActiveReservation || hasValidCreateReady) {
            throw new CustomException(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
        }

        BigDecimal amount = timeSlot.depositPerPerson().multiply(BigDecimal.valueOf(partySize));
        ReadyPaymentResult payment = paymentReadyCreator.createReadyPayment(new CreateReadyPaymentCommand(
                memberId, timeSlotId, null, PaymentPurpose.CREATE, partySize, amount));

        return ReservationPrepareResponse.from(payment);
    }

    private ReservationPrepareResponse prepareJoin(Long memberId, Long reservationId, int partySize) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));

        if (!reservation.isActive() || !reservation.isRecruitmentOpen()) {
            throw new CustomException(ReservationErrorCode.INVALID_STATE);
        }
        if (reservation.getCreatorMemberId().equals(memberId)) {
            throw new CustomException(ReservationErrorCode.INVALID_STATE);
        }

        // 최초 예약과 동일하게 대상 회차를 잠가 동시 참여 요청의 좌석 계산을 직렬화한다.
        TimeSlotLockResult timeSlot = lockTimeSlotOrThrow(reservation.getTimeSlotId());

        Instant now = clock.instant();
        boolean alreadyParticipating = reservationParticipantRepository
                .existsByReservationIdAndMemberIdAndParticipationStatus(
                        reservationId, memberId, ParticipationStatus.RESERVED);
        boolean hasPendingJoinReady = paymentReadyCreator.existsValidJoinReady(reservationId, memberId, now);
        if (alreadyParticipating || hasPendingJoinReady) {
            throw new CustomException(ReservationErrorCode.INVALID_STATE);
        }

        int currentParticipantCount = reservationParticipantRepository
                .sumPartySizeByReservationIdAndParticipationStatusIn(reservationId, OCCUPYING_PARTICIPATION_STATUSES);
        int heldPartySize = paymentReadyCreator.sumHeldPartySize(reservationId, now);
        int availableCapacity = timeSlot.capacity() - currentParticipantCount - heldPartySize;

        if (partySize > availableCapacity) {
            throw new CustomException(ReservationErrorCode.INSUFFICIENT_REMAINING_CAPACITY);
        }

        BigDecimal amount = timeSlot.depositPerPerson().multiply(BigDecimal.valueOf(partySize));
        ReadyPaymentResult payment = paymentReadyCreator.createReadyPayment(new CreateReadyPaymentCommand(
                memberId, timeSlot.timeSlotId(), reservationId, PaymentPurpose.JOIN, partySize, amount));

        return ReservationPrepareResponse.from(payment);
    }

    private TimeSlotLockResult lockTimeSlotOrThrow(Long timeSlotId) {
        return timeSlotLockPort.lockForReservation(timeSlotId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }
}

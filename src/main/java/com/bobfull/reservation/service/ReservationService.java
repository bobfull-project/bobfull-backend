package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.paymenttemp.dto.CreateReadyPaymentCommand;
import com.bobfull.paymenttemp.entity.Payment;
import com.bobfull.paymenttemp.entity.PaymentPurpose;
import com.bobfull.paymenttemp.entity.PaymentStatus;
import com.bobfull.paymenttemp.repository.PaymentRepository;
import com.bobfull.paymenttemp.service.PaymentService;
import com.bobfull.reservation.dto.ReservationPrepareRequest;
import com.bobfull.reservation.dto.ReservationPrepareResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.timeslottemp.entity.TimeSlot;
import com.bobfull.timeslottemp.repository.TableInfoProjection;
import com.bobfull.timeslottemp.repository.TimeSlotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 결제 준비(#35)를 담당한다. 결제 성공 전에는 Reservation·ReservationParticipant를
 * 생성하지 않으며(ADR 0001), TimeSlot 행 잠금을 트랜잭션 종료까지 유지한 채 좌석 정합성을
 * 확인한 뒤 Payment 도메인의 READY 생성 인터페이스만 호출한다.
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);
    private static final List<ParticipationStatus> OCCUPYING_PARTICIPATION_STATUSES =
            List.of(ParticipationStatus.RESERVED, ParticipationStatus.NO_SHOW);

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    public ReservationService(
            TimeSlotRepository timeSlotRepository,
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            Clock clock
    ) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
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
        TimeSlot timeSlot = lockTimeSlotOrThrow(timeSlotId);
        TableInfoProjection tableInfo = findTableInfoOrThrow(timeSlot.getSharedTableId());

        if (partySize > tableInfo.getCapacity()) {
            throw new CustomException(ReservationErrorCode.INVALID_PARTY_SIZE);
        }

        boolean hasActiveReservation = reservationRepository
                .existsByTimeSlotIdAndReservationStatusIn(timeSlotId, ACTIVE_RESERVATION_STATUSES);
        boolean hasValidCreateReady = paymentRepository
                .existsByTimeSlotIdAndPaymentPurposeAndPaymentStatusAndExpiresAtAfter(
                        timeSlotId, PaymentPurpose.CREATE, PaymentStatus.READY, clock.instant());
        if (hasActiveReservation || hasValidCreateReady) {
            throw new CustomException(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
        }

        BigDecimal amount = tableInfo.getDepositPerPerson().multiply(BigDecimal.valueOf(partySize));
        Payment payment = paymentService.createReadyPayment(new CreateReadyPaymentCommand(
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
        TimeSlot timeSlot = lockTimeSlotOrThrow(reservation.getTimeSlotId());
        TableInfoProjection tableInfo = findTableInfoOrThrow(timeSlot.getSharedTableId());

        Instant now = clock.instant();
        boolean alreadyParticipating = reservationParticipantRepository
                .existsByReservationIdAndMemberIdAndParticipationStatus(
                        reservationId, memberId, ParticipationStatus.RESERVED);
        boolean hasPendingJoinReady = paymentRepository
                .existsByReservationIdAndMemberIdAndPaymentStatusAndExpiresAtAfter(
                        reservationId, memberId, PaymentStatus.READY, now);
        if (alreadyParticipating || hasPendingJoinReady) {
            throw new CustomException(ReservationErrorCode.INVALID_STATE);
        }

        int currentParticipantCount = reservationParticipantRepository
                .sumPartySizeByReservationIdAndParticipationStatusIn(reservationId, OCCUPYING_PARTICIPATION_STATUSES);
        int heldPartySize = paymentRepository
                .sumPartySizeByReservationIdAndPaymentStatusAndExpiresAtAfter(reservationId, PaymentStatus.READY, now);
        int availableCapacity = tableInfo.getCapacity() - currentParticipantCount - heldPartySize;

        if (partySize > availableCapacity) {
            throw new CustomException(ReservationErrorCode.INSUFFICIENT_REMAINING_CAPACITY);
        }

        BigDecimal amount = tableInfo.getDepositPerPerson().multiply(BigDecimal.valueOf(partySize));
        Payment payment = paymentService.createReadyPayment(new CreateReadyPaymentCommand(
                memberId, timeSlot.getId(), reservationId, PaymentPurpose.JOIN, partySize, amount));

        return ReservationPrepareResponse.from(payment);
    }

    private TimeSlot lockTimeSlotOrThrow(Long timeSlotId) {
        return timeSlotRepository.findByIdForUpdate(timeSlotId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }

    private TableInfoProjection findTableInfoOrThrow(Long sharedTableId) {
        return timeSlotRepository.findTableInfo(sharedTableId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }
}

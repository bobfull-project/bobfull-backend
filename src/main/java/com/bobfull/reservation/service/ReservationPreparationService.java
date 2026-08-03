package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.payment.dto.CreateReadyPaymentCommand;
import com.bobfull.payment.dto.CreateReadyPaymentResult;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.service.PaymentHoldReader;
import com.bobfull.payment.service.ReadyPaymentCreator;
import com.bobfull.reservation.dto.ReservationAvailabilityResponse;
import com.bobfull.reservation.dto.ReservationPrepareRequest;
import com.bobfull.reservation.dto.ReservationPrepareResponse;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.port.ReservationTargetReader;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 예약 생성과 기존 예약 추가 참여의 예약 가능 여부 확인·결제 준비를 담당한다(Issue #35, ADR 0001).
 * 결제 성공 전에는 Reservation·ReservationParticipant를 생성하지 않으며, 실제 확정은 #93이
 * {@link com.bobfull.payment.port.ReservationConfirmationPort} 구현에서 이 도메인의
 * {@link ReservationConfirmationService}를 호출해 수행한다.
 */
@Service
public class ReservationPreparationService {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    private final ReservationTargetReader reservationTargetReader;
    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final PaymentHoldReader paymentHoldReader;
    private final ReadyPaymentCreator readyPaymentCreator;
    private final AvailableCapacityCalculator availableCapacityCalculator;

    public ReservationPreparationService(
            ReservationTargetReader reservationTargetReader,
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            PaymentHoldReader paymentHoldReader,
            ReadyPaymentCreator readyPaymentCreator,
            AvailableCapacityCalculator availableCapacityCalculator
    ) {
        this.reservationTargetReader = reservationTargetReader;
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.paymentHoldReader = paymentHoldReader;
        this.readyPaymentCreator = readyPaymentCreator;
        this.availableCapacityCalculator = availableCapacityCalculator;
    }

    @Transactional(readOnly = true)
    public ReservationAvailabilityResponse checkAvailability(
            Long memberId, PaymentPurpose type, Long targetId, Integer partySize
    ) {
        validatePartySizeInput(partySize);
        ValidatedTarget target = (type == PaymentPurpose.CREATE)
                ? resolveCreateTarget(targetId, partySize, false)
                : resolveJoinTarget(memberId, targetId, partySize, false);
        return ReservationAvailabilityResponse.available(target.availableCapacity());
    }

    @Transactional
    public ReservationPrepareResponse prepare(Long memberId, ReservationPrepareRequest request) {
        validatePartySizeInput(request.partySize());
        ValidatedTarget target = (request.type() == PaymentPurpose.CREATE)
                ? resolveCreateTarget(request.targetId(), request.partySize(), true)
                : resolveJoinTarget(memberId, request.targetId(), request.partySize(), true);

        BigDecimal amount = BigDecimal.valueOf(target.depositPerPerson()).multiply(BigDecimal.valueOf(request.partySize()));
        CreateReadyPaymentCommand command = new CreateReadyPaymentCommand(
                memberId, target.timeSlotId(), target.reservationId(), request.type(), request.partySize(), amount);
        CreateReadyPaymentResult result = readyPaymentCreator.createReadyPayment(command);
        return ReservationPrepareResponse.from(result);
    }

    // 락 순서: TimeSlot 단독(ADR 0001 "복수 비관적 락의 획득 순서" 참고).
    private ValidatedTarget resolveCreateTarget(Long timeSlotId, Integer partySize, boolean lock) {
        ReservationTargetReader.ReservationTarget target = reservationTargetReader.read(timeSlotId, lock);
        validatePartySizeAgainstCapacity(partySize, target.tableCapacity());
        validateNoActiveCreate(target.timeSlotId());

        int availableCapacity = availableCapacityCalculator.calculate(target.timeSlotId(), target.tableCapacity());
        return new ValidatedTarget(target.timeSlotId(), null, target.depositPerPerson(), availableCapacity);
    }

    private ValidatedTarget resolveJoinTarget(Long memberId, Long reservationId, Integer partySize, boolean lock) {
        // Reservation을 잠금 조회로 트랜잭션의 첫 쿼리로 만들어야 한다. MySQL REPEATABLE_READ에서는
        // 이후의 일반 SELECT(잔여 인원 합계 등)가 트랜잭션의 첫 조회 시점 스냅샷을 그대로 쓰기 때문에,
        // 잠금 없는 조회를 먼저 하면 TimeSlot 락을 기다렸다 풀려도 그 사이 상대가 커밋한 결과를
        // 못 보고 통과해버릴 수 있다(ADR 0001, Issue #36에서 재현·확인됨).
        // 락 순서: Reservation → TimeSlot(ADR 0001 "복수 비관적 락의 획득 순서" 참고, 역순 금지).
        Reservation reservation = lock ? findReservationWithLockOrThrow(reservationId) : findReservationOrThrow(reservationId);
        ReservationTargetReader.ReservationTarget target = reservationTargetReader.read(reservation.getTimeSlotId(), lock);

        validateJoinable(reservation);
        validateNotAlreadyParticipating(reservation.getId(), memberId);
        validateNoActiveJoinReady(reservation.getId(), memberId);

        int availableCapacity = availableCapacityCalculator.calculate(target.timeSlotId(), target.tableCapacity());
        validatePartySizeAgainstRemainingCapacity(partySize, availableCapacity);

        return new ValidatedTarget(target.timeSlotId(), reservation.getId(), target.depositPerPerson(), availableCapacity);
    }

    private void validateNoActiveCreate(Long timeSlotId) {
        boolean activeReservationExists = reservationRepository.existsByTimeSlotIdAndReservationStatusIn(
                timeSlotId, ACTIVE_STATUSES);
        boolean activeCreateReadyExists = paymentHoldReader.existsActiveReadyPayment(timeSlotId, PaymentPurpose.CREATE);
        if (activeReservationExists || activeCreateReadyExists) {
            throw new CustomException(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
        }
    }

    private void validateJoinable(Reservation reservation) {
        if (!reservation.isActive() || reservation.getRecruitmentStatus() != RecruitmentStatus.OPEN) {
            throw new CustomException(ReservationErrorCode.INVALID_STATE);
        }
    }

    private void validateNotAlreadyParticipating(Long reservationId, Long memberId) {
        if (reservationParticipantRepository.existsByReservationIdAndMemberId(reservationId, memberId)) {
            throw new CustomException(ReservationErrorCode.INVALID_STATE);
        }
    }

    /**
     * 결제 완료 전에는 ReservationParticipant가 생성되지 않으므로, 같은 회원이 같은 예약에
     * 반복해서 JOIN을 요청해도 {@link #validateNotAlreadyParticipating}만으로는 막을 수 없다.
     * 만료되지 않은 JOIN READY Payment 존재 여부로 중복 결제 준비 자체를 막는다.
     */
    private void validateNoActiveJoinReady(Long reservationId, Long memberId) {
        if (paymentHoldReader.existsActiveJoinReadyPayment(reservationId, memberId)) {
            throw new CustomException(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
        }
    }

    private void validatePartySizeInput(Integer partySize) {
        if (partySize == null || partySize < 1) {
            throw new CustomException(ReservationErrorCode.INVALID_PARTY_SIZE);
        }
    }

    private void validatePartySizeAgainstCapacity(Integer partySize, Integer tableCapacity) {
        if (partySize > tableCapacity) {
            throw new CustomException(ReservationErrorCode.INVALID_PARTY_SIZE);
        }
    }

    private void validatePartySizeAgainstRemainingCapacity(Integer partySize, int availableCapacity) {
        if (partySize > availableCapacity) {
            throw new CustomException(ReservationErrorCode.INSUFFICIENT_REMAINING_CAPACITY);
        }
    }

    private Reservation findReservationOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }

    private Reservation findReservationWithLockOrThrow(Long reservationId) {
        return reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
    }

    private record ValidatedTarget(Long timeSlotId, Long reservationId, Integer depositPerPerson, int availableCapacity) {
    }
}

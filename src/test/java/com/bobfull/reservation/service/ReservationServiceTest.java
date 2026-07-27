package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-27T00:00:00Z"),
            ZoneOffset.UTC
    );
    private static final Long MEMBER_ID = 1L;
    private static final Long TIME_SLOT_ID = 10L;
    private static final Long SHARED_TABLE_ID = 20L;
    private static final Long RESERVATION_ID = 30L;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private TableInfoProjection tableInfoProjection;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                timeSlotRepository,
                reservationRepository,
                reservationParticipantRepository,
                paymentRepository,
                paymentService,
                FIXED_CLOCK
        );
    }

    @Test
    void CREATE_자리가_있으면_READY_결제를_생성한다() {
        // given
        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));
        when(tableInfoProjection.getCapacity()).thenReturn(4);
        when(tableInfoProjection.getDepositPerPerson()).thenReturn(BigDecimal.valueOf(10000));
        when(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(any(), any())).thenReturn(false);
        when(paymentRepository.existsByTimeSlotIdAndPaymentPurposeAndPaymentStatusAndExpiresAtAfter(
                any(), any(), any(), any())).thenReturn(false);

        Payment readyPayment = readyPayment(PaymentPurpose.CREATE, TIME_SLOT_ID, null, 3, BigDecimal.valueOf(30000));
        when(paymentService.createReadyPayment(any())).thenReturn(readyPayment);

        // when
        ReservationPrepareResponse result = reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.CREATE, TIME_SLOT_ID, 3));

        // then
        assertThat(result.paymentId()).isEqualTo(readyPayment.getId());
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        verify(reservationRepository, never()).save(any());
        verify(reservationParticipantRepository, never()).save(any());
    }

    @Test
    void CREATE_partySize가_테이블_정원을_초과하면_INVALID_PARTY_SIZE를_반환하고_결제를_생성하지_않는다() {
        // given
        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));
        when(tableInfoProjection.getCapacity()).thenReturn(4);

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.CREATE, TIME_SLOT_ID, 5)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_PARTY_SIZE);
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void CREATE_활성_예약이_이미_있으면_ACTIVE_RESERVATION_ALREADY_EXISTS를_반환한다() {
        // given
        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));
        when(tableInfoProjection.getCapacity()).thenReturn(4);
        when(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(any(), any())).thenReturn(true);

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.CREATE, TIME_SLOT_ID, 2)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void CREATE_만료되지_않은_CREATE_READY가_이미_있으면_ACTIVE_RESERVATION_ALREADY_EXISTS를_반환한다() {
        // given
        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));
        when(tableInfoProjection.getCapacity()).thenReturn(4);
        when(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(any(), any())).thenReturn(false);
        when(paymentRepository.existsByTimeSlotIdAndPaymentPurposeAndPaymentStatusAndExpiresAtAfter(
                any(), any(), any(), any())).thenReturn(true);

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.CREATE, TIME_SLOT_ID, 2)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void CREATE_대상_회차가_없으면_RESOURCE_NOT_FOUND를_반환한다() {
        // given
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.CREATE, TIME_SLOT_ID, 2)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void JOIN_남은_자리가_있으면_READY_결제를_생성한다() {
        // given
        Reservation reservation = reservation(RESERVATION_ID, TIME_SLOT_ID, 99L);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));
        when(tableInfoProjection.getCapacity()).thenReturn(4);
        when(tableInfoProjection.getDepositPerPerson()).thenReturn(BigDecimal.valueOf(10000));

        when(reservationParticipantRepository.existsByReservationIdAndMemberIdAndParticipationStatus(
                RESERVATION_ID, MEMBER_ID, ParticipationStatus.RESERVED)).thenReturn(false);
        when(paymentRepository.existsByReservationIdAndMemberIdAndPaymentStatusAndExpiresAtAfter(
                any(), any(), any(), any())).thenReturn(false);
        when(reservationParticipantRepository.sumPartySizeByReservationIdAndParticipationStatusIn(any(), any()))
                .thenReturn(1);
        when(paymentRepository.sumPartySizeByReservationIdAndPaymentStatusAndExpiresAtAfter(any(), any(), any()))
                .thenReturn(0);

        Payment readyPayment = readyPayment(PaymentPurpose.JOIN, TIME_SLOT_ID, RESERVATION_ID, 2, BigDecimal.valueOf(20000));
        when(paymentService.createReadyPayment(any())).thenReturn(readyPayment);

        // when
        ReservationPrepareResponse result = reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.JOIN, RESERVATION_ID, 2));

        // then
        assertThat(result.paymentId()).isEqualTo(readyPayment.getId());
        verify(reservationRepository, never()).save(any());
        verify(reservationParticipantRepository, never()).save(any());
    }

    @Test
    void JOIN_partySize가_남은_참여_가능_인원을_초과하면_INSUFFICIENT_REMAINING_CAPACITY를_반환한다() {
        // given
        Reservation reservation = reservation(RESERVATION_ID, TIME_SLOT_ID, 99L);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));
        when(tableInfoProjection.getCapacity()).thenReturn(4);

        when(reservationParticipantRepository.existsByReservationIdAndMemberIdAndParticipationStatus(
                any(), any(), any())).thenReturn(false);
        when(paymentRepository.existsByReservationIdAndMemberIdAndPaymentStatusAndExpiresAtAfter(
                any(), any(), any(), any())).thenReturn(false);
        // 현재 참여 인원 3 + 임시 선점 1 = availableCapacity 0
        when(reservationParticipantRepository.sumPartySizeByReservationIdAndParticipationStatusIn(any(), any()))
                .thenReturn(3);
        when(paymentRepository.sumPartySizeByReservationIdAndPaymentStatusAndExpiresAtAfter(any(), any(), any()))
                .thenReturn(1);

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.JOIN, RESERVATION_ID, 1)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.INSUFFICIENT_REMAINING_CAPACITY);
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void JOIN_이미_참여중인_회원이면_INVALID_STATE를_반환하고_결제를_생성하지_않는다() {
        // given
        Reservation reservation = reservation(RESERVATION_ID, TIME_SLOT_ID, 99L);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        TimeSlot timeSlot = timeSlot(TIME_SLOT_ID, SHARED_TABLE_ID);
        when(timeSlotRepository.findByIdForUpdate(TIME_SLOT_ID)).thenReturn(Optional.of(timeSlot));
        when(timeSlotRepository.findTableInfo(SHARED_TABLE_ID)).thenReturn(Optional.of(tableInfoProjection));

        when(reservationParticipantRepository.existsByReservationIdAndMemberIdAndParticipationStatus(
                RESERVATION_ID, MEMBER_ID, ParticipationStatus.RESERVED)).thenReturn(true);

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.JOIN, RESERVATION_ID, 1)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_STATE);
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void JOIN_대상이_본인이_생성한_예약이면_INVALID_STATE를_반환한다() {
        // given
        Reservation reservation = reservation(RESERVATION_ID, TIME_SLOT_ID, MEMBER_ID);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.JOIN, RESERVATION_ID, 1)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_STATE);
        verify(timeSlotRepository, never()).findByIdForUpdate(anyLong());
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void JOIN_대상_예약이_취소_상태이면_INVALID_STATE를_반환한다() {
        // given
        Reservation reservation = reservation(RESERVATION_ID, TIME_SLOT_ID, 99L);
        ReflectionTestUtils.setField(reservation, "reservationStatus", ReservationStatus.CANCELLED);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.JOIN, RESERVATION_ID, 1)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_STATE);
        verify(paymentService, never()).createReadyPayment(any());
    }

    @Test
    void JOIN_대상_예약을_찾을_수_없으면_RESOURCE_NOT_FOUND를_반환한다() {
        // given
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(() -> reservationService.prepare(
                MEMBER_ID, new ReservationPrepareRequest(PaymentPurpose.JOIN, RESERVATION_ID, 1)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.RESOURCE_NOT_FOUND);
    }

    private TimeSlot timeSlot(Long id, Long sharedTableId) {
        TimeSlot timeSlot = TimeSlot.create(sharedTableId, Instant.parse("2026-07-27T09:00:00Z"),
                Instant.parse("2026-07-27T11:00:00Z"));
        ReflectionTestUtils.setField(timeSlot, "id", id);
        return timeSlot;
    }

    private Reservation reservation(Long id, Long timeSlotId, Long creatorMemberId) {
        Reservation reservation = Reservation.create(timeSlotId, creatorMemberId);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private Payment readyPayment(PaymentPurpose purpose, Long timeSlotId, Long reservationId, int partySize, BigDecimal amount) {
        return Payment.createReady(
                "PAY-20260727-TEST0001",
                MEMBER_ID,
                timeSlotId,
                reservationId,
                purpose,
                partySize,
                amount,
                "KRW",
                FIXED_CLOCK.instant().plusSeconds(600)
        );
    }
}

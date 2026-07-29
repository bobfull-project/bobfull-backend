package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.payment.dto.CreateReadyPaymentCommand;
import com.bobfull.payment.dto.CreateReadyPaymentResult;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.service.PaymentHoldReader;
import com.bobfull.payment.service.ReadyPaymentCreator;
import com.bobfull.reservation.dto.ReservationAvailabilityResponse;
import com.bobfull.reservation.dto.ReservationPrepareRequest;
import com.bobfull.reservation.dto.ReservationPrepareResponse;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationPreparationServiceTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private SharedTableRepository sharedTableRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    @Mock
    private PaymentHoldReader paymentHoldReader;

    @Mock
    private ReadyPaymentCreator readyPaymentCreator;

    @Mock
    private AvailableCapacityCalculator availableCapacityCalculator;

    private ReservationPreparationService service() {
        return new ReservationPreparationService(
                timeSlotRepository, sharedTableRepository, restaurantRepository,
                reservationRepository, reservationParticipantRepository,
                paymentHoldReader, readyPaymentCreator, availableCapacityCalculator);
    }

    @Test
    void CREATE_예약_가능_여부는_잠금_없이_조회한다() {
        // given
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES)).willReturn(false);
        given(paymentHoldReader.existsActiveReadyPayment(200L, PaymentPurpose.CREATE)).willReturn(false);
        given(availableCapacityCalculator.calculate(200L, 4)).willReturn(4);

        // when
        ReservationAvailabilityResponse response = service().checkAvailability(1L, PaymentPurpose.CREATE, 200L, 2);

        // then
        assertThat(response.available()).isTrue();
        assertThat(response.availableCapacity()).isEqualTo(4);
        verify(timeSlotRepository, never()).findWithLockByIdAndDeletedAtIsNull(any());
    }

    @Test
    void CREATE_결제_준비는_회차를_잠그고_READY_결제_생성을_요청한다() {
        // given
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(timeSlotRepository.findWithLockByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES)).willReturn(false);
        given(paymentHoldReader.existsActiveReadyPayment(200L, PaymentPurpose.CREATE)).willReturn(false);
        given(availableCapacityCalculator.calculate(200L, 4)).willReturn(4);
        given(readyPaymentCreator.createReadyPayment(any(CreateReadyPaymentCommand.class))).willReturn(
                new CreateReadyPaymentResult("payment-id", PaymentStatus.READY, BigDecimal.valueOf(20000),
                        Instant.parse("2026-07-25T08:10:00Z")));

        // when
        ReservationPrepareResponse response = service().prepare(
                1L, new ReservationPrepareRequest(PaymentPurpose.CREATE, 200L, 2));

        // then
        ArgumentCaptor<CreateReadyPaymentCommand> captor = ArgumentCaptor.forClass(CreateReadyPaymentCommand.class);
        verify(readyPaymentCreator).createReadyPayment(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(1L);
        assertThat(captor.getValue().timeSlotId()).isEqualTo(200L);
        assertThat(captor.getValue().reservationId()).isNull();
        assertThat(captor.getValue().purpose()).isEqualTo(PaymentPurpose.CREATE);
        assertThat(captor.getValue().amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(response.paymentId()).isEqualTo("payment-id");
        assertThat(response.expiresAt().toString()).isEqualTo("2026-07-25T17:10+09:00");
    }

    @Test
    void CREATE_partySize가_테이블_정원을_초과하면_400_예외가_발생한다() {
        // given
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        Throwable result = catchThrowable(
                () -> service().checkAvailability(1L, PaymentPurpose.CREATE, 200L, 5));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_PARTY_SIZE);
    }

    @Test
    void CREATE_대상_회차에_활성_예약이_있으면_409_예외가_발생한다() {
        // given
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES)).willReturn(true);

        // when
        Throwable result = catchThrowable(
                () -> service().checkAvailability(1L, PaymentPurpose.CREATE, 200L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
    }

    @Test
    void CREATE_대상_회차에_유효한_CREATE_READY_결제가_있으면_409_예외가_발생한다() {
        // given
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES)).willReturn(false);
        given(paymentHoldReader.existsActiveReadyPayment(200L, PaymentPurpose.CREATE)).willReturn(true);

        // when
        Throwable result = catchThrowable(
                () -> service().checkAvailability(1L, PaymentPurpose.CREATE, 200L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.ACTIVE_RESERVATION_ALREADY_EXISTS);
    }

    @Test
    void CREATE_대상_회차를_찾을_수_없으면_404_예외가_발생한다() {
        // given
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(
                () -> service().checkAvailability(1L, PaymentPurpose.CREATE, 200L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void partySize가_1보다_작으면_400_예외가_발생한다() {
        // when
        Throwable result = catchThrowable(
                () -> service().checkAvailability(1L, PaymentPurpose.CREATE, 200L, 0));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_PARTY_SIZE);
    }

    @Test
    void JOIN_예약_가능_여부는_잔여_인원_기준으로_확인한다() {
        // given
        Reservation reservation = reservation(10L, 200L);
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.existsByReservationIdAndMemberId(10L, 2L)).willReturn(false);
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(availableCapacityCalculator.calculate(200L, 4)).willReturn(2);

        // when
        ReservationAvailabilityResponse response = service().checkAvailability(2L, PaymentPurpose.JOIN, 10L, 2);

        // then
        assertThat(response.availableCapacity()).isEqualTo(2);
    }

    @Test
    void JOIN_대상_예약을_찾을_수_없으면_404_예외가_발생한다() {
        // given
        given(reservationRepository.findById(10L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(() -> service().checkAvailability(2L, PaymentPurpose.JOIN, 10L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void JOIN_대상_예약의_모집이_마감되면_409_예외가_발생한다() {
        // given
        Reservation reservation = reservationWithClosedRecruitment(10L, 200L);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(() -> service().checkAvailability(2L, PaymentPurpose.JOIN, 10L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_STATE);
    }

    @Test
    void JOIN_이미_참여중인_회원은_409_예외가_발생한다() {
        // given
        Reservation reservation = reservation(10L, 200L);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.existsByReservationIdAndMemberId(10L, 2L)).willReturn(true);

        // when
        Throwable result = catchThrowable(() -> service().checkAvailability(2L, PaymentPurpose.JOIN, 10L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.INVALID_STATE);
    }

    @Test
    void JOIN_partySize가_잔여_인원을_초과하면_409_예외가_발생한다() {
        // given
        Reservation reservation = reservation(10L, 200L);
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.existsByReservationIdAndMemberId(10L, 2L)).willReturn(false);
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(availableCapacityCalculator.calculate(200L, 4)).willReturn(1);

        // when
        Throwable result = catchThrowable(() -> service().checkAvailability(2L, PaymentPurpose.JOIN, 10L, 2));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.INSUFFICIENT_REMAINING_CAPACITY);
    }

    @Test
    void JOIN_결제_준비는_회차를_잠그고_기존_예약_식별자로_READY_결제를_요청한다() {
        // given
        Reservation reservation = reservation(10L, 200L);
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        Restaurant restaurant = restaurant(10L, 10000);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.existsByReservationIdAndMemberId(10L, 2L)).willReturn(false);
        given(timeSlotRepository.findWithLockByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));
        given(availableCapacityCalculator.calculate(200L, 4)).willReturn(2);
        given(readyPaymentCreator.createReadyPayment(any(CreateReadyPaymentCommand.class))).willReturn(
                new CreateReadyPaymentResult("payment-id", PaymentStatus.READY, BigDecimal.valueOf(10000),
                        Instant.parse("2026-07-25T08:10:00Z")));

        // when
        service().prepare(2L, new ReservationPrepareRequest(PaymentPurpose.JOIN, 10L, 1));

        // then
        ArgumentCaptor<CreateReadyPaymentCommand> captor = ArgumentCaptor.forClass(CreateReadyPaymentCommand.class);
        verify(readyPaymentCreator).createReadyPayment(captor.capture());
        assertThat(captor.getValue().reservationId()).isEqualTo(10L);
        assertThat(captor.getValue().purpose()).isEqualTo(PaymentPurpose.JOIN);
    }

    private TimeSlot timeSlot(Long id, Long sharedTableId) {
        TimeSlot timeSlot = TimeSlot.create(sharedTableId, Instant.parse("2026-08-01T02:00:00Z"),
                Instant.parse("2026-08-01T04:00:00Z"));
        ReflectionTestUtils.setField(timeSlot, "id", id);
        return timeSlot;
    }

    private SharedTable sharedTable(Long id, Long restaurantId, Integer capacity) {
        SharedTable sharedTable = SharedTable.create(restaurantId, capacity);
        ReflectionTestUtils.setField(sharedTable, "id", id);
        return sharedTable;
    }

    private Restaurant restaurant(Long id, Integer depositPerPerson) {
        Restaurant restaurant = Restaurant.create(
                1L, "밥풀식당", "제주시 애월읍 1", "한식", "설명", "흑돼지,혼밥", depositPerPerson);
        ReflectionTestUtils.setField(restaurant, "id", id);
        return restaurant;
    }

    private Reservation reservation(Long id, Long timeSlotId) {
        Reservation reservation = Reservation.create(timeSlotId, 1L);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private Reservation reservationWithClosedRecruitment(Long id, Long timeSlotId) {
        Reservation reservation = reservation(id, timeSlotId);
        ReflectionTestUtils.setField(reservation, "recruitmentStatus", RecruitmentStatus.CLOSED);
        return reservation;
    }
}

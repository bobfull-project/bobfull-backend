package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.event.ReservationConfirmedEvent;
import com.bobfull.reservation.event.ReservationPaymentCompletedEvent;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.reservation.port.ReservationCapacityReader;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationConfirmationServiceTest {

    private static final List<ParticipationStatus> OCCUPYING_STATUSES =
            List.of(ParticipationStatus.RESERVED, ParticipationStatus.CANCEL_REQUESTED);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    @Mock
    private ReservationCapacityReader reservationCapacityReader;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReservationConfirmationService service() {
        return new ReservationConfirmationService(
                reservationRepository, reservationParticipantRepository, reservationCapacityReader, eventPublisher);
    }

    @Test
    void CREATE는_새_예약과_최초_참여자를_생성하고_확정_기준_미달이면_RECRUITING_OPEN을_유지한다() {
        // given
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            ReflectionTestUtils.setField(reservation, "id", 10L);
            return reservation;
        });
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 20L);
            return participant;
        });
        given(reservationCapacityReader.readTableCapacity(200L)).willReturn(4);
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(2);

        // when
        ReservationConfirmationService.ReservationConfirmationResult result =
                service().confirm(PaymentPurpose.CREATE, 200L, null, 1L, 2);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.reservationParticipantId()).isEqualTo(20L);
        verify(reservationRepository).save(any(Reservation.class));
        verify(eventPublisher).publishEvent(new ReservationConfirmedEvent(10L));
        verify(eventPublisher).publishEvent(new ReservationPaymentCompletedEvent(10L, 20L, PaymentPurpose.CREATE));
    }

    @Test
    void JOIN으로_정원_4명중_3명_확정_기준에_도달하면_CONFIRMED_OPEN을_유지한다() {
        // given
        Reservation reservation = reservation(10L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 21L);
            return participant;
        });
        given(reservationCapacityReader.readTableCapacity(200L)).willReturn(4);
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(3);

        // when
        service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);

        // then
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.OPEN);
        // ChatRoom 생성용 이벤트는 CREATE 전용이라 JOIN에서는 발행되지 않지만(Issue #168 이전과 동일),
        // 결제 완료 이메일 안내용 이벤트는 CREATE·JOIN 모두에서 발행된다(Issue #168 V2).
        verify(eventPublisher, never()).publishEvent(any(ReservationConfirmedEvent.class));
        verify(eventPublisher).publishEvent(new ReservationPaymentCompletedEvent(10L, 21L, PaymentPurpose.JOIN));
    }

    @Test
    void 확정_기준에_처음_도달하면_RESERVATION_CONFIRMED_구조화로그를_남긴다() {
        // given
        Reservation reservation = reservation(10L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 21L);
            return participant;
        });
        given(reservationCapacityReader.readTableCapacity(200L)).willReturn(4);
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(3);
        Logger logger = (Logger) LoggerFactory.getLogger(ReservationConfirmationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            // when
            service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);
        } finally {
            logger.detachAppender(appender);
        }

        // then
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("event=RESERVATION_CONFIRMED");
            assertThat(event.getFormattedMessage()).contains("reservationId=10");
            assertThat(event.getFormattedMessage()).contains("participantId=21");
            assertThat(event.getFormattedMessage()).contains("memberId=2");
            assertThat(event.getFormattedMessage()).contains("beforeStatus=RECRUITING");
            assertThat(event.getFormattedMessage()).contains("afterStatus=CONFIRMED");
        });
    }

    @Test
    void JOIN으로_정원에_완전히_도달하면_CONFIRMED_CLOSED로_전이한다() {
        // given
        Reservation reservation = reservation(10L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 21L);
            return participant;
        });
        given(reservationCapacityReader.readTableCapacity(200L)).willReturn(4);
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(4);

        // when
        ReservationConfirmationService.ReservationConfirmationResult result =
                service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.reservationParticipantId()).isEqualTo(21L);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
    }

    @Test
    void 정원_2명_테이블은_확정_기준도_정원과_같아_2명이_차면_CONFIRMED_CLOSED로_전이한다() {
        // given
        Reservation reservation = reservation(10L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 22L);
            return participant;
        });
        given(reservationCapacityReader.readTableCapacity(200L)).willReturn(2);
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(2);

        // when
        service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);

        // then
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
    }

    @Test
    void JOIN_결제_완료_시점에_예약이_취소_접수_상태면_참여자_생성을_거부한다() {
        // given: 결제 완료 웹훅 도착 전에 다른 참여자가 예약 취소를 접수해 CANCELLING이 된 경쟁 조건
        Reservation reservation = reservation(10L);
        reservation.startCancelling();
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(
                () -> service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1));

        // then
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.RESERVATION_ALREADY_CANCELLED);
        verify(reservationParticipantRepository, never()).save(any(ReservationParticipant.class));
    }

    @Test
    void JOIN_결제_완료_시점에_예약이_이미_CANCELLED면_참여자_생성을_거부한다() {
        // given
        Reservation reservation = reservation(10L);
        reservation.startCancelling();
        reservation.cancel();
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(
                () -> service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1));

        // then
        assertThat(((CustomException) result).getErrorCode())
                .isEqualTo(ReservationErrorCode.RESERVATION_ALREADY_CANCELLED);
        verify(reservationParticipantRepository, never()).save(any(ReservationParticipant.class));
    }

    private Reservation reservation(Long id) {
        Reservation reservation = Reservation.create(200L, 1L);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

}

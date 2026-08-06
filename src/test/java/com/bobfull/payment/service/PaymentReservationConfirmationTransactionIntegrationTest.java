package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bobfull.payment.entity.Payment;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.payment.repository.PaymentRepository;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.reservation.service.ReservationConfirmationService;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-reservation-transaction-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=payment-reservation-transaction-test-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=3600",
        "portone.api-secret=portone-payment-reservation-test-api-secret",
        "portone.store-id=portone-payment-reservation-test-store-id",
        "portone.webhook-secret=d2hzZWNfcmVzZXJ2YXRpb24tdGVzdA=="
})
@ContextConfiguration(classes = PaymentReservationConfirmationTransactionIntegrationTest.FailureInjectionConfiguration.class)
class PaymentReservationConfirmationTransactionIntegrationTest {

    @Autowired private PaymentCompletionTransactionService paymentCompletionTransactionService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationParticipantRepository reservationParticipantRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private SharedTableRepository sharedTableRepository;
    @Autowired private FailureMode failureMode;
    @Autowired private ChatRoomRepository chatRoomRepository;

    @AfterEach
    void cleanUp() {
        failureMode.reset();
        paymentRepository.deleteAll();
        chatRoomRepository.deleteAll();
        reservationParticipantRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        sharedTableRepository.deleteAll();
    }

    @Test
    void CREATE_완료는_Payment_PAID와_Reservation_최초_Participant를_하나의_트랜잭션으로_저장한다() {
        TimeSlot timeSlot = timeSlot(4);
        Payment payment = readyCreatePayment(timeSlot, 3);

        paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId());

        Payment completed = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reservationParticipantRepository.count()).isEqualTo(1);
        assertThat(chatRoomRepository.count()).isEqualTo(1);
        assertThat(completed.getReservationId()).isNotNull();
        assertThat(completed.getReservationParticipantId()).isNotNull();
        Reservation reservation = reservationRepository.findById(completed.getReservationId()).orElseThrow();
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.OPEN);
        ReservationParticipant participant = reservationParticipantRepository
                .findById(completed.getReservationParticipantId()).orElseThrow();
        assertThat(participant.getReservationId()).isEqualTo(reservation.getId());
        assertThat(chatRoomRepository.findByReservationId(reservation.getId())).isPresent();
    }

    @Test
    void JOIN_완료는_기존_Reservation에_Participant_한_건만_추가하고_확정_기준이면_CONFIRMED_OPEN으로_전이한다() {
        TimeSlot timeSlot = timeSlot(4);
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.create(timeSlot.getId(), 10L));
        reservationParticipantRepository.saveAndFlush(ReservationParticipant.create(reservation.getId(), 10L, 2));
        Payment payment = readyJoinPayment(timeSlot, reservation, 20L, 1);

        paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId());

        assertThat(reservationParticipantRepository.count()).isEqualTo(2);
        assertThat(chatRoomRepository.count()).isZero();
        Reservation updated = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updated.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(updated.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.OPEN);
        Payment completed = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(completed.getReservationId()).isEqualTo(reservation.getId());
        assertThat(completed.getReservationParticipantId()).isNotNull();
    }

    @Test
    void JOIN으로_정원에_도달하면_CONFIRMED_CLOSED로_전이한다() {
        TimeSlot timeSlot = timeSlot(4);
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.create(timeSlot.getId(), 10L));
        reservationParticipantRepository.saveAndFlush(ReservationParticipant.create(reservation.getId(), 10L, 3));
        Payment payment = readyJoinPayment(timeSlot, reservation, 20L, 1);

        paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId());

        Reservation updated = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updated.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(updated.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
    }

    @Test
    void ChatRoom_생성_실패는_이미_커밋된_Payment_Reservation_Participant를_되돌리지_않는다() {
        TimeSlot timeSlot = timeSlot(4);
        Payment payment = readyCreatePayment(timeSlot, 3);
        failureMode.set(FailureMode.Type.CHAT_ROOM_CREATION_FAILURE);

        paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId());

        Payment completed = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(completed.getReservationId()).isNotNull();
        assertThat(completed.getReservationParticipantId()).isNotNull();
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reservationParticipantRepository.count()).isEqualTo(1);
        assertThat(chatRoomRepository.count()).isZero();
    }

    @Test
    void Reservation_저장_실패는_Payment_PAID_전이를_롤백한다() {
        TimeSlot timeSlot = timeSlot(4);
        Payment payment = readyCreatePayment(timeSlot, 1);
        failureMode.set(FailureMode.Type.RESERVATION_SAVE_FAILURE);

        assertThatThrownBy(() -> paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertPaymentAndReservationRolledBack(payment);
    }

    @Test
    void Participant_저장_실패는_Payment과_Reservation을_함께_롤백한다() {
        TimeSlot timeSlot = timeSlot(4);
        Payment payment = readyCreatePayment(timeSlot, 1);
        failureMode.set(FailureMode.Type.PARTICIPANT_SAVE_FAILURE);

        assertThatThrownBy(() -> paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertPaymentAndReservationRolledBack(payment);
    }

    @Test
    void 결과_ID_연결_실패는_Payment_Reservation_Participant를_모두_롤백한다() {
        TimeSlot timeSlot = timeSlot(4);
        Payment payment = readyCreatePayment(timeSlot, 1);
        failureMode.set(FailureMode.Type.RESULT_LINK_FAILURE);

        assertThatThrownBy(() -> paymentCompletionTransactionService.complete(payment.getPaymentId(), payment.getMemberId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertPaymentAndReservationRolledBack(payment);
    }

    @Test
    void ReservationConfirmationService는_MANDATORY이고_REQUIRES_NEW를_사용하지_않는다() throws NoSuchMethodException {
        Transactional transactional = ReservationConfirmationService.class
                .getMethod("confirm", PaymentPurpose.class, Long.class, Long.class, Long.class, Integer.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(ReservationConfirmationService.class.getAnnotation(Transactional.class)).isNull();
    }

    private void assertPaymentAndReservationRolledBack(Payment payment) {
        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(reloaded.getReservationId()).isNull();
        assertThat(reloaded.getReservationParticipantId()).isNull();
        assertThat(reservationRepository.count()).isZero();
        assertThat(reservationParticipantRepository.count()).isZero();
        // 핵심 트랜잭션이 롤백되면 AFTER_COMMIT 리스너 자체가 실행되지 않아 ChatRoom도 생성되지 않는다.
        assertThat(chatRoomRepository.count()).isZero();
    }

    private TimeSlot timeSlot(int capacity) {
        SharedTable table = sharedTableRepository.saveAndFlush(SharedTable.create(1L, capacity));
        return timeSlotRepository.saveAndFlush(TimeSlot.create(table.getId(),
                Instant.parse("2026-08-01T02:00:00Z"), Instant.parse("2026-08-01T04:00:00Z")));
    }

    private Payment readyCreatePayment(TimeSlot timeSlot, int partySize) {
        return paymentRepository.saveAndFlush(Payment.createReady(paymentId(), 10L, timeSlot.getId(), null,
                PaymentPurpose.CREATE, partySize, BigDecimal.valueOf(10000), Instant.parse("2026-09-01T00:00:00Z")));
    }

    private Payment readyJoinPayment(TimeSlot timeSlot, Reservation reservation, Long memberId, int partySize) {
        return paymentRepository.saveAndFlush(Payment.createReady(paymentId(), memberId, timeSlot.getId(), reservation.getId(),
                PaymentPurpose.JOIN, partySize, BigDecimal.valueOf(10000), Instant.parse("2026-09-01T00:00:00Z")));
    }

    private String paymentId() {
        return "payment-" + UUID.randomUUID();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {
        @Bean
        FailureMode failureMode() {
            return new FailureMode();
        }

        @Bean
        @Primary
        com.bobfull.chat.service.ChatRoomCreationService failureInjectingChatRoomCreationService(
                ChatRoomRepository chatRoomRepository, FailureMode failureMode
        ) {
            return new com.bobfull.chat.service.ChatRoomCreationService(chatRoomRepository) {
                @Override
                public com.bobfull.chat.entity.ChatRoom createIfAbsent(Long reservationId) {
                    if (failureMode.type == FailureMode.Type.CHAT_ROOM_CREATION_FAILURE) {
                        throw new IllegalStateException("강제 ChatRoom 생성 실패(테스트)");
                    }
                    return super.createIfAbsent(reservationId);
                }
            };
        }

        @Bean
        @Primary
        ReservationConfirmationPort failureInjectingReservationConfirmationPort(
                ReservationConfirmationService service,
                ReservationRepository reservationRepository,
                ReservationParticipantRepository reservationParticipantRepository,
                FailureMode failureMode
        ) {
            return payment -> {
                if (failureMode.type == FailureMode.Type.RESERVATION_SAVE_FAILURE) {
                    reservationRepository.saveAndFlush(Reservation.create(null, payment.getMemberId()));
                }
                ReservationConfirmationService.ReservationConfirmationResult result = service.confirm(
                        payment.getPurpose(), payment.getTimeSlotId(), payment.getReservationId(),
                        payment.getMemberId(), payment.getPartySize());
                if (failureMode.type == FailureMode.Type.PARTICIPANT_SAVE_FAILURE) {
                    reservationParticipantRepository.saveAndFlush(
                            ReservationParticipant.create(null, payment.getMemberId(), payment.getPartySize()));
                }
                if (failureMode.type == FailureMode.Type.RESULT_LINK_FAILURE) {
                    return new ReservationConfirmationPort.ReservationConfirmationResult(null, null);
                }
                return new ReservationConfirmationPort.ReservationConfirmationResult(
                        result.reservationId(), result.reservationParticipantId());
            };
        }
    }

    static class FailureMode {
        enum Type { NONE, RESERVATION_SAVE_FAILURE, PARTICIPANT_SAVE_FAILURE, RESULT_LINK_FAILURE, CHAT_ROOM_CREATION_FAILURE }
        private Type type = Type.NONE;
        void set(Type type) { this.type = type; }
        void reset() { this.type = Type.NONE; }
    }
}

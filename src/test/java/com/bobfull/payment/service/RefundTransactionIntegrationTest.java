package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bobfull.payment.adapter.ReservationCancellationRefundAdapter;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import com.bobfull.payment.port.PortOneRefundRequester;
import com.bobfull.payment.repository.PaymentRepository;
import com.bobfull.payment.repository.RefundRepository;
import com.bobfull.common.exception.CustomException;
import com.bobfull.reservation.port.ReservationCancellationRefundPort.RefundRequestCommand;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:refund-transaction-test;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "jwt.secret=refund-transaction-test-secret-key-please-keep-long", "jwt.access-token-expiration-seconds=3600", "portone.api-secret=test", "portone.store-id=test", "portone.webhook-secret=dGVzdA=="})
@ContextConfiguration(classes = RefundTransactionIntegrationTest.Config.class)
class RefundTransactionIntegrationTest {
    @Autowired private OuterRollbackProbe outerRollbackProbe;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private ReservationCancellationRefundAdapter adapter;
    @Autowired private SequencedRequester requester;
    @Autowired private RefundTransactionService transactionService;
    @Autowired private RefundCompletionService refundCompletionService;
    @Autowired private RefundWebhookService refundWebhookService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationParticipantRepository reservationParticipantRepository;
    private Reservation activeReservation;
    private final Map<Long, Long> participantIds = new ConcurrentHashMap<>();

    @AfterEach void clean() { requester.reset(); refundRepository.deleteAll(); paymentRepository.deleteAll(); reservationParticipantRepository.deleteAll(); reservationRepository.deleteAll(); activeReservation = null; participantIds.clear(); }

    @Test
    void 앞선_외부_환불_성공은_뒤_실패와_예약트랜잭션_롤백_후에도_보존된다() {
        Payment first = paid(1L); Payment second = paid(2L);
        assertThatThrownBy(() -> outerRollbackProbe.cancel(activeReservation.getId(), List.of(participantIds.get(1L), participantIds.get(2L)))).isInstanceOf(RuntimeException.class);
        assertThat(refundRepository.findByPayment_Id(first.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refundRepository.findByPayment_Id(second.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(paymentRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void 동일_Payment_동시_환불은_Refund_한건과_외부호출_한번으로_수렴한다() throws Exception {
        Payment payment = paid(1L);
        requester.blockFirstCall();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> adapter.requestRefunds(new RefundRequestCommand(activeReservation.getId(), List.of(participantIds.get(1L)), 1L, "test")));
            assertThat(requester.firstCallEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> adapter.requestRefunds(new RefundRequestCommand(activeReservation.getId(), List.of(participantIds.get(1L)), 1L, "test")));
            requester.releaseFirstCall.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
        } finally { executor.shutdownNow(); }
        assertThat(refundRepository.count()).isEqualTo(1);
        assertThat(requester.calls()).isEqualTo(1);
        assertThat(refundRepository.findByPayment_Id(payment.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void 완료된_Refund_중복요청은_외부환불_재호출없이_기존완료결과를_반환한다() {
        Payment payment = paid(1L);
        RefundRequestCommand command = new RefundRequestCommand(activeReservation.getId(), List.of(participantIds.get(1L)), 1L, "test");

        adapter.requestRefunds(command);
        var repeated = adapter.requestRefunds(command);

        assertThat(repeated).singleElement().extracting(result -> result.refundStatus()).isEqualTo(RefundStatus.COMPLETED.name());
        assertThat(refundRepository.count()).isEqualTo(1);
        assertThat(requester.calls()).isEqualTo(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void 타임아웃은_실패로_확정하지_않고_자동_재호출하지_않는다() {
        Payment payment = paid(1L); requester.timeoutNextCall();
        assertThatThrownBy(() -> adapter.requestRefunds(new RefundRequestCommand(activeReservation.getId(), List.of(participantIds.get(1L)), 1L, "test")))
                .isInstanceOf(CustomException.class);
        assertThat(refundRepository.findByPayment_Id(payment.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(requester.calls()).isEqualTo(1);
    }

    @Test
    void 결과불명확_통신오류는_실패로_확정하지_않고_자동_재호출하지_않는다() {
        Payment payment = paid(1L); requester.connectionResetNextCall();
        assertThatThrownBy(() -> adapter.requestRefunds(new RefundRequestCommand(activeReservation.getId(), List.of(participantIds.get(1L)), 1L, "test")))
                .isInstanceOf(CustomException.class);
        assertThat(refundRepository.findByPayment_Id(payment.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(requester.calls()).isEqualTo(1);
    }

    @Test
    void Cancelled_웹훅_완료는_Refund_Payment_Participant_Reservation까지_반영한다() {
        Payment payment = paid(1L);
        var refund = transactionService.createRequested(activeReservation.getId(), participantIds.get(1L)).refund();
        refundCompletionService.reflectExternalResult(refund.getId(), "cancel-webhook", false);
        refundWebhookService.complete(payment.getPaymentId(), "cancel-webhook");
        assertThat(refundRepository.findByPayment_Id(payment.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ReservationParticipant participant = reservationParticipantRepository.findById(participantIds.get(1L)).orElseThrow();
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(participant.getCancelledAt()).isNotNull();
        assertThat(reservationRepository.findById(activeReservation.getId()).orElseThrow().getReservationStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void 즉시응답_완료후_같은_Cancelled_웹훅도_예약완료를_멱등처리한다() {
        Payment payment = paid(1L);
        adapter.requestRefunds(new RefundRequestCommand(activeReservation.getId(), List.of(participantIds.get(1L)), 1L, "test"));
        refundWebhookService.complete(payment.getPaymentId(), "cancel-" + payment.getPaymentId());
        ReservationParticipant participant = reservationParticipantRepository.findById(participantIds.get(1L)).orElseThrow();
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(participant.getCancelledAt()).isNotNull();
        assertThat(reservationRepository.findById(activeReservation.getId()).orElseThrow().getReservationStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void 웹훅과_즉시응답_동시완료도_Participant를_한번만_완료한다() throws Exception {
        Payment payment = paid(1L);
        var refund = transactionService.createRequested(activeReservation.getId(), participantIds.get(1L)).refund();
        refundCompletionService.reflectExternalResult(refund.getId(), "cancel-race", false);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var immediate = executor.submit(() -> refundCompletionService.reflectExternalResult(refund.getId(), "cancel-race", true));
            var webhook = executor.submit(() -> refundWebhookService.complete(payment.getPaymentId(), "cancel-race"));
            immediate.get(5, TimeUnit.SECONDS);
            webhook.get(5, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        ReservationParticipant participant = reservationParticipantRepository.findById(participantIds.get(1L)).orElseThrow();
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(participant.getCancelledAt()).isNotNull();
        assertThat(reservationRepository.findById(activeReservation.getId()).orElseThrow().getReservationStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void 다른_CANCEL_REQUESTED가_남으면_웹훅_완료후에도_CANCELLING을_유지한다() {
        Payment first = paid(1L);
        paid(2L);
        var refund = transactionService.createRequested(activeReservation.getId(), participantIds.get(1L)).refund();
        refundCompletionService.reflectExternalResult(refund.getId(), "cancel-first", false);
        refundWebhookService.complete(first.getPaymentId(), "cancel-first");
        assertThat(reservationParticipantRepository.findById(participantIds.get(1L)).orElseThrow().getParticipationStatus())
                .isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(reservationParticipantRepository.findById(participantIds.get(2L)).orElseThrow().getParticipationStatus())
                .isEqualTo(ParticipationStatus.CANCEL_REQUESTED);
        assertThat(reservationRepository.findById(activeReservation.getId()).orElseThrow().getReservationStatus())
                .isEqualTo(ReservationStatus.CANCELLING);
    }

    @Test
    void 존재하지않는_cancellationId_웹훅은_안전하게_무시한다() {
        org.assertj.core.api.Assertions.assertThatCode(
                () -> refundWebhookService.complete("missing-payment", "missing-cancellation"))
                .doesNotThrowAnyException();
    }

    @Test
    void 예약완료_반영에_실패하면_Refund와_Payment_완료상태도_함께_롤백한다() {
        Payment payment = Payment.createReady("rollback-" + UUID.randomUUID(), 1L, 1L, 999L,
                PaymentPurpose.JOIN, 1, BigDecimal.valueOf(1000), Instant.parse("2026-12-01T00:00:00Z"));
        payment.complete(Instant.parse("2026-08-01T00:00:00Z"));
        payment.attachReservationConfirmation(999L, 888L);
        payment = paymentRepository.saveAndFlush(payment);
        Refund refund = transactionService.createRequested(999L, 888L).refund();

        assertThatThrownBy(() -> refundCompletionService.reflectExternalResult(refund.getId(), "cancel-rollback", true))
                .isInstanceOf(CustomException.class);

        assertThat(refundRepository.findById(refund.getId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    private Payment paid(Long participantId) {
        if (activeReservation == null) {
            activeReservation = reservationRepository.saveAndFlush(Reservation.create(1L, 1L));
            activeReservation.startCancelling();
            activeReservation = reservationRepository.saveAndFlush(activeReservation);
        }
        ReservationParticipant participant = ReservationParticipant.create(activeReservation.getId(), participantId, 1);
        participant.requestCancel("test");
        participant = reservationParticipantRepository.saveAndFlush(participant);
        participantIds.put(participantId, participant.getId());
        Payment payment = Payment.createReady("refund-" + UUID.randomUUID(), participantId, 1L, activeReservation.getId(), PaymentPurpose.JOIN, 1, BigDecimal.valueOf(1000), Instant.parse("2026-12-01T00:00:00Z"));
        payment.complete(Instant.parse("2026-08-01T00:00:00Z")); payment.attachReservationConfirmation(activeReservation.getId(), participant.getId());
        return paymentRepository.saveAndFlush(payment);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean @Primary SequencedRequester requester() { return new SequencedRequester(); }
        @Bean OuterRollbackProbe outerRollbackProbe(ReservationCancellationRefundAdapter adapter) { return new OuterRollbackProbe(adapter); }
    }
    static class SequencedRequester implements PortOneRefundRequester {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile CountDownLatch firstCallEntered;
        private volatile CountDownLatch releaseFirstCall;
        private volatile boolean timeoutNext;
        private volatile boolean connectionResetNext;
        public RefundResult request(String paymentId, BigDecimal amount, String reason) {
            int call = calls.incrementAndGet();
            if (timeoutNext) { timeoutNext = false; throw new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException("timeout")); }
            if (connectionResetNext) { connectionResetNext = false; throw new java.util.concurrent.CompletionException(new java.io.IOException("connection reset")); }
            if (call == 2) throw new ExplicitRefundFailureException("PortOne explicitly rejected the refund");
            CountDownLatch entered = firstCallEntered; if (entered != null) { entered.countDown(); try { releaseFirstCall.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
            return new RefundResult("cancel-" + paymentId, true);
        }
        public boolean isCancellationCompleted(String paymentId, String cancellationId) { return true; }
        void blockFirstCall() { firstCallEntered = new CountDownLatch(1); releaseFirstCall = new CountDownLatch(1); }
        int calls() { return calls.get(); }
        void timeoutNextCall() { timeoutNext = true; }
        void connectionResetNextCall() { connectionResetNext = true; }
        void reset() { calls.set(0); firstCallEntered = null; releaseFirstCall = null; timeoutNext = false; connectionResetNext = false; }
    }
    static class OuterRollbackProbe {
        private final ReservationCancellationRefundAdapter adapter;
        OuterRollbackProbe(ReservationCancellationRefundAdapter adapter) { this.adapter = adapter; }
        @Transactional public void cancel(Long reservationId, List<Long> ids) { adapter.requestRefunds(new RefundRequestCommand(reservationId, ids, 1L, "test")); }
    }
}

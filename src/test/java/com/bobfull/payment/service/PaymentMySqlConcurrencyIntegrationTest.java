package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.port.PortOnePaymentReader;
import com.bobfull.payment.repository.PaymentRepository;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/** MySQL 행 잠금 검증용 선택적 통합 테스트: BOBFULL_MYSQL_CONCURRENCY_TEST=true 일 때만 실행한다. */
@EnabledIfEnvironmentVariable(named = "BOBFULL_MYSQL_CONCURRENCY_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${BOBFULL_TEST_MYSQL_URL}",
        "spring.datasource.username=${BOBFULL_TEST_MYSQL_USERNAME}",
        "spring.datasource.password=${BOBFULL_TEST_MYSQL_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "payment.expiration.enabled=false",
        "jwt.secret=payment-mysql-concurrency-test-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=3600",
        "portone.api-secret=portone-payment-mysql-concurrency-test-api-secret",
        "portone.store-id=portone-payment-mysql-concurrency-test-store-id",
        "portone.webhook-secret=d2hzZWNfbXlzcWwtY29uY3VycmVuY3ktdGVzdA=="
})
@ContextConfiguration(classes = PaymentMySqlConcurrencyIntegrationTest.ConcurrencyConfiguration.class)
class PaymentMySqlConcurrencyIntegrationTest {

    @Autowired private PaymentCompletionService completionService;
    @Autowired private PaymentCompletionTransactionService completionTransactionService;
    @Autowired private PaymentExpirationProcessor expirationProcessor;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationParticipantRepository participantRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private SharedTableRepository sharedTableRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ControlledPortOnePaymentReader paymentReader;
    @Autowired private MutableClock mutableClock;

    @AfterEach
    void cleanUp() {
        paymentReader.reset();
        mutableClock.set(Instant.parse("2030-08-31T23:59:00Z"));
        paymentRepository.deleteAll();
        participantRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        sharedTableRepository.deleteAll();
    }

    @Test
    void 완료_처리가_먼저_행_락을_획득하면_PAID와_예약_확정만_커밋되고_만료_Processor는_변경하지_않는다() throws Exception {
        Payment payment = readyPayment();
        mutableClock.set(Instant.parse("2030-08-31T23:59:00Z"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                paymentRepository.findWithLockByPaymentId(payment.getPaymentId()).orElseThrow();
                completionTransactionService.complete(payment.getPaymentId(), payment.getMemberId());
                mutableClock.set(Instant.parse("2030-09-01T00:00:00Z"));
                CountDownLatch expirationStarted = new CountDownLatch(1);
                Future<?> expiration = executor.submit(() -> {
                    expirationStarted.countDown();
                    expirationProcessor.expire(payment.getId());
                });
                await(expirationStarted);
                assertThat(expiration.isDone()).isFalse();
            });
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertPaidWithSingleReservation(payment);
    }

    @Test
    void 만료_Processor가_먼저_행_락을_획득하면_EXPIRED만_커밋되고_완료_처리는_PAYMENT_EXPIRED로_거절된다() throws Exception {
        Payment payment = readyPayment();
        mutableClock.set(Instant.parse("2030-09-01T00:00:00Z"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Future<Throwable>> completion = new AtomicReference<>();
            transactionTemplate.executeWithoutResult(status -> {
                paymentRepository.findWithLockById(payment.getId()).orElseThrow();
                expirationProcessor.expire(payment.getId());
                CountDownLatch completionStarted = new CountDownLatch(1);
                completion.set(executor.submit(() -> {
                    completionStarted.countDown();
                    return capture(() ->
                            completionService.complete(payment.getPaymentId(), payment.getMemberId()));
                }));
                await(completionStarted);
            });
            Throwable thrown = completion.get().get(10, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOf(CustomException.class);
            assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_EXPIRED);
        } finally {
            executor.shutdownNow();
        }

        Payment expired = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reservationRepository.count()).isZero();
        assertThat(participantRepository.count()).isZero();
    }

    @Test
    void PortOne_조회_사이에_만료되면_락_획득_후_재검증되어_예약_확정이_차단된다() throws Exception {
        Payment payment = readyPayment();
        mutableClock.set(Instant.parse("2030-08-31T23:59:00Z"));
        paymentReader.blockNextRead();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> completion = executor.submit(() -> capture(() ->
                    completionService.complete(payment.getPaymentId(), payment.getMemberId())));
            paymentReader.awaitReadStartedOrThrow();
            mutableClock.set(Instant.parse("2030-09-01T00:00:00Z"));
            expirationProcessor.expire(payment.getId());
            paymentReader.releaseRead();

            Throwable thrown = completion.get(10, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOf(CustomException.class);
        } finally {
            executor.shutdownNow();
        }

        Payment expired = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reservationRepository.count()).isZero();
        assertThat(participantRepository.count()).isZero();
    }

    private void assertPaidWithSingleReservation(Payment payment) {
        Payment paid = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(participantRepository.count()).isEqualTo(1);
        assertThat(paid.getReservationId()).isNotNull();
        assertThat(paid.getReservationParticipantId()).isNotNull();
    }

    private Payment readyPayment() {
        SharedTable table = sharedTableRepository.saveAndFlush(SharedTable.create(1L, 4));
        TimeSlot timeSlot = timeSlotRepository.saveAndFlush(TimeSlot.create(table.getId(),
                Instant.parse("2026-08-01T02:00:00Z"), Instant.parse("2026-08-01T04:00:00Z")));
        return paymentRepository.saveAndFlush(Payment.createReady("payment-" + UUID.randomUUID(), 10L, timeSlot.getId(), null,
                PaymentPurpose.CREATE, 1, BigDecimal.valueOf(10000), Instant.parse("2030-09-01T00:00:00Z")));
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 제어 중 인터럽트되었습니다.", exception);
        }
    }

    private Throwable capture(ThrowingRunnable runnable) {
        return org.assertj.core.api.Assertions.catchThrowable(runnable::run);
    }

    @FunctionalInterface
    interface ThrowingRunnable { void run(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyConfiguration {
        @Bean
        @Primary
        ControlledPortOnePaymentReader controlledPortOnePaymentReader() {
            return new ControlledPortOnePaymentReader();
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2030-08-31T23:59:00Z"));
        }
    }

    static class ControlledPortOnePaymentReader implements PortOnePaymentReader {
        private volatile CountDownLatch readStarted;
        private volatile CountDownLatch readReleased;

        @Override
        public PortOnePayment read(String paymentId) {
            CountDownLatch started = readStarted;
            CountDownLatch released = readReleased;
            if (started != null && released != null) {
                started.countDown();
                try {
                    if (!released.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("PortOne 조회 제어 대기 시간이 초과되었습니다.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("PortOne 조회 제어 중 인터럽트되었습니다.", exception);
                }
            }
            return new PortOnePayment(paymentId, true, BigDecimal.valueOf(10000), Payment.CURRENCY_KRW);
        }

        void blockNextRead() {
            readStarted = new CountDownLatch(1);
            readReleased = new CountDownLatch(1);
        }

        void awaitReadStartedOrThrow() {
            try {
                if (!readStarted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("PortOne 조회 시작 대기 시간이 초과되었습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("PortOne 조회 시작 대기 중 인터럽트되었습니다.", exception);
            }
        }

        void releaseRead() { readReleased.countDown(); }

        void reset() {
            readStarted = null;
            readReleased = null;
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

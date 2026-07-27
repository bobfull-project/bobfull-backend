package com.bobfull.paymenttemp.service;

import com.bobfull.paymenttemp.dto.CreateReadyPaymentCommand;
import com.bobfull.paymenttemp.entity.Payment;
import com.bobfull.paymenttemp.repository.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * READY 상태의 결제·10분 임시 좌석 선점을 생성하는 최소 구현이다(#91의 생성 인터페이스 최소본).
 * 예약 도메인은 이 인터페이스만 호출하고 Payment 엔티티를 직접 생성·조작하지 않는다(#35 Q1).
 * 결제 완료 검증·웹훅·PortOne 연동을 포함한 정식 Payment 도메인 구현은 #91이 담당한다.
 */
@Service
public class PaymentService {

    private static final String CURRENCY_KRW = "KRW";
    private static final Duration READY_EXPIRATION = Duration.ofMinutes(10);

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Transactional
    public Payment createReadyPayment(CreateReadyPaymentCommand command) {
        Instant now = clock.instant();
        Payment payment = Payment.createReady(
                generatePaymentId(now),
                command.memberId(),
                command.timeSlotId(),
                command.reservationId(),
                command.paymentPurpose(),
                command.partySize(),
                command.amount(),
                CURRENCY_KRW,
                now.plus(READY_EXPIRATION)
        );
        return paymentRepository.save(payment);
    }

    // paymentId 생성 알고리즘은 문서에 확정되어 있지 않다. 순번 대신 UUID로 동시성 문제 없이
    // 고유성을 보장하며, 정식 규칙은 #91에서 다시 결정될 수 있다.
    private String generatePaymentId(Instant now) {
        String datePart = now.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "PAY-" + datePart + "-" + randomPart;
    }
}

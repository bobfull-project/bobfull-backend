package com.bobfull.reservation.port;

import java.time.Instant;

/**
 * 예약 결제 준비가 Payment 도메인(#91)에 요구하는 최소 계약이다.
 * 예약 도메인은 이 인터페이스만 호출하고 Payment 엔티티·상태·영속화를 직접 다루지 않는다(#35 Q1).
 * 실제 구현체(엔티티, 만료 정책, paymentId 생성 규칙 포함)는 #91이 구현하며,
 * 이 인터페이스의 구현체는 #91 병합 후 연결한다.
 */
public interface PaymentReadyCreator {

    /** 회차당 만료되지 않은 CREATE READY 결제는 최대 1건이다(ERD 4.7, ADR 0001). */
    boolean existsValidCreateReady(Long timeSlotId, Instant now);

    /** 같은 회원의 동일 예약에 대한 중복 JOIN 결제 준비를 막기 위한 확인이다. */
    boolean existsValidJoinReady(Long reservationId, Long memberId, Instant now);

    /** 해당 예약에 대해 만료되지 않은 READY 결제의 partySize 합계(임시 선점 인원)다. */
    int sumHeldPartySize(Long reservationId, Instant now);

    ReadyPaymentResult createReadyPayment(CreateReadyPaymentCommand command);
}

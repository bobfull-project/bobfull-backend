package com.bobfull.payment.service;

import com.bobfull.payment.entity.PaymentPurpose;

/**
 * 예약 도메인이 Payment 내부 구현에 의존하지 않고 좌석 임시 선점 현황을 조회하는 계약이다.
 */
public interface PaymentHoldReader {

    /**
     * 대상 TimeSlot에 만료되지 않은 READY Payment가 특정 목적으로 존재하는지 확인한다.
     */
    boolean existsActiveReadyPayment(Long timeSlotId, PaymentPurpose purpose);

    /**
     * 대상 TimeSlot의 만료되지 않은 READY Payment partySize 합계를 반환한다.
     */
    int sumActiveReadyPartySize(Long timeSlotId);

    /**
     * 같은 회원이 같은 예약에 대해 만료되지 않은 JOIN READY Payment를 이미 갖고 있는지 확인한다.
     * 참여자(ReservationParticipant)는 결제 완료 전에는 생성되지 않으므로, 결제 준비 단계의
     * 중복 요청은 참여자 존재 여부가 아니라 이 조회로 막아야 한다.
     */
    boolean existsActiveJoinReadyPayment(Long reservationId, Long memberId);
}

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
}

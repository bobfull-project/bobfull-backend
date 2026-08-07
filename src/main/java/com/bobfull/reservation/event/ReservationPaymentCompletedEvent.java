package com.bobfull.reservation.event;

import com.bobfull.payment.entity.PaymentPurpose;

/**
 * 결제 완료 트랜잭션 안에서 예약이 새로 생성(CREATE)되거나 참여자가 추가(JOIN)됐음을 알리는
 * 이벤트다(Issue #168 V2). 참여자 본인에게 접수·참여 완료 이메일을 안내하는 용도로만 쓰며,
 * {@link ReservationConfirmedEvent}(ChatRoom 생성용, CREATE 전용)와는 목적이 다르다 — 이 이벤트는
 * CREATE·JOIN 모두에서 발행되고, 확정 기준 충족 여부와 무관하게 결제 완료 자체를 알린다.
 * 이메일 발송처럼 결제 확정의 필수 조건이 아닌 후속 처리는 AFTER_COMMIT에서 구독해야 한다.
 */
public record ReservationPaymentCompletedEvent(Long reservationId, Long reservationParticipantId, PaymentPurpose purpose) {
}

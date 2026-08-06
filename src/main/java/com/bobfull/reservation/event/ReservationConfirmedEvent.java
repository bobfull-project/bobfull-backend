package com.bobfull.reservation.event;

/**
 * 최초(CREATE) 예약이 결제 확정 트랜잭션 안에서 생성됐음을 알리는 이벤트다. ChatRoom 생성처럼
 * 결제·예약 확정의 필수 조건이 아닌 후속 처리는 이 이벤트를 AFTER_COMMIT에서 구독해 별도
 * 트랜잭션으로 수행해야 한다(#50 PR #174 리뷰, ChatRoom 저장 실패가 결제 확정을 롤백시키던 BLOCKER).
 */
public record ReservationConfirmedEvent(Long reservationId) {
}

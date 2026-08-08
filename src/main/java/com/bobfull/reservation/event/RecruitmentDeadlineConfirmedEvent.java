package com.bobfull.reservation.event;

/**
 * 모집 마감 처리 시점에 확정 기준을 충족한 예약을 알리는 이벤트다(Issue #47, #168 V2).
 * {@code ReservationCancellationTransactionService#acceptRecruitmentDeadline}의 트랜잭션이
 * 실제로 커밋된 뒤에만 이 이벤트의 리스너가 실행돼야 하므로 AFTER_COMMIT에서 구독한다.
 */
public record RecruitmentDeadlineConfirmedEvent(Long reservationId) {
}

package com.bobfull.reservation.event;

import java.util.List;

/**
 * 모집 마감 처리 시점에 확정 기준 미달로 취소 접수된 예약을 알리는 이벤트다(Issue #47, #168 V2).
 * {@code ReservationCancellationTransactionService#acceptRecruitmentDeadline}의 트랜잭션이
 * 실제로 커밋된 뒤에만 이 이벤트의 리스너가 실행돼야 하므로 AFTER_COMMIT에서 구독한다.
 * 환불 요청({@code ReservationCancellationRefundPort})은 이 이벤트와 별개로 호출되며, 환불
 * 실패가 이 이벤트 처리(이메일 안내) 자체를 막지 않는다.
 */
public record RecruitmentDeadlineCancelledEvent(Long reservationId, List<Long> reservationParticipantIds) {
}

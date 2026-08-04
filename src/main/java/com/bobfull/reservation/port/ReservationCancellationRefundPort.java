package com.bobfull.reservation.port;

import java.util.List;

/**
 * 예약 취소 시 결제 도메인에 환불을 요청하는 outbound port다(Issue #131, ADR 0005).
 * 예약 도메인은 Payment·Refund Repository나 Entity를 직접 참조하지 않고 이 계약으로만
 * 환불 기능을 호출하며, 환불 금액·Payment 조회·Refund 생성·PortOne 요청은 결제 도메인이 결정한다.
 * 실제 Adapter 구현은 #45(결제 환불 실행 및 Refund 상태 관리) 또는 #44 통합 단계에서
 * 결제 패키지에 작성한다 — 이 Issue는 계약만 정의하고 구현하지 않는다.
 */
public interface ReservationCancellationRefundPort {

    /**
     * 취소 대상 참여자들의 Payment 전체 금액에 대한 환불을 요청한다. 실패하거나 이미 환불이
     * 존재하는 경우 결제 도메인이 정의하는 예외를 던져야 하며, 이 경우 호출자의 트랜잭션이
     * 롤백되어 예약·참여 상태 전이가 커밋되지 않아야 한다(완료 조건: 환불 실패 시 상태 미확정).
     *
     * <p><b>락 순서 제약(ADR 0001)</b>: 호출자({@code ReservationCancellationService})는 이미
     * Reservation을 잠근 트랜잭션 안에서 이 메서드를 호출한다. 결제 완료 흐름은 Payment → Reservation
     * 순서로 락을 잡으므로, 이 메서드의 실제 구현(Adapter)이 같은 트랜잭션 안에서 Payment 행에
     * 비관적 락을 걸면 Reservation → Payment 역순이 되어 결제 완료 흐름과 교착(deadlock) 위험이
     * 생긴다. 구현체는 Payment에 비관적 락을 걸지 않아야 하며, 환불 중복 방지는 낙관적 락이나
     * {@code Refund.payment_id} UNIQUE 제약, 또는 별도 트랜잭션 분리로 처리한다.</p>
     */
    List<RefundRequestResult> requestRefunds(RefundRequestCommand command);

    /**
     * @param reservationId              환불 대상이 속한 예약
     * @param reservationParticipantIds  환불 대상 참여자 식별자 목록(추가 참여자 취소는 1건,
     *                                    최초 예약자 취소는 유효 참여자 전체)
     * @param requesterMemberId          취소를 요청한 인증 회원
     * @param cancelReason               취소 사유(각 참여자 Payment에 동일하게 적용)
     */
    record RefundRequestCommand(
            Long reservationId,
            List<Long> reservationParticipantIds,
            Long requesterMemberId,
            String cancelReason
    ) {
    }

    /**
     * @param reservationParticipantId 환불이 요청된 참여자
     * @param refundStatus             결제 도메인이 정의하는 환불 요청 상태 문자열(예: "REQUESTED")
     */
    record RefundRequestResult(Long reservationParticipantId, String refundStatus) {
    }
}

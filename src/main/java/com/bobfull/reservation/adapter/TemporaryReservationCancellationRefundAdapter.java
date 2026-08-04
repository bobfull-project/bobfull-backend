package com.bobfull.reservation.adapter;

import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link ReservationCancellationRefundPort}의 실제 Adapter는 #45(결제 환불 실행 및 Refund 상태
 * 관리)가 결제 패키지에 작성한다(Issue #131 도메인 경계·충돌 방지 계약). 이 클래스는 그 전까지
 * 애플리케이션 컨텍스트가 정상 기동하도록 두는 임시 Placeholder이며, Payment·Refund
 * Entity·Repository를 참조하지 않고 실제 환불을 영속화·요청하지 않는다.
 *
 * <p><b>주의</b>: #45 또는 #44 통합 단계에서 결제 패키지의 실제 구현으로 교체되면 이 클래스는
 * 삭제해야 한다.</p>
 */
@Component
public class TemporaryReservationCancellationRefundAdapter implements ReservationCancellationRefundPort {

    @Override
    public List<RefundRequestResult> requestRefunds(RefundRequestCommand command) {
        return command.reservationParticipantIds().stream()
                .map(reservationParticipantId -> new RefundRequestResult(reservationParticipantId, "REQUESTED"))
                .toList();
    }
}

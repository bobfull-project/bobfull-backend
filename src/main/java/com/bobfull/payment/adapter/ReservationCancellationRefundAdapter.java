package com.bobfull.payment.adapter;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.port.PortOneRefundRequester;
import com.bobfull.payment.service.RefundCompletionService;
import com.bobfull.payment.service.RefundTransactionService;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReservationCancellationRefundAdapter implements ReservationCancellationRefundPort {
    private static final Logger log = LoggerFactory.getLogger(ReservationCancellationRefundAdapter.class);
    private final RefundTransactionService transactionService;
    private final RefundCompletionService completionService;
    private final PortOneRefundRequester refundRequester;

    public ReservationCancellationRefundAdapter(RefundTransactionService transactionService,
            RefundCompletionService completionService, PortOneRefundRequester refundRequester) {
        this.transactionService = transactionService;
        this.completionService = completionService;
        this.refundRequester = refundRequester;
    }

    @Override
    public List<RefundRequestResult> requestRefunds(RefundRequestCommand command) {
        return command.reservationParticipantIds().stream().distinct().map(participantId -> request(command, participantId)).toList();
    }

    private RefundRequestResult request(RefundRequestCommand command, Long participantId) {
        Refund refund = transactionService.createRequested(command.reservationId(), participantId);
        try {
            var result = refundRequester.request(refund.getPayment().getPaymentId(), refund.getAmount(), command.cancelReason());
            var completion = completionService.reflectExternalResult(refund.getId(), result.cancellationId(), result.completed());
            return new RefundRequestResult(participantId, completion.refundStatus().name());
        } catch (RuntimeException exception) {
            if (!(exception instanceof PortOneRefundRequester.ExplicitRefundFailureException)) {
                log.error("event=REFUND_RESULT_UNKNOWN paymentId={} refundId={} externalStatus=UNKNOWN internalStatus=REQUESTED autoRetry=false",
                        refund.getPayment().getId(), refund.getId());
                throw new CustomException(PaymentErrorCode.PORTONE_REFUND_FAILED);
            }
            transactionService.markFailed(refund.getId());
            log.error("event=REFUND_FAILED paymentId={} refundId={} externalStatus=FAILED internalStatus=FAILED autoRetry=false",
                    refund.getPayment().getId(), refund.getId());
            throw new CustomException(PaymentErrorCode.PORTONE_REFUND_FAILED);
        }
    }

}

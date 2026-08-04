package com.bobfull.payment.adapter;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.port.PortOneRefundRequester;
import com.bobfull.payment.service.RefundTransactionService;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.service.ReservationCancellationService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

@Component
public class ReservationCancellationRefundAdapter implements ReservationCancellationRefundPort {
    private static final Logger log = LoggerFactory.getLogger(ReservationCancellationRefundAdapter.class);
    private final RefundTransactionService transactionService;
    private final PortOneRefundRequester refundRequester;
    private final ObjectProvider<ReservationCancellationService> cancellationServiceProvider;

    public ReservationCancellationRefundAdapter(RefundTransactionService transactionService, PortOneRefundRequester refundRequester,
            ObjectProvider<ReservationCancellationService> cancellationServiceProvider) {
        this.transactionService = transactionService;
        this.refundRequester = refundRequester;
        this.cancellationServiceProvider = cancellationServiceProvider;
    }

    @Override
    public List<RefundRequestResult> requestRefunds(RefundRequestCommand command) {
        return command.reservationParticipantIds().stream().distinct().map(participantId -> request(command, participantId)).toList();
    }

    private RefundRequestResult request(RefundRequestCommand command, Long participantId) {
        Refund refund = transactionService.createRequested(command.reservationId(), participantId);
        try {
            var result = refundRequester.request(refund.getPayment().getPaymentId(), refund.getAmount(), command.cancelReason());
            var status = transactionService.reflectExternalResult(refund.getId(), result.cancellationId(), result.completed());
            if (status == com.bobfull.payment.entity.RefundStatus.COMPLETED) {
                cancellationServiceProvider.getObject().completeParticipantCancellation(
                        command.reservationId(), participantId, Instant.now());
            }
            return new RefundRequestResult(participantId, status.name());
        } catch (RuntimeException exception) {
            if (isResultUnknown(exception)) {
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

    private boolean isResultUnknown(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }
}

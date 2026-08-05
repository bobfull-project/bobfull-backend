package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.port.PortOneRefundRequester;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundReconciliationProcessorTest {

    @Mock private PortOneRefundRequester refundRequester;
    @Mock private RefundTransactionService transactionService;
    @Mock private RefundCompletionService completionService;
    @Mock private Refund refund;
    @Mock private Payment payment;

    private RefundReconciliationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new RefundReconciliationProcessor(refundRequester, transactionService, completionService);
        given(refund.getId()).willReturn(1L);
        given(refund.getPayment()).willReturn(payment);
        given(payment.getPaymentId()).willReturn("payment-1");
        given(payment.getAmount()).willReturn(BigDecimal.TEN);
        given(refund.getAmount()).willReturn(BigDecimal.TEN);
        given(refund.getRequestedAt()).willReturn(Instant.parse("2026-08-05T00:00:00Z"));
    }

    @Test
    void COMPLETED이면_완료_경로를_호출하고_PG_조회_시각을_기록한다() {
        given(refund.getCancellationId()).willReturn(null);
        given(refundRequester.reconcile("payment-1", null, BigDecimal.TEN, Instant.parse("2026-08-05T00:00:00Z")))
                .willReturn(PortOneRefundRequester.ReconciliationResult.completed("cancel-1",
                        Instant.parse("2026-08-05T00:01:00Z")));

        var result = processor.reconcile(refund);

        assertThat(result.status()).isEqualTo(PortOneRefundRequester.ReconciliationStatus.COMPLETED);
        verify(completionService).reflectExternalResult(1L, "cancel-1", true);
        verify(transactionService).markPgChecked(1L);
    }

    @Test
    void PROCESSING이고_cancellationId가_있으면_완료_경로를_미완료로_호출한다() {
        given(refund.getCancellationId()).willReturn("cancel-1");
        given(refundRequester.reconcile("payment-1", "cancel-1", BigDecimal.TEN, Instant.parse("2026-08-05T00:00:00Z")))
                .willReturn(PortOneRefundRequester.ReconciliationResult.processing("cancel-1"));

        processor.reconcile(refund);

        verify(completionService).reflectExternalResult(1L, "cancel-1", false);
        verify(transactionService).markPgChecked(1L);
    }

    @Test
    void NOT_COMPLETED이면_완료_경로를_호출하지_않는다() {
        given(refund.getCancellationId()).willReturn(null);
        given(refundRequester.reconcile(eq("payment-1"), eq((String) null), eq(BigDecimal.TEN),
                eq(Instant.parse("2026-08-05T00:00:00Z"))))
                .willReturn(PortOneRefundRequester.ReconciliationResult.notCompleted());

        processor.reconcile(refund);

        verify(completionService, never()).reflectExternalResult(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(transactionService).markPgChecked(1L);
    }

    @Test
    void AMBIGUOUS이면_완료_경로를_호출하지_않지만_PG_조회_시각은_기록한다() {
        given(refund.getCancellationId()).willReturn(null);
        given(refundRequester.reconcile(eq("payment-1"), eq((String) null), eq(BigDecimal.TEN),
                eq(Instant.parse("2026-08-05T00:00:00Z"))))
                .willReturn(PortOneRefundRequester.ReconciliationResult.ambiguous("multiple or mixed cancellations"));

        processor.reconcile(refund);

        verify(completionService, never()).reflectExternalResult(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(transactionService).markPgChecked(1L);
    }

    @Test
    void 조회_자체가_실패해도_PG_조회_시각은_기록하고_예외는_전파한다() {
        given(refund.getCancellationId()).willReturn(null);
        given(refundRequester.reconcile(eq("payment-1"), eq((String) null), eq(BigDecimal.TEN),
                eq(Instant.parse("2026-08-05T00:00:00Z"))))
                .willThrow(new IllegalStateException("PortOne timeout"));

        assertThatThrownBy(() -> processor.reconcile(refund))
                .isInstanceOf(RefundReconciliationProcessor.RefundLookupException.class);

        verify(transactionService).markPgChecked(1L);
        verify(completionService, never()).reflectExternalResult(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}

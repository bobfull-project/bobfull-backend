package com.bobfull.payment.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.portone.sdk.server.payment.PaymentCancellation;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PortOneSdkRefundRequesterTest {

    @Test
    void 취소접수응답은_PROCESSING으로_해석한다() {
        PaymentCancellation.Recognized cancellation = cancellation("cancel-1", null);

        var result = PortOneSdkRefundRequester.toRefundResult(cancellation);

        assertThat(result.cancellationId()).isEqualTo("cancel-1");
        assertThat(result.completed()).isFalse();
        assertThat(PortOneSdkRefundRequester.isCompletedCancellation(cancellation, "cancel-1")).isFalse();
    }

    @Test
    void cancelledAt이_있는_취소만_완료로_해석한다() {
        PaymentCancellation.Recognized cancellation = cancellation("cancel-1", Instant.parse("2026-08-04T00:00:00Z"));

        var result = PortOneSdkRefundRequester.toRefundResult(cancellation);

        assertThat(result.completed()).isTrue();
        assertThat(PortOneSdkRefundRequester.isCompletedCancellation(cancellation, "cancel-1")).isTrue();
        assertThat(PortOneSdkRefundRequester.isCompletedCancellation(cancellation, "other")).isFalse();
    }

    private PaymentCancellation.Recognized cancellation(String id, Instant cancelledAt) {
        PaymentCancellation.Recognized cancellation = Mockito.mock(PaymentCancellation.Recognized.class);
        when(cancellation.getId()).thenReturn(id);
        when(cancellation.getCancelledAt()).thenReturn(cancelledAt);
        return cancellation;
    }
}

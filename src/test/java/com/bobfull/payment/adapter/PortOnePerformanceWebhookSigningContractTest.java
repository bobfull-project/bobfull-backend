package com.bobfull.payment.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransactionCancelledCancelled;
import io.portone.sdk.server.webhook.WebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Issue #146 K6 Harness가 {@code /api/webhooks/portone}으로 직접 서명해 보낼
 * {@code Transaction.Cancelled} 페이로드 형식이, 실제 PortOne 서버 SDK({@link WebhookVerifier})가
 * 요구하는 서명·JSON 계약과 정확히 일치하는지 확인한다. K6는 JavaScript로 같은 서명 절차를
 * 재현하므로(k6/common/portoneWebhook.js), 이 테스트가 그 절차의 정답 기준이 된다.
 *
 * <p>{@code application-performance.yml}의 {@code portone.webhook-secret} 값은 "whsec_"로
 * 시작하지 않으므로, {@link WebhookVerifier#WebhookVerifier(String)}는 그 값 전체를 그대로
 * base64 디코드해 HMAC 키로 쓴다(SDK jar 역어셈블로 확인) — "whsec_" 접두어 분기를 타지 않는다.</p>
 */
class PortOnePerformanceWebhookSigningContractTest {

    private static final String WEBHOOK_SECRET_CONFIG = "d2hzZWNfcmVzZXJ2YXRpb24tc2VhdC1jb25jdXJyZW5jeQ==";

    @Test
    void K6가_생성할_서명과_동일한_방식으로_서명하면_실제_SDK_검증을_통과한다() throws Exception {
        String body = """
                {"type":"Transaction.Cancelled","timestamp":"2026-08-13T03:00:00.000Z",\
                "data":{"paymentId":"perf-payment-1","storeId":"performance-test-store",\
                "transactionId":"perf-tx-perf-payment-1","cancellationId":"perf-cancel-perf-payment-1"}}""";
        String id = "cancelled-1-0-1";
        // WebhookVerifier는 webhook-timestamp를 현재 시각 ±300초로 검증한다(SDK jar 역어셈블로 확인).
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        byte[] keyBytes = Base64.getDecoder().decode(WEBHOOK_SECRET_CONFIG);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        String signedContent = id + "." + timestamp + "." + body;
        String signatureB64 = Base64.getEncoder().encodeToString(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        String signatureHeader = "v1," + signatureB64;

        Webhook webhook = new WebhookVerifier(WEBHOOK_SECRET_CONFIG).verify(body, id, signatureHeader, timestamp);

        assertThat(webhook).isInstanceOf(WebhookTransactionCancelledCancelled.class);
        WebhookTransactionCancelledCancelled cancelled = (WebhookTransactionCancelledCancelled) webhook;
        assertThat(cancelled.getData().getPaymentId()).isEqualTo("perf-payment-1");
        assertThat(cancelled.getData().getCancellationId()).isEqualTo("perf-cancel-perf-payment-1");
    }
}

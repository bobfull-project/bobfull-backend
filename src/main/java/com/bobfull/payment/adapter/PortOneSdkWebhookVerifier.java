package com.bobfull.payment.adapter;
import com.bobfull.payment.port.PortOneWebhookVerifier;
import io.portone.sdk.server.webhook.*;
import org.springframework.stereotype.Component;
@Component public class PortOneSdkWebhookVerifier implements PortOneWebhookVerifier {
 private final WebhookVerifier verifier; public PortOneSdkWebhookVerifier(WebhookVerifier verifier){this.verifier=verifier;}
 public WebhookEvent verify(String body,String id,String sig,String ts) throws io.portone.sdk.server.errors.WebhookVerificationException { Webhook w=verifier.verify(body,id,sig,ts); return w instanceof WebhookTransactionPaid p ? new WebhookEvent(p.getData().getPaymentId()) : new WebhookEvent(null); }
}

package com.bobfull.payment.controller;

import com.bobfull.common.response.ApiResponse;
import com.bobfull.common.security.AuthMember;
import com.bobfull.payment.dto.PaymentCompletionResponse;
import com.bobfull.payment.service.PaymentCompletionService;
import com.bobfull.payment.service.PaymentCompletionTransactionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentCompletionService paymentCompletionService;
    public PaymentController(PaymentCompletionService paymentCompletionService) { this.paymentCompletionService = paymentCompletionService; }
    @PostMapping("/{paymentId}/complete")
    public ApiResponse<PaymentCompletionResponse> complete(@AuthenticationPrincipal AuthMember authMember, @PathVariable String paymentId) {
        PaymentCompletionTransactionService.PaymentCompletionResult result =
                paymentCompletionService.complete(paymentId, authMember.id());
        return ApiResponse.success(PaymentCompletionResponse.from(result.payment(), result.reservationId(), result.participationId()));
    }
}

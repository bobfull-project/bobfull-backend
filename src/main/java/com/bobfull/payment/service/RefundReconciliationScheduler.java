package com.bobfull.payment.service;

import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import com.bobfull.payment.port.PortOneRefundRequester;
import com.bobfull.payment.repository.RefundRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래 멈춘 환불을 조회 전용으로 재확인하며, 한 건 실패가 다음 후보를 막지 않게 한다. */
@Component
@ConditionalOnProperty(prefix = "payment.refund-reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(RefundReconciliationScheduler.class);
    private static final Duration LONG_RUNNING_WARN = Duration.ofMinutes(30);
    private static final Duration LONG_RUNNING_ERROR = Duration.ofMinutes(60);
    private static final Duration ALERT_WINDOW = Duration.ofMinutes(10);

    private final RefundRepository refundRepository;
    private final RefundReconciliationProcessor processor;
    private final Clock clock;
    private final int batchSize;
    private final Duration minimumAge;
    private final Duration recheckDelay;
    private final Map<Long, Instant> longRunningRefunds = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lookupFailures = new ConcurrentHashMap<>();

    public RefundReconciliationScheduler(RefundRepository refundRepository, RefundReconciliationProcessor processor,
                                         Clock clock, @Value("${payment.refund-reconciliation.batch-size:20}") int batchSize,
                                         @Value("${payment.refund-reconciliation.minimum-age:10m}") Duration minimumAge,
                                         @Value("${payment.refund-reconciliation.recheck-delay:5m}") Duration recheckDelay) {
        this.refundRepository = refundRepository;
        this.processor = processor;
        this.clock = clock;
        this.batchSize = batchSize;
        this.minimumAge = minimumAge;
        this.recheckDelay = recheckDelay;
    }

    @Scheduled(fixedDelayString = "${payment.refund-reconciliation.fixed-delay:5m}")
    public void reconcileStalledRefunds() {
        Instant now = clock.instant();
        refundRepository.findReconciliationCandidates(List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING),
                now.minus(minimumAge), now.minus(recheckDelay), PageRequest.of(0, batchSize))
                .forEach(refund -> reconcileOne(refund, now));
    }

    private void reconcileOne(Refund refund, Instant now) {
        logLongRunningRefund(refund, now);
        try {
            PortOneRefundRequester.ReconciliationResult result = processor.reconcile(refund);
            if (result.status() == PortOneRefundRequester.ReconciliationStatus.AMBIGUOUS) {
                log.warn("event=REFUND_MATCH_AMBIGUOUS refundId={} paymentId={} reason={}",
                        refund.getId(), refund.getPayment().getPaymentId(), result.detail());
            }
        } catch (RefundReconciliationProcessor.RefundLookupException exception) {
            lookupFailures.put(refund.getId(), now);
            log.error("event=REFUND_LOOKUP_FAILED refundId={} paymentId={} reason={}", refund.getId(),
                    refund.getPayment().getPaymentId(), exception.toString(), exception);
            logMultipleFailures(now);
        } catch (RuntimeException exception) {
            log.error("event=REFUND_RECONCILIATION_REQUIRED level=ERROR refundId={} paymentId={} reason={}",
                    refund.getId(), refund.getPayment().getPaymentId(), exception.toString(), exception);
        }
    }

    private void logLongRunningRefund(Refund refund, Instant now) {
        Duration age = Duration.between(refund.getUpdatedAt(), now);
        if (age.compareTo(LONG_RUNNING_WARN) < 0) return;
        longRunningRefunds.put(refund.getId(), now);
        String level = age.compareTo(LONG_RUNNING_ERROR) >= 0 ? "ERROR" : "WARN";
        if ("ERROR".equals(level)) {
            log.error("event=REFUND_RECONCILIATION_REQUIRED level={} refundId={} paymentId={} status={} requestedAt={} updatedAt={} lastPgCheckedAt={} hasCancellationId={}",
                    level, refund.getId(), refund.getPayment().getPaymentId(), refund.getStatus(), refund.getRequestedAt(),
                    refund.getUpdatedAt(), refund.getLastPgCheckedAt(), refund.getCancellationId() != null);
        } else {
            log.warn("event=REFUND_RECONCILIATION_REQUIRED level={} refundId={} paymentId={} status={} requestedAt={} updatedAt={} lastPgCheckedAt={} hasCancellationId={}",
                    level, refund.getId(), refund.getPayment().getPaymentId(), refund.getStatus(), refund.getRequestedAt(),
                    refund.getUpdatedAt(), refund.getLastPgCheckedAt(), refund.getCancellationId() != null);
        }
        pruneAndLogMultiple(longRunningRefunds, now, "long_running_refunds");
    }

    private void logMultipleFailures(Instant now) { pruneAndLogMultiple(lookupFailures, now, "lookup_failures"); }

    private void pruneAndLogMultiple(Map<Long, Instant> events, Instant now, String kind) {
        events.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minus(ALERT_WINDOW)));
        if (events.size() >= 3) {
            log.error("event=REFUND_RECONCILIATION_REQUIRED level=ERROR kind={} distinctRefundCount={} windowMinutes=10",
                    kind, events.size());
        }
    }
}

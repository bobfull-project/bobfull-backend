package com.bobfull.common.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 운영 대시보드용 비즈니스 Counter를 기록한다.
 * 메트릭 기록 실패가 핵심 트랜잭션 흐름에 영향을 주지 않도록 내부에서 예외를 삼킨다.
 */
@Component
public class BusinessMetricRecorder {

    public static final String METRIC_NAME = "bobfull_business_events";

    private static final Logger log = LoggerFactory.getLogger(BusinessMetricRecorder.class);

    private final MeterRegistry meterRegistry;

    public BusinessMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void increment(BusinessMetricEvent event) {
        try {
            Counter.builder(METRIC_NAME)
                    .description("BobFull business event occurrences")
                    .tag("event", event.name())
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException exception) {
            log.warn("businessMetricRecordFailed event={} reason={}", event.name(),
                    exception.getClass().getSimpleName());
        }
    }
}

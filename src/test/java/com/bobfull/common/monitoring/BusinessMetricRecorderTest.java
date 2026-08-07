package com.bobfull.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class BusinessMetricRecorderTest {

    @Test
    void 비즈니스_이벤트를_event_라벨_Counter로_증가시킨다() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BusinessMetricRecorder recorder = new BusinessMetricRecorder(meterRegistry);

        // when
        recorder.increment(BusinessMetricEvent.LOGIN_FAILED);
        recorder.increment(BusinessMetricEvent.LOGIN_FAILED);

        // then
        assertThat(meterRegistry.get(BusinessMetricRecorder.METRIC_NAME)
                .tag("event", "LOGIN_FAILED")
                .counter()
                .count()).isEqualTo(2.0);
    }

    @Test
    void 메트릭_기록_실패는_비즈니스_예외로_전파하지_않는다() {
        // given
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        given(meterRegistry.config()).willThrow(new IllegalStateException("registry failure"));
        BusinessMetricRecorder recorder = new BusinessMetricRecorder(meterRegistry);

        // when & then
        assertThatCode(() -> recorder.increment(BusinessMetricEvent.PAYMENT_COMPENSATION_REQUIRED))
                .doesNotThrowAnyException();
    }
}

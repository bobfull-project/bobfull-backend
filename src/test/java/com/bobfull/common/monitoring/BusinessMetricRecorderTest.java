package com.bobfull.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class BusinessMetricRecorderTest {

    @Test
    void 생성시_모든_비즈니스_이벤트_Counter를_0으로_등록한다() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // when
        new BusinessMetricRecorder(meterRegistry);

        // then
        for (BusinessMetricEvent event : BusinessMetricEvent.values()) {
            assertThat(counter(meterRegistry, event).count()).isZero();
        }
    }

    @Test
    void 비즈니스_이벤트를_event_라벨_Counter로_증가시킨다() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BusinessMetricRecorder recorder = new BusinessMetricRecorder(meterRegistry);

        // when
        recorder.increment(BusinessMetricEvent.LOGIN_FAILED);
        recorder.increment(BusinessMetricEvent.LOGIN_FAILED);

        // then
        assertThat(counter(meterRegistry, BusinessMetricEvent.LOGIN_FAILED).count()).isEqualTo(2.0);
    }

    @Test
    void 특정_이벤트를_기록하면_해당_Counter만_1_증가한다() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BusinessMetricRecorder recorder = new BusinessMetricRecorder(meterRegistry);

        // when
        recorder.increment(BusinessMetricEvent.PAYMENT_COMPENSATION_REQUIRED);

        // then
        assertThat(counter(meterRegistry, BusinessMetricEvent.PAYMENT_COMPENSATION_REQUIRED).count())
                .isEqualTo(1.0);
        for (BusinessMetricEvent event : BusinessMetricEvent.values()) {
            if (event != BusinessMetricEvent.PAYMENT_COMPENSATION_REQUIRED) {
                assertThat(counter(meterRegistry, event).count()).isZero();
            }
        }
    }

    @Test
    void 비즈니스_이벤트_Counter는_기존_metric_name과_event_tag만_사용한다() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // when
        new BusinessMetricRecorder(meterRegistry);

        // then
        Counter counter = counter(meterRegistry, BusinessMetricEvent.PAYMENT_COMPENSATION_REQUIRED);
        assertThat(counter.getId().getName()).isEqualTo(BusinessMetricRecorder.METRIC_NAME);
        assertThat(counter.getId().getTags()).hasSize(1);
        assertThat(counter.getId().getTag("event")).isEqualTo("PAYMENT_COMPENSATION_REQUIRED");
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

    private Counter counter(SimpleMeterRegistry meterRegistry, BusinessMetricEvent event) {
        return meterRegistry.get(BusinessMetricRecorder.METRIC_NAME)
                .tag("event", event.name())
                .counter();
    }
}

package com.bobfull.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 리뷰 지적(MAJOR 1) 재검증: Kafka ACK 대기가 느려도 dispatch(...) 호출 자체(=채팅 send 경로)는
 * 즉시 반환돼야 한다. processor.signal(...)의 실제 지연은 별도 스레드에서만 발생해야 한다.
 */
class ChatMessageOutboxSignalDispatcherTest {

    @Test void dispatch는_processor_signal이_느려도_즉시_반환한다() throws InterruptedException {
        // given
        ChatMessageOutboxProcessor processor = mock(ChatMessageOutboxProcessor.class);
        CountDownLatch signalStarted = new CountDownLatch(1);
        CountDownLatch releaseSignal = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            signalStarted.countDown();
            releaseSignal.await(5, TimeUnit.SECONDS);
            return null;
        }).when(processor).signal(1L);
        ChatMessageOutboxSignalDispatcher dispatcher = new ChatMessageOutboxSignalDispatcher(processor);

        // when
        long startedAt = System.nanoTime();
        dispatcher.dispatch(1L);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        // then: dispatch 호출 자체는 즉시 반환(=채팅 send 경로가 Kafka ACK 대기로 막히지 않음)
        assertThat(elapsedMillis).isLessThan(500);
        assertThat(signalStarted.await(2, TimeUnit.SECONDS)).isTrue();
        releaseSignal.countDown();
        verify(processor, timeout(2000)).signal(1L);
    }

    @Test void processor_signal이_예외를_던져도_dispatch_호출자에게는_전파되지_않는다() {
        // given
        ChatMessageOutboxProcessor processor = mock(ChatMessageOutboxProcessor.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("강제 실패(테스트)")).when(processor).signal(2L);
        ChatMessageOutboxSignalDispatcher dispatcher = new ChatMessageOutboxSignalDispatcher(processor);

        // when / then
        org.assertj.core.api.Assertions.assertThatCode(() -> dispatcher.dispatch(2L)).doesNotThrowAnyException();
        verify(processor, timeout(2000)).signal(2L);
    }
}

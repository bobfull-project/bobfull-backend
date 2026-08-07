package com.bobfull.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * emailTaskExecutor의 거부 정책이 호출 스레드에서 작업을 대신 실행하지 않고(= CallerRunsPolicy를
 * 쓰지 않고) 버리며 로그만 남기는지 확인한다(Issue #168 V2 독립 리뷰 MAJOR 반영). 실제 스레드
 * 풀을 포화시키는 타이밍에 의존하지 않도록, 거부 정책 자체를 직접 호출해 검증한다.
 */
class NotificationAsyncConfigTest {

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(NotificationAsyncConfig.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(NotificationAsyncConfig.class)).detachAppender(logAppender);
    }

    @Test
    void 거부된_작업을_호출_스레드에서_대신_실행하지_않고_버리며_로그를_남긴다() {
        NotificationAsyncConfig config = new NotificationAsyncConfig();
        RejectedExecutionHandler handler = config.discardAndLogPolicy();
        ThreadPoolTaskExecutor executor = config.emailTaskExecutor();
        AtomicBoolean taskRan = new AtomicBoolean(false);

        handler.rejectedExecution(() -> taskRan.set(true), executor.getThreadPoolExecutor());

        assertThat(taskRan).isFalse();
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("RESERVATION_NOTIFICATION_TASK_REJECTED"));

        executor.shutdown();
    }

    @Test
    void 기본_설정은_CallerRunsPolicy가_아니다() {
        NotificationAsyncConfig config = new NotificationAsyncConfig();
        ThreadPoolTaskExecutor executor = config.emailTaskExecutor();

        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isNotInstanceOf(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy.class);

        executor.shutdown();
    }
}

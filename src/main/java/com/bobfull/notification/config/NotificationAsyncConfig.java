package com.bobfull.notification.config;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이메일 발송 후속 처리(AFTER_COMMIT 이벤트 리스너)를 별도 스레드 풀에서 실행한다(Issue #168 V2).
 * {@code @TransactionalEventListener(AFTER_COMMIT)}는 "커밋 이후 호출"만 보장할 뿐 비동기를
 * 의미하지 않으므로, SMTP 통신 지연이 호출 스레드(HTTP 요청 또는 스케줄러 실행)를 막지 않도록
 * 이 전용 Executor 기반 {@code @Async}를 함께 사용한다.
 *
 * <p>큐(200)와 최대 스레드(8)가 모두 소진되면 {@code CallerRunsPolicy}는 호출 스레드에서 작업을
 * 그대로 실행해 이 클래스의 목적(호출 스레드 비차단)을 정면으로 어기게 된다 — 그래서 대신 작업을
 * 버리고 로그만 남기는 거부 정책을 쓴다. 이메일은 이미 최선형 재시도(참여자별 최대 3회)이고 실패해도
 * 예약·결제 상태에 영향이 없으므로, 극단적인 부하 상황에서는 호출 스레드를 지키는 쪽을 우선한다.</p>
 */
@Configuration
@EnableAsync
public class NotificationAsyncConfig {
    private static final Logger log = LoggerFactory.getLogger(NotificationAsyncConfig.class);

    public static final String EMAIL_TASK_EXECUTOR = "emailTaskExecutor";

    @Bean(EMAIL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-notify-");
        executor.setRejectedExecutionHandler(discardAndLogPolicy());
        executor.initialize();
        return executor;
    }

    // 테스트에서 직접 검증할 수 있도록 default(package-private) 접근으로 둔다.
    RejectedExecutionHandler discardAndLogPolicy() {
        return (runnable, executor) -> log.error(
                "event=RESERVATION_NOTIFICATION_TASK_REJECTED reason=EMAIL_TASK_EXECUTOR_SATURATED "
                        + "activeCount={} queueSize={}",
                executor.getActiveCount(), executor.getQueue().size());
    }
}

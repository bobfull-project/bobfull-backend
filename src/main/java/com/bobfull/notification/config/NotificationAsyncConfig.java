package com.bobfull.notification.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이메일 발송 후속 처리(AFTER_COMMIT 이벤트 리스너)를 별도 스레드 풀에서 실행한다(Issue #168 V2).
 * {@code @TransactionalEventListener(AFTER_COMMIT)}는 "커밋 이후 호출"만 보장할 뿐 비동기를
 * 의미하지 않으므로, SMTP 통신 지연이 호출 스레드(HTTP 요청 또는 스케줄러 실행)를 막지 않도록
 * 이 전용 Executor 기반 {@code @Async}를 함께 사용한다.
 */
@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    public static final String EMAIL_TASK_EXECUTOR = "emailTaskExecutor";

    @Bean(EMAIL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

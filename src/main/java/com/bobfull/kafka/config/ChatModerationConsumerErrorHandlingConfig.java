package com.bobfull.kafka.config;

import com.bobfull.common.exception.CustomException;
import com.bobfull.kafka.consumer.ChatModerationDltRecoverer;
import com.bobfull.kafka.exception.InvalidChatMessageEventException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Q1 결정(최초 처리 포함 최대 3회)을 강제한다. 메시지 없음/버전 불일치처럼 재시도로 해결되지 않는
 * 예외는 즉시 DLT로 보내 불필요한 반복 호출을 만들지 않는다.
 */
@Configuration
public class ChatModerationConsumerErrorHandlingConfig {

    @Bean
    public CommonErrorHandler chatModerationErrorHandler(ChatModerationDltRecoverer recoverer,
            @Value("${bobfull.kafka.chat-message.consumer-max-attempts:3}") int maxAttempts,
            @Value("${bobfull.kafka.chat-message.consumer-retry-backoff-ms:1000}") long retryBackoffMs
    ) {
        long retriesAfterFirstAttempt = Math.max(0, maxAttempts - 1);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(retryBackoffMs, retriesAfterFirstAttempt));
        errorHandler.addNotRetryableExceptions(CustomException.class, InvalidChatMessageEventException.class);
        return errorHandler;
    }
}

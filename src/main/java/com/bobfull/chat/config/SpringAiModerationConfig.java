package com.bobfull.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAI 전용 객체는 Adapter에만 주입되도록 ChatClient를 구성한다. */
@Configuration
public class SpringAiModerationConfig {
    @Bean
    ChatClient moderationChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}

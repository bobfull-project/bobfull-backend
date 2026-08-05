package com.bobfull.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각 조회를 위한 Clock을 주입한다.
 * DB 저장 기준이 UTC이므로 UTC 기준 Clock을 사용한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

package com.bobfull.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class JpaAuditingConfigTest {

    @Test
    void 고정된_Clock을_주입하면_DateTimeProvider가_해당_시각을_반환한다() {
        // given
        Instant fixedInstant = Instant.parse("2026-07-23T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        JpaAuditingConfig config = new JpaAuditingConfig();

        // when
        DateTimeProvider provider = config.auditingDateTimeProvider(fixedClock);

        // then
        assertThat(provider.getNow()).contains(fixedInstant);
    }
}

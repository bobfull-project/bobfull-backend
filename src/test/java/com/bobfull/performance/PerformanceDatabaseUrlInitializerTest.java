package com.bobfull.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class PerformanceDatabaseUrlInitializerTest {

    @Test
    void 전용_스키마_URL은_허용한다() {
        assertThatCode(() -> PerformanceDatabaseUrlInitializer.validate(
                "jdbc:mysql://localhost:3307/bobfull_perf_121"))
                .doesNotThrowAnyException();
    }

    @Test
    void query_parameter가_있는_전용_스키마_URL은_허용한다() {
        assertThatCode(() -> PerformanceDatabaseUrlInitializer.validate(
                "jdbc:mysql://localhost:3307/bobfull_perf_121?useSSL=false"))
                .doesNotThrowAnyException();
    }

    @Test
    void 개발_DB와_동시성_DB와_임의_DB는_초기화_전에_거절한다() {
        assertInvalid("jdbc:mysql://localhost:3307/bobfull");
        assertInvalid("jdbc:mysql://localhost:3307/bobfull_concurrency_test");
        assertInvalid("jdbc:mysql://localhost:3307/other");
    }

    @Test
    void 빈값과_잘못된_JDBC_URL은_초기화_전에_거절한다() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("not-a-jdbc-url");
    }

    @Test
    void 초기화_단계에서_잘못된_URL을_컨텍스트_refresh_전에_거절한다() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "performance-test", Map.of("BOBFULL_PERF_DB_URL", "jdbc:mysql://localhost:3307/bobfull")));

            assertThatThrownBy(() -> new PerformanceDatabaseUrlInitializer().initialize(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("performance 전용 DB bobfull_perf_121 URL을 사용해야 합니다.");
            assertThat(context.isActive()).isFalse();
        }
    }

    private void assertInvalid(String jdbcUrl) {
        assertThatThrownBy(() -> PerformanceDatabaseUrlInitializer.validate(jdbcUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("performance 전용 DB bobfull_perf_121 URL을 사용해야 합니다.");
    }
}

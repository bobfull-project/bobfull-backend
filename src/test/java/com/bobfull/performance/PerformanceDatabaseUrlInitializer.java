package com.bobfull.performance;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * create-drop 성능 테스트가 전용 스키마 이외의 DB에 연결되기 전에 차단한다.
 */
public class PerformanceDatabaseUrlInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String PERFORMANCE_DATABASE = "bobfull_perf_121";
    private static final String URL_PROPERTY = "BOBFULL_PERF_DB_URL";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        validate(applicationContext.getEnvironment().getProperty(URL_PROPERTY));
    }

    static void validate(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw invalidDatabaseUrl();
        }

        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            if (!jdbcUrl.startsWith("jdbc:mysql:")
                    || !"mysql".equals(uri.getScheme())
                    || !('/' + PERFORMANCE_DATABASE).equals(uri.getPath())) {
                throw invalidDatabaseUrl();
            }
        } catch (StringIndexOutOfBoundsException | URISyntaxException exception) {
            throw invalidDatabaseUrl();
        }
    }

    private static IllegalStateException invalidDatabaseUrl() {
        return new IllegalStateException("performance 전용 DB bobfull_perf_121 URL을 사용해야 합니다.");
    }
}

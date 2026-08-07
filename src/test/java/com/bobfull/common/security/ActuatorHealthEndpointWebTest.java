package com.bobfull.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:actuator-health-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.health.redis.enabled=false",
        // 테스트 환경에는 실제로 붙는 SMTP 서버가 없어(Issue #168), mail Health Indicator가
        // 전체 상태를 DOWN으로 끌어내리지 않도록 Redis와 동일하게 비활성화한다.
        "management.health.mail.enabled=false",
        "jwt.secret=actuator-health-test-secret-key-please-keep-this-long",
        "jwt.access-token-expiration-seconds=3600",
        "portone.api-secret=portone-actuator-health-test-api-secret",
        "portone.store-id=portone-actuator-health-test-store-id",
        "portone.webhook-secret=d2hzZWNfYWN0dWF0b3ItdGVzdA==",
        "aws.region=ap-northeast-2",
        "aws.s3.image-bucket=bobfull-test-image-bucket"
})
class ActuatorHealthEndpointWebTest {

    @LocalServerPort
    private int port;

    @Test
    void actuator_health는_인증없이_200을_반환한다() throws Exception {
        // when
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
}

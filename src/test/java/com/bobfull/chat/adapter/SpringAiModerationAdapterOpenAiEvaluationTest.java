package com.bobfull.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bobfull.chat.dto.AiModerationResponse;
import com.bobfull.chat.entity.ModerationResultType;
import com.bobfull.chat.port.AiModerationPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

/** 실제 OpenAI 단건 연결 검증이다. 일반 build에서는 API Key가 없으면 실행하지 않는다. */
@Tag("openai-evaluation")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("local")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openai-evaluation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=openai-evaluation-only-secret-key-with-minimum-length",
        "portone.api-secret=test-api-secret",
        "portone.store-id=test-store-id",
        "portone.webhook-secret=d2hzZWNfZEdWemRDMXpkR055WlhRPQ==",
        "spring.mail.host=localhost",
        "spring.mail.port=1025"
})
class SpringAiModerationAdapterOpenAiEvaluationTest {
    @Autowired private AiModerationPort aiModerationPort;

    @DynamicPropertySource
    static void openAiApiKey(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.api-key", () -> System.getenv("OPENAI_API_KEY"));
    }

    @Test
    void local_환경변수로_바인딩한_OpenAI에_SAFE_단건_분석을_요청한다() {
        // when
        AiModerationResponse response = aiModerationPort.analyze("내일 7시에 식당에서 봐요.");

        // then
        assertThat(response.provider()).isEqualTo("OpenAI");
        assertThat(response.model()).isNotBlank();
        assertThat(response.result().result()).isEqualTo(ModerationResultType.SAFE);
    }
}

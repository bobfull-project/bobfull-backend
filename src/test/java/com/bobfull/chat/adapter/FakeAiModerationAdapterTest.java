package com.bobfull.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bobfull.chat.dto.AiModerationResponse;
import com.bobfull.chat.entity.ModerationResultType;
import com.bobfull.chat.entity.RiskLevel;
import org.junit.jupiter.api.Test;

class FakeAiModerationAdapterTest {

    @Test
    void 지연시간이_0이면_즉시_SAFE_결과를_반환한다() {
        FakeAiModerationAdapter adapter = new FakeAiModerationAdapter(0L, ModerationResultType.SAFE);

        AiModerationResponse response = adapter.analyze("안녕하세요");

        assertThat(response.result().result()).isEqualTo(ModerationResultType.SAFE);
        assertThat(response.result().riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.provider()).isEqualTo("Fake");
    }

    @Test
    void FLAGGED로_설정하면_HIGH_위험도를_반환한다() {
        FakeAiModerationAdapter adapter = new FakeAiModerationAdapter(0L, ModerationResultType.FLAGGED);

        AiModerationResponse response = adapter.analyze("금지어");

        assertThat(response.result().result()).isEqualTo(ModerationResultType.FLAGGED);
        assertThat(response.result().riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void 설정한_지연시간만큼_실제로_대기한다() {
        long latencyMs = 100L;
        FakeAiModerationAdapter adapter = new FakeAiModerationAdapter(latencyMs, ModerationResultType.SAFE);

        long start = System.nanoTime();
        adapter.analyze("지연 확인");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(latencyMs);
    }
}

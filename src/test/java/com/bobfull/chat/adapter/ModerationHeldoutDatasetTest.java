package com.bobfull.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bobfull.chat.adapter.SpringAiModerationHeldoutEvaluationTest.HeldoutCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Issue #213 Held-out/Challenge Dataset의 구조적 불변식만 검증한다. 실제 Provider 호출 없이,
 * API Key 유무와 무관하게 일반 build에서 항상 실행된다(#66의 {@link SpringAiModerationEvaluationDatasetTest}와
 * 같은 역할). 라벨의 정답 여부(Human 확정)는 이 테스트의 범위가 아니다 — 개수·분포·중복만 확인한다.
 */
class ModerationHeldoutDatasetTest {

    @Test
    void Held_out_Set은_카테고리별_20건씩_총_80건이다() {
        List<HeldoutCase> cases = SpringAiModerationHeldoutEvaluationTest.heldoutCases();
        assertThat(cases).hasSize(80);
        assertThat(cases.stream().map(HeldoutCase::id).distinct()).hasSize(80);
        for (String prefix : List.of("HOLDOUT-SAFE-", "HOLDOUT-PROFANITY-", "HOLDOUT-PI-", "HOLDOUT-SPAM-")) {
            assertThat(cases.stream().filter(c -> c.id().startsWith(prefix)).count())
                    .as("prefix=%s", prefix)
                    .isEqualTo(20);
        }
    }

    @Test
    void Challenge_Set은_20건에서_30건_사이다() {
        List<HeldoutCase> cases = SpringAiModerationHeldoutEvaluationTest.challengeCases();
        assertThat(cases.size()).isBetween(20, 30);
        assertThat(cases.stream().map(HeldoutCase::id).distinct()).hasSize(cases.size());
    }

    @Test
    void Stability_Subset_20건은_Held_out_Set에서_뽑은_고정된_부분집합이다() {
        List<HeldoutCase> heldout = SpringAiModerationHeldoutEvaluationTest.heldoutCases();
        List<HeldoutCase> stability = SpringAiModerationHeldoutEvaluationTest.stabilitySubset();
        Set<String> heldoutIds = heldout.stream().map(HeldoutCase::id).collect(Collectors.toSet());

        assertThat(stability).hasSize(20);
        assertThat(stability.stream().map(HeldoutCase::id).distinct()).hasSize(20);
        assertThat(heldoutIds).containsAll(stability.stream().map(HeldoutCase::id).toList());
    }

    @Test
    void Held_out과_Challenge_문장은_기존_40건_Regression_Set_문장과_중복되지_않는다() {
        Set<String> existingMessages = SpringAiModerationHeldoutEvaluationTest.existingRegressionSetMessagesForDuplicateCheck();
        List<String> newMessages = new ArrayList<>();
        SpringAiModerationHeldoutEvaluationTest.heldoutCases().forEach(c -> newMessages.add(c.message()));
        SpringAiModerationHeldoutEvaluationTest.challengeCases().forEach(c -> newMessages.add(c.message()));

        assertThat(newMessages).doesNotContainAnyElementsOf(existingMessages);
    }

    @Test
    void Held_out과_Challenge_문장은_서로도_중복되지_않는다() {
        List<String> all = new ArrayList<>();
        SpringAiModerationHeldoutEvaluationTest.heldoutCases().forEach(c -> all.add(c.message()));
        SpringAiModerationHeldoutEvaluationTest.challengeCases().forEach(c -> all.add(c.message()));

        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    void 기존_40건_중복_검사용_사본은_정확히_40건이다() {
        assertThat(SpringAiModerationHeldoutEvaluationTest.existingRegressionSetMessagesForDuplicateCheck()).hasSize(40);
    }
}

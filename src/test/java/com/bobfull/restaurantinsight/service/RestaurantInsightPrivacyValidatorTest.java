package com.bobfull.restaurantinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RestaurantInsightPrivacyValidatorTest {
    private final RestaurantInsightPrivacyValidator validator = new RestaurantInsightPrivacyValidator();

    @Test
    void 전화번호가_포함된_메시지는_Insight_분석에서_제외한다() {
        assertThat(validator.containsSensitiveIdentifier("010-1234-5678로 연락주세요")).isTrue();
    }

    @Test
    void 식별_단서가_없는_메뉴_aspect만_저장을_허용한다() {
        assertThat(validator.isSafeAspect("탕수육")).isTrue();
        assertThat(validator.isSafeAspect("김철수님")).isFalse();
        assertThat(validator.isSafeAspect("010 1234 5678")).isFalse();
    }

    @Test
    void aspect는_NFKC와_공백을_정규화하고_40자를_초과하면_제외한다() {
        assertThat(validator.normalizeSafeAspect("  탕수육　 맛  ")).isEqualTo("탕수육 맛");
        assertThat(validator.normalizeSafeAspect("가".repeat(41))).isNull();
    }
}

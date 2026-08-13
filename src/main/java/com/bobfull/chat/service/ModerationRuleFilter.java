package com.bobfull.chat.service;

import com.bobfull.chat.dto.ModerationResult;
import com.bobfull.chat.entity.ModerationCategory;
import com.bobfull.chat.entity.ModerationResultType;
import com.bobfull.chat.entity.RiskLevel;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** #251의 고신뢰 CLEAR_FLAGGED 전용 filter다. 매칭하지 않으면 항상 LLM에 위임한다. */
@Component
public class ModerationRuleFilter {
    private static final Pattern MOBILE_PHONE = Pattern.compile("(?<!\\d)010[\\s.-]*\\d{4}[\\s.-]*\\d{4}(?!\\d)");

    public Optional<ModerationResult> clearFlagged(String content) {
        if (isPromptInjectionCandidate(content)) return Optional.empty();
        if (MOBILE_PHONE.matcher(content).find()) {
            // 단일 Rule 결과가 다른 위반 category를 누락할 수 있는 복합 입력은 기존 LLM 다중분류에 맡긴다.
            if (content.contains("수익방")) return Optional.empty();
            return flagged(ModerationCategory.PERSONAL_INFORMATION, RiskLevel.MEDIUM);
        }

        String canonical = content.replaceAll("[\\s.\\-@]+", "");
        if (canonical.contains("씨발") || canonical.contains("시발") || canonical.contains("개새끼") || canonical.contains("죽여버린다")) {
            return flagged(ModerationCategory.PROFANITY, RiskLevel.HIGH);
        }
        if (content.contains("코인 수익방") || content.contains("주식 리딩방") || content.contains("대출 승인 보장")) {
            return flagged(ModerationCategory.SPAM, RiskLevel.HIGH);
        }
        return Optional.empty();
    }

    private static Optional<ModerationResult> flagged(ModerationCategory category, RiskLevel riskLevel) {
        return Optional.of(new ModerationResult(ModerationResultType.FLAGGED, java.util.Set.of(category), riskLevel));
    }

    private static boolean isPromptInjectionCandidate(String content) {
        return content.contains("이전 지시") || content.contains("이전 명령") || content.contains("System Prompt")
                || content.contains("JSON Schema") || content.contains("Moderation AI") || content.contains("{\"result\":\"SAFE\"}")
                || content.contains("관리자 권한") || content.contains("분석하지 말고") || content.contains("정책을 공개")
                || content.contains("역할을 바꿔");
    }
}

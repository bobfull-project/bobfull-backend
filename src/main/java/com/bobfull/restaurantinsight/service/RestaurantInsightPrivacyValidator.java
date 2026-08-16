package com.bobfull.restaurantinsight.service;

import java.util.regex.Pattern;
import java.text.Normalizer;
import org.springframework.stereotype.Component;

/** 외부 Provider 전송 전과 Insight 저장 전의 최소 개인정보 차단 규칙이다. */
@Component
public class RestaurantInsightPrivacyValidator {
    private static final Pattern PHONE = Pattern.compile("(?:01[016789]|0[2-9][0-9]?)[ -]?\\d{3,4}[ -]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern RESERVATION_NUMBER = Pattern.compile("(?i)(예약|주문)\\s*(번호|no\\.?)?\\s*[:#-]?\\s*\\d{4,}");
    private static final Pattern ALLOWED_ASPECT = Pattern.compile("[\\p{L}\\p{N} .,&+()/\\-]{1,40}");
    public boolean containsSensitiveIdentifier(String value) { return PHONE.matcher(value).find() || EMAIL.matcher(value).find() || RESERVATION_NUMBER.matcher(value).find(); }
    public String normalizeSafeAspect(String aspect) {
        if (aspect == null) return null;
        String normalized = Normalizer.normalize(aspect, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (!ALLOWED_ASPECT.matcher(normalized).matches() || containsSensitiveIdentifier(normalized)
                || normalized.contains("님") || normalized.contains("직원분") || normalized.contains("사장님")) return null;
        return normalized;
    }
    public boolean isSafeAspect(String aspect) { return normalizeSafeAspect(aspect) != null; }
}

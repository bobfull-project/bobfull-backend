package com.bobfull.restaurantinsight.service;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 외부 Provider 전송 전(입력 원문)과 Insight 저장 전(추출된 aspect)의 최소 개인정보·재식별 차단 규칙이다.
 *
 * <p>차단 대상: 전화번호, 이메일, 예약/주문번호, URL, 존칭이 붙은 이름, 신체·복장 묘사가 결합된 직원 식별
 * 표현, 잘 알려진 예시용 인명(전수 목록). 사람 이름 자체를 일반 NLP 개체명 인식 없이 완벽히 걸러낼 수는
 * 없으므로, 이 Validator는 정규식/키워드 기반 방어선이며 완벽한 인명 인식을 주장하지 않는다.</p>
 */
@Component
public class RestaurantInsightPrivacyValidator {
    private static final int MAX_ASPECT_CODE_POINTS = 40;

    private static final Pattern PHONE = Pattern.compile("(?:01[016789]|0[2-9][0-9]?)[ -]?\\d{3,4}[ -]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern RESERVATION_NUMBER = Pattern.compile("(?i)(예약|주문)\\s*(번호|no\\.?)?\\s*[:#-]?\\s*\\d{4,}");
    private static final Pattern URL = Pattern.compile(
            "(?i)(https?://\\S+|www\\.\\S+|\\b[a-z0-9][a-z0-9-]*\\.(com|net|org|io|co\\.kr|kr|me)(/\\S*)?\\b)");
    private static final Pattern ACCOUNT_IDENTIFIER = Pattern.compile("(?i)(계정|아이디|id)\\s*[:#-]?\\s*[a-z0-9_.]{3,}");

    // 계약이 허용한 문자만: Unicode 문자/숫자/공백 + 메뉴 표기용 기호 - · / & ( )
    private static final Pattern ALLOWED_ASPECT_CHARS = Pattern.compile("[\\p{L}\\p{N} \\-·/&()]+");

    // 직원 신체·복장·인상착의 묘사 + 역할 단어가 함께 나오면 "안경 쓴 남자 직원", "모자 쓴 직원"처럼
    // 특정 직원을 재식별할 수 있는 표현으로 간주한다. "직원 응대"처럼 일반화된 표현은 DESCRIPTOR가 없어 통과한다.
    private static final Pattern STAFF_ROLE = Pattern.compile("(직원|사장|매니저|알바|점장|기사)");
    private static final Pattern PERSON_DESCRIPTOR = Pattern.compile(
            "(안경|모자|수염|문신|흉터|파마|아저씨|아줌마|젊은|나이\\s*(많|든)|남자|여자|키\\s*(가|큰|작은)|머리\\s*(긴|짧은|묶은|염색))");

    // 이름 뒤에 존칭이 붙은 형태("김철수님", "박OO씨", "이OO 기사님" 등)
    private static final Pattern HONORIFIC_NAME = Pattern.compile("[가-힣]{1,3}\\s*(씨|님|군|양|기사님|선생님|사장님)");

    // 일반 NLP 개체명 인식 없이는 임의 실명을 완벽히 걸러낼 수 없으므로, 흔히 쓰이는 예시/placeholder 인명만
    // 전수 목록으로 차단한다(정규식 기반 성씨 패턴은 "조림"/"전골" 같은 음식 단어를 오탐하므로 사용하지 않는다).
    private static final Set<String> COMMON_PLACEHOLDER_NAMES = Set.of(
            "김철수", "이영희", "박민수", "최영수", "정지훈", "홍길동", "김영희", "이철수", "박철수", "최철수", "김민수", "이민수");

    public boolean containsSensitiveIdentifier(String value) {
        if (value == null) return false;
        return PHONE.matcher(value).find() || EMAIL.matcher(value).find() || RESERVATION_NUMBER.matcher(value).find()
                || URL.matcher(value).find() || ACCOUNT_IDENTIFIER.matcher(value).find()
                || identifiesSpecificPerson(value);
    }

    public String normalizeSafeAspect(String aspect) {
        if (aspect == null) return null;
        String normalized = Normalizer.normalize(aspect, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return null;
        if (normalized.codePointCount(0, normalized.length()) > MAX_ASPECT_CODE_POINTS) return null;
        if (!ALLOWED_ASPECT_CHARS.matcher(normalized).matches()) return null;
        if (containsSensitiveIdentifier(normalized)) return null;
        return normalized;
    }

    public boolean isSafeAspect(String aspect) { return normalizeSafeAspect(aspect) != null; }

    private boolean identifiesSpecificPerson(String text) {
        if (HONORIFIC_NAME.matcher(text).find()) return true;
        if (text.contains("직원분")) return true;
        if (STAFF_ROLE.matcher(text).find() && PERSON_DESCRIPTOR.matcher(text).find()) return true;
        String trimmed = text.trim();
        return COMMON_PLACEHOLDER_NAMES.contains(trimmed);
    }
}

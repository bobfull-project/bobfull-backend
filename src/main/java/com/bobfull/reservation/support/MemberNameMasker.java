package com.bobfull.reservation.support;

/**
 * 회원 이름을 노쇼 이력·집계 응답에 노출할 때 가운데 글자를 마스킹한다(Issue #48 §9-1·9-4·9-5).
 * PR #130(Issue #49)이 같은 목적의 {@code com.bobfull.admin.support.MemberNameMasker}를 별도로
 * 추가하는 중이라 우연히 중복될 수 있다 — 두 PR이 모두 병합되면 공통 위치로 정리한다.
 */
public final class MemberNameMasker {

    private MemberNameMasker() {
    }

    public static String mask(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "○";
        }
        return name.charAt(0) + "○".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}

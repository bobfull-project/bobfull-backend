package com.bobfull.admin.support;

/** 회원 이름을 노쇼 통계 응답에 노출할 때 가운데 글자를 마스킹한다(Issue #49 §11-11, API 명세 예시 "홍○동" 기준). */
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

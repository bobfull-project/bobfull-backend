package com.bobfull.common.security;

/**
 * 인증된 회원의 역할이다.
 * docs/PROJECT_CONTEXT.md의 일반 사용자·식당 사장님·관리자 역할에 대응한다.
 */
public enum MemberRole {
    MEMBER,
    OWNER,
    ADMIN
}

package com.bobfull.auth.dto;

import com.bobfull.common.security.MemberRole;
import com.bobfull.member.entity.Member;

public record SignupResponse(
        Long memberId,
        String email,
        String name,
        MemberRole role
) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}

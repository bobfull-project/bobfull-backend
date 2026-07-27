package com.bobfull.member.dto;

public record MemberUpdateResponse(boolean result) {

    public static MemberUpdateResponse success() {
        return new MemberUpdateResponse(true);
    }
}

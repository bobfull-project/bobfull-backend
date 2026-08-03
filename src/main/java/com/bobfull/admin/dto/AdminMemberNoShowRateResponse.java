package com.bobfull.admin.dto;

import com.bobfull.admin.support.MemberNameMasker;

public record AdminMemberNoShowRateResponse(
        Long memberId,
        String name,
        long totalReservationCount,
        long noShowCount,
        double noShowRate
) {
    public static AdminMemberNoShowRateResponse of(AdminMemberNoShowRateResult result, double noShowRate) {
        return new AdminMemberNoShowRateResponse(
                result.memberId(), MemberNameMasker.mask(result.name()),
                result.totalReservationCount(), result.noShowCount(), noShowRate);
    }
}

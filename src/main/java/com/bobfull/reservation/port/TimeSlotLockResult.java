package com.bobfull.reservation.port;

import java.math.BigDecimal;

/**
 * TimeSlot 잠금 뒤 예약 결제 준비에 필요한 좌석 정보만 담는다.
 * TimeSlot·SharedTable·Restaurant의 실제 엔티티·스키마는 #33·식당 도메인이 소유한다.
 */
public record TimeSlotLockResult(
        Long timeSlotId,
        int capacity,
        BigDecimal depositPerPerson
) {
}

package com.bobfull.reservation.port;

import java.util.Optional;

/**
 * 예약 결제 준비가 TimeSlot 도메인(#33)에 요구하는 최소 계약이다.
 * 대상 회차를 잠그고(비관적 락, ADR 0001) 좌석 계산에 필요한 정원·1인당 예약금만 반환한다.
 * TimeSlot 등록·조회·수정·삭제와 실제 엔티티·저장 방식은 #33이 구현하며,
 * 이 인터페이스의 구현체는 #33 병합 후 연결한다.
 */
public interface TimeSlotLockPort {

    Optional<TimeSlotLockResult> lockForReservation(Long timeSlotId);
}

package com.bobfull.timeslot.service;

import org.springframework.stereotype.Service;

/**
 * 예약 도메인이 연결될 때 회차 변경 가능 여부를 검증하는 경계다.
 */
@Service
public class TimeSlotReservationValidator {

    public void validateChangeAllowed(Long sessionId) {
        // 예약 도메인이 구현되면 활성 예약 존재 기준 수정 제한을 이 경계에서 연결한다.
    }

    public void validateDeletionAllowed(Long sessionId) {
        // 예약 도메인이 구현되면 활성 예약 존재 기준 삭제 제한을 이 경계에서 연결한다.
    }
}

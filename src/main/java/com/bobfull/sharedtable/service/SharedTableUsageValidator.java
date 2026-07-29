package com.bobfull.sharedtable.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.SharedTableErrorCode;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;

/**
 * 회차·예약 도메인이 연결될 때 합석 테이블 변경 가능 여부를 검증하는 경계다.
 */
@Service
public class SharedTableUsageValidator {

    private final TimeSlotRepository timeSlotRepository;

    public SharedTableUsageValidator(TimeSlotRepository timeSlotRepository) {
        this.timeSlotRepository = timeSlotRepository;
    }

    public void validateCapacityChangeAllowed(Long tableId) {
        // 예약 도메인이 구현되면 예약 존재 기준 정원 변경 제한을 이 경계에서 연결한다.
    }

    public void validateDeletionAllowed(Long tableId) {
        if (timeSlotRepository.existsBySharedTableIdAndDeletedAtIsNull(tableId)) {
            throw new CustomException(SharedTableErrorCode.TABLE_HAS_DINING_SESSION);
        }
    }
}

package com.bobfull.reservation.port;

/**
 * 예약 확정 기준 계산에 필요한 테이블 정원을 제공한다.
 */
public interface ReservationCapacityReader {

    int readTableCapacity(Long timeSlotId);
}

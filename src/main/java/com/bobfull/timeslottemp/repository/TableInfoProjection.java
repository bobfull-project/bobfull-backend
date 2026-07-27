package com.bobfull.timeslottemp.repository;

import java.math.BigDecimal;

/**
 * TimeSlot이 속한 SharedTable의 정원과 Restaurant의 1인당 예약금을 함께 조회하는 Projection이다.
 * SharedTable·Restaurant 엔티티는 각각 #33·식당 도메인 소유라 별도 매핑 클래스를 만들지 않고,
 * 예약 결제 준비(#35)에 필요한 두 값만 native query로 읽는다.
 */
public interface TableInfoProjection {

    Integer getCapacity();

    BigDecimal getDepositPerPerson();
}

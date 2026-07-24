# ADR 0001: 예약 좌석 정합성과 임시 선점 전략

- 상태: `Accepted`
- 작성일: `2026-07-24`
- 관련 Issue: `#18`

## 배경

최초 예약과 추가 참여는 결제 완료 전에도 남은 좌석을 고려해야 하며, 같은 TimeSlot에 대한 동시 요청이 정원을 초과하지 않아야 한다.

## 문제

결제 대기 중인 요청을 좌석 계산에서 제외하면 초과 참여가 가능하고, TimeSlot에 단순 UNIQUE를 두면 취소 이력 보존과 재사용이 어려워진다.

## 고려한 대안

- 별도 `SeatHold` 엔티티로 임시 선점을 관리한다.
- `reservation.time_slot_id`에 단순 UNIQUE를 둔다.
- 만료되지 않은 `PaymentStatus.READY`를 임시 선점으로 사용하고 TimeSlot 기준으로 확인한다.

## 결정

별도 `SeatHold` 엔티티를 만들지 않고 만료되지 않은 `PaymentStatus.READY`로 10분 임시 선점을 표현한다. 결제 성공 전에는 `Reservation` 또는 `ReservationParticipant`를 생성하지 않는다.

`availableCapacity`는 테이블 정원에서 PAID 유효 참여 인원과 만료되지 않은 READY 선점 인원을 차감해 계산한다. 동일 TimeSlot의 최초 예약은 TimeSlot 행 비관적 락과 활성 Reservation·유효 CREATE READY 확인으로 직렬화하며, 활성 Reservation 또는 유효한 CREATE READY는 동시에 최대 1건만 허용한다.

## 선택 이유

현재 결제 상태와 예약 흐름을 이용해 임시 선점을 표현하면서, 취소 이력을 보존하는 TimeSlot 재사용 요구와 동시 최초 예약 방지 요구를 함께 만족한다.

## 장점

- 임시 선점 상태를 현재 Payment 모델과 함께 관리한다.
- 결제 성공 전 예약·참여 데이터 생성을 피한다.
- 활성 예약과 유효 CREATE READY의 중복 생성을 방지한다.

## 단점과 위험

- READY Payment의 만료·정리·추적 책임이 중요하다.
- TimeSlot 비관적 락은 처리량 증가 시 병목이 될 수 있다.
- 다중 인스턴스 환경에서 별도 선점 저장소가 필요해질 수 있다.

## 검증 방법

동일 TimeSlot의 동시 최초 예약 요청에서 활성 Reservation 또는 유효 CREATE READY의 성공 건수가 최대 1건인지 검증한다. 좌석 계산과 상태의 상세 계약은 [PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md), [ERD.md](../ERD.md), [API 명세](../BOBFULL_API_SPEC_COMPLETE.md)를 따른다.

## 재검토 조건

- READY Payment만으로 선점 만료·정리·추적이 어려워질 때
- TimeSlot 비관적 락 병목이 확인될 때
- 다중 인스턴스에서 별도 선점 저장소가 필요할 때

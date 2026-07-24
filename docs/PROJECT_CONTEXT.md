# 밥풀(BobFull) 프로젝트 컨텍스트

> 기준일: 2026-07-23
> API 계약의 최우선 기준은 [`BOBFULL_API_SPEC_COMPLETE.md`](./BOBFULL_API_SPEC_COMPLETE.md)다. 이 문서가 API 명세와 충돌하면 API 명세를 따른다.

## 1. 프로젝트 개요

**밥풀**은 제주 평일 저녁의 혼자 여행하는 사용자를 핵심 타깃으로, 사용자가 식당의 합석 회차를 선택해 예약하거나 기존 합석 예약에 참여하는 서비스다. OWNER는 식당·합석 테이블·합석 회차를 관리하고, MEMBER는 `partySize`를 포함해 예약 결제를 준비한다. PortOne 결제 검증 또는 웹훅 검증이 성공하면 예약·참여·결제 결과를 반영한다. 서비스 대상 지역은 제주로 한정한다.

## 2. 역할과 권한

역할은 `MEMBER`, `OWNER`, `ADMIN`이다.

### MEMBER

- 인증 없이 사용자용 식당·예약 가능 회차·참여 가능한 예약을 조회한다.
- 인증 후 본인 정보·예약·참여·결제·환불을 조회하고 예약 결제를 준비한다.
- 최초 예약자는 본인 예약의 모집 상태를 `CLOSED`로 변경한다.
- V2에서 서버 시간 기준 식사 시작 2시간 전까지 본인 참여 전체를 취소한다. 부분 `partySize` 취소와 2시간 이내 MEMBER 취소는 지원하지 않는다.
- 결제 완료 검증은 해당 Payment를 생성한 당사자만 호출한다. `Payment.memberId`와 인증 사용자 ID가 일치해야 한다.
- V1은 참여자 상세 목록 대신 예약 집계 정보만 조회한다. V2에서는 해당 예약의 유효 참여자만 `nickname`, `partySize` 목록을 조회한다.

### OWNER

- 본인 식당의 식당·합석 테이블·합석 회차를 등록·조회·수정·삭제한다.
- 식당·테이블 생성 시 서버가 `ACTIVE`를 기본 적용한다. 등록·수정 Request에 `status`는 없다.
- 본인 식당의 예약·참여자 정보를 조회한다.
- V1에서 지급 예정 금액과 예약별 지급 예정 내역·상세를 조회한다.
- V2에서 식당 귀책 예약 취소와 식사 종료 후 노쇼 처리·해제·이력 조회를 수행한다.

### ADMIN

- V2에서 회원·식당·예약·결제·환불·노쇼 현황 및 운영 지표를 조회한다.
- V3에서 실패 결제·환불과 정산 데이터를 재처리하거나 재집계한다.

## 3. 테이블·인원 정책

- `capacity`는 `2`, `4`, `6`, `8`만 허용한다. 단건 테이블 등록·수정과 테이블·회차 일괄 등록에 동일하게 적용한다.
- 허용 범위 밖 `capacity`의 ErrorCode는 `INVALID_TABLE_CAPACITY`다.
- 예약 생성(`CREATE`)은 `1 <= partySize <= table.capacity`여야 한다.
- 추가 참여(`JOIN`)은 `1 <= partySize <= availableCapacity`여야 한다.
- 잘못된 `partySize`는 `INVALID_PARTY_SIZE`, 추가 참여 가능 인원 초과는 `INSUFFICIENT_REMAINING_CAPACITY`다.

```text
currentParticipantCount
= PAID 상태 유효 참여자의 partySize 합계

임시 선점 인원
= 만료되지 않은 READY 결제의 partySize 합계

availableCapacity
= capacity - currentParticipantCount - 임시 선점 인원
```

- V1 예약 상세는 `currentParticipantCount`, `availableCapacity`, `confirmationThreshold`, `reservationStatus`, `recruitmentStatus`의 집계 정보를 제공한다.

## 4. 예약·모집 상태

### 상태 enum

```text
ReservationStatus: RECRUITING, CONFIRMED, CANCELLED, CLOSED
RecruitmentStatus: OPEN, CLOSED
ParticipationStatus: RESERVED, NO_SHOW, CANCELLED
PaymentStatus: READY, PAID, FAILED, CANCELLED
RefundStatus: REQUESTED, PROCESSING, COMPLETED, FAILED
```

### 확정 기준과 흐름

| 테이블 정원 | confirmationThreshold |
|---:|---:|
| 2 | 2 |
| 4 | 3 |
| 6 | 5 |
| 8 | 7 |

1. 최초 결제 완료 시 예약은 `RECRUITING + OPEN`으로 시작한다.
2. 확정 기준 도달 시 `CONFIRMED + OPEN`이 된다.
3. 정원 도달 시 `CONFIRMED + CLOSED`가 된다.
4. 식사 시작 2시간 전에 모집은 `CLOSED`가 된다.
5. 모집 마감 시 확정 기준 미달이면 예약은 `CANCELLED`가 되고 참여자 전액을 환불한다.

`CONFIRMED`는 모집 종료를 의미하지 않는다.

### 최초 예약자 수동 모집 마감

- 최초 예약자만 `reservationStatus=CONFIRMED`와 `recruitmentStatus=OPEN`을 모두 만족할 때 모집을 `CLOSED`로 변경할 수 있다.
- `RECRUITING` 예약은 수동 마감할 수 없고, 이미 `CLOSED`인 모집을 중복 마감하거나 다시 `OPEN`으로 변경할 수 없다.
- 수동 마감 자체는 TimeSlot을 복구하지 않는다. TimeSlot 재사용은 Reservation 전체가 `CANCELLED`된 뒤에만 판단한다.

## 5. 예약·결제·환불

### 예약 결제 준비

1. 클라이언트는 `/api/reservations/prepare`에 `type`, `targetId`, `partySize`를 전송한다.
2. `CREATE`의 `targetId`는 `sessionId`, `JOIN`의 `targetId`는 `reservationId`다.
3. 서버는 좌석을 10분간 임시 선점하고 `PaymentStatus.READY`와 PortOne `paymentId`를 생성한다.
4. 결제 성공 전에는 예약 또는 참여자를 생성하지 않는다. 실패 또는 만료 시 `FAILED`와 좌석 해제를 반영한다.

### 결제 완료와 웹훅

- 결제 당사자는 `/api/payments/{paymentId}/complete`로 결제 완료를 검증한다.
- 당사자가 아니면 `PAYMENT_ACCESS_DENIED`, 대상 결제가 없으면 `PAYMENT_NOT_FOUND`, 이미 완료 처리됐으면 `PAYMENT_ALREADY_COMPLETED`를 반환한다.
- PortOne 웹훅은 사용자 인증 대신 서명 검증과 멱등성으로 처리한다.
- 완료 검증과 웹훅이 동시에 실행되어도 예약·참여·결제 상태는 한 번만 반영한다.

### 취소·환불

- V2에서 MEMBER는 서버 시간 기준 식사 시작 2시간 전까지만 본인 `ReservationParticipant`의 신청 인원 전체를 취소한다. 부분 취소·부분 환불·마감 이후 무환불 취소는 이번 범위에 없다.
- 최초 예약자 취소는 예약 전체를 `CANCELLED`로 변경하고, 다른 유효 참여자를 포함해 전액 환불한다. 기존 예약이 `CANCELLED`이고 현재 시간이 식사 시작 2시간 전보다 이전이며 다른 활성 예약 또는 OWNER·시스템 사용 제한이 없을 때만 TimeSlot을 새 예약 가능 상태로 복구한다.
- 추가 참여자 취소는 해당 `ReservationParticipant`만 `CANCELLED`로 변경하고 본인 Payment 전체를 환불한다. `currentParticipantCount`와 `availableCapacity`를 재계산하며, 모집이 `OPEN`일 때 확정 기준 미달이면 `RECRUITING`, 기준 이상이면 `CONFIRMED`를 유지한다.
- 수동 마감된 `CONFIRMED + CLOSED` 예약에서 추가 참여자 취소 후 확정 기준 이상이면 `CONFIRMED + CLOSED`를 유지한다. 기준 미달이면 Reservation 전체를 `CANCELLED`로 변경하고 남은 유효 참여자를 전액 환불한다. 모집은 다시 `OPEN`으로 변경하지 않으며, ChatRoom 신규 메시지 전송을 종료한다.
- 모집 마감은 정원 도달 또는 식사 시작 2시간 전에 `CLOSED`가 된다. 마감 시 확정 기준 미달이면 예약 전체를 `CANCELLED`로 변경하고 남은 유효 참여자를 전액 환불하며, 취소 좌석을 재모집하지 않는다.
- V2에서 OWNER 또는 시스템 귀책으로 예약을 진행할 수 없으면 예약 전체를 `CANCELLED`로 변경하고 모든 유효 참여자를 전액 환불한다. 참여자를 `NO_SHOW`로 처리하지 않는다. TimeSlot 재사용은 기존 예약이 `CANCELLED`이고 현재 시간이 식사 시작 2시간 전보다 이전이며 다른 활성 예약 또는 OWNER·시스템 사용 제한이 없는 경우만 가능하다.
- MEMBER 취소는 `NO_SHOW` 또는 이미 `CANCELLED`인 참여자에게 허용되지 않는다. 현재 ParticipationStatus에는 `VISITED` 상태가 없다.
- Refund는 Payment 전체 금액을 대상으로 하며 Payment당 하나만 생성한다. 환불 완료 시 `RefundStatus.COMPLETED`와 `PaymentStatus.CANCELLED`를 반영한다.
- 사용자는 환불 처리 API를 직접 호출하지 않으며, V1에서 본인 환불 목록·상세를 조회한다.

### TimeSlot 활성 예약 정합성

- TimeSlot은 취소 이력을 포함해 여러 Reservation을 연결할 수 있지만, `RECRUITING` 또는 `CONFIRMED` 활성 Reservation은 동시에 최대 1건이다.
- `reservation.time_slot_id` 단순 UNIQUE는 사용하지 않는다. 새 최초 예약 또는 READY Payment 생성은 대상 TimeSlot 행을 비관적 락으로 조회하고, 활성 Reservation 존재 여부를 확인한 뒤 트랜잭션 종료까지 잠금을 유지한다.
- `CREATE` 결제 준비는 활성 Reservation뿐 아니라 동일 TimeSlot의 만료되지 않은 `paymentPurpose=CREATE`, `paymentStatus=READY` Payment도 확인한다. 둘 다 없을 때만 새 CREATE READY를 생성하며, 유효한 CREATE READY는 TimeSlot당 최대 1건이다.
- 유효한 CREATE READY가 있으면 `409 ACTIVE_RESERVATION_ALREADY_EXISTS`로 거절한다. 해당 Payment가 만료되거나 `FAILED`가 된 뒤에는 새 CREATE 요청을 허용한다. 기존 Reservation에 대한 `JOIN READY`는 `availableCapacity`를 기준으로 별도 처리한다.
- 실제 구현 Issue에서는 동일 TimeSlot에 대한 동시 최초 예약 생성 시 활성 Reservation 또는 유효한 CREATE READY의 성공이 최대 1건인지 검증한다.

## 6. 노쇼·지급 예정 금액

- V2에서 OWNER는 식사 종료 후 노쇼 처리 대상 참여자를 조회하고, 참여자를 노쇼 처리·해제하며 이력을 조회한다.
- V1 지급 예정 금액은 결제 완료액에서 환불 완료액을 뺀 값이다.

## 7. 채팅

- V2에서 최초 예약 결제 완료 시 예약당 채팅방 1개를 생성한다. 별도 생성 API는 없다.
- 유효 참여자는 결제 완료 참여자 중 `CANCELLED`가 아닌 참여자다. 유효 참여자만 접근하며, `CANCELLED` 참여자는 즉시 접근이 종료되고 OWNER와 ADMIN은 참여하지 않는다.
- 예약이 `CANCELLED` 또는 `CLOSED`면 새 메시지 전송을 종료하고, 기존 메시지는 조회할 수 있다.
- 메시지는 DB에 저장하며 과거 메시지는 cursor 기반으로 조회한다.
- WebSocket 연결 Endpoint는 `/ws`다.
- STOMP 전송 경로는 `/pub/chat/rooms/{reservationId}/messages`, 구독 경로는 `/sub/chat/rooms/{reservationId}`다.
- HTTP 조회 경로는 `GET /api/chat/rooms/{reservationId}/messages`다.
- 읽음 처리, 이미지·파일, 메시지 수정·삭제, 신고·차단, Redis Pub/Sub, Kafka는 범위에서 제외한다.

## 8. 버전 범위

### V1

- 회원·인증, 식당·테이블·회차 관리 및 사용자 조회
- 예약 가능 여부, 결제 준비, 예약·참여 집계 조회, 모집 마감
- PortOne 결제 완료 검증·웹훅, 결제·환불 조회
- 지급 예정 금액·예약별 지급 예정 내역·상세

### V2

- 로그아웃·토큰 재발급
- 참여 취소·식당 귀책 취소·모집 마감/실패/환불/회차 복구 내부 처리
- 유효 참여자 최소 목록, 노쇼, 관리자 현황·통계
- 예약 참여자 채팅과 API·비즈니스 로그, 소프트 딜리트·배포 운영 정책

### V3

- Actuator 모니터링·헬스 체크
- 실패 결제·환불 재처리, 정산 데이터 재집계
- 부하 테스트, 검색 성능·이벤트 처리·모니터링·배포 고도화

Redis와 Kafka는 현재 확정 기능·ERD 선행 계약에 포함하지 않는다. 채팅에도 Redis Pub/Sub나 Kafka를 사용하지 않는다.

## 9. API 공통 계약

- 일반 서비스 API는 `/api/**`를 사용하며 URL에 버전을 넣지 않는다.
- Actuator는 `/actuator/**`, WebSocket 연결 Endpoint는 `/ws`다.
- 성공 응답은 `success`, `message`, `data`를, 실패 응답은 `success`, `code`, `message`를 사용한다.
- OWNER·ADMIN API는 `/api/owner`, `/api/admin`으로 분리한다.

## 10. 역할 분배

| 이름 | 핵심 도메인 | 공통·도전 기술 |
|---|---|---|
| 김현승 | 예약금 결제·환불·지급 예정 예약금 | AI·채팅 |
| 김홍기 | 합석 테이블·예약 시간·검색 | AWS·CI/CD·로그·모니터링 |
| 배지현 | 예약·참여·좌석 재고·동시성 | 프론트엔드·Kafka |
| 정용태 | 회원·인증·사장님·식당·관리자 | 캐시·조회 성능·K6 |

## 11. 관련 문서

- 제품 방향·초기 타깃·MVP 범위: [`PRD.md`](./PRD.md)
- API 명세: [`BOBFULL_API_SPEC_COMPLETE.md`](./BOBFULL_API_SPEC_COMPLETE.md)
- 데이터 모델: [`ERD.md`](./ERD.md)
- 논리 책임 경계: [`ARCHITECTURE.md`](./ARCHITECTURE.md)
- 도메인 변경 영향: [`DOMAIN_DEPENDENCIES.md`](./DOMAIN_DEPENDENCIES.md)

# BobFull 도메인 의존성과 변경 영향

## 1. 목적

이 문서는 정책이나 상태 전이를 새로 정의하지 않는다.

예약·모집·참여·좌석·결제·취소·환불·노쇼·지급 예정 예약금이 서로 어떻게 연결되는지 확인하고, 한 영역의 변경이 다른 담당 영역과 문서·테스트에 누락되지 않도록 사용하는 변경 영향 기준이다.

정책이 이 문서와 충돌하면 [`BOBFULL_API_SPEC_COMPLETE.md`](./BOBFULL_API_SPEC_COMPLETE.md), [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md), [`ERD.md`](./ERD.md)의 순서로 확인한다. 이 세 문서가 충돌하면 임의로 선택하지 않고 Human 판단을 요청한다.

## 2. 도메인 연결 요약

```text
회원·인증
→ 식당·사장님 권한
→ 합석 테이블·예약 회차
→ 최초 예약 생성 또는 추가 참여
→ 좌석 10분 임시 선점과 Payment READY
→ PortOne 예약금 결제·서버 검증
→ Payment PAID 후 참여 인원·예약 상태·모집 상태 반영
→ 취소·환불·회차 복구
→ 사장님의 참여자별 노쇼 처리·해제
→ 지급 예정 예약금 조회
```

| 도메인 | 선행 입력 | 주요 결과 | 직접 영향을 받는 도메인 | 핵심 담당 |
|---|---|---|---|---|
| 회원·인증 | 회원 정보, 권한 | 인증 사용자와 역할 | 식당, 예약, 관리자 | 정용태 |
| 식당·사장님 | 인증된 사장님 | 본인 식당과 소유권 | 테이블·회차, 노쇼, 지급 예정금 | 정용태 |
| 테이블·예약 회차 | 식당, 정원, 날짜·시간 | 예약 가능한 테이블·시간 | 예약, 좌석, 검색 | 김홍기 |
| 예약 | 회차, 최초 예약자 | 예약 상태·모집 상태 | 참여, 취소, 노쇼 | 배지현 |
| 참여자 | 사용자, partySize, 결제 성공 | 참여자 상태·현재 참여 인원 | 좌석, 예약 상태, 환불 | 배지현 |
| 좌석·동시성 | 테이블 정원, 현재 참여 인원 | 남은 참여 가능 인원과 정원 보호 | 예약, 결제 실패 보상 | 배지현 |
| 결제 | 사용자, partySize, 예약금 | `READY/PAID/FAILED/CANCELLED` | 예약·참여 등록, 환불, 지급 예정금 | 김현승 |
| 취소·환불 | 예약·참여자·모집 상태, 마감 시각 | 참여자·예약·결제 상태 변경 | 좌석, 회차 복구, 지급 예정금 | 정용태·김현승·배지현 |
| 노쇼 | 사장님 소유권, `RESERVED` 참여자 | `NO_SHOW/RESERVED`, 처리 이력 | 지급 예정금, 노쇼율 | 정용태·김현승 |
| 채팅 | 예약, 결제 완료 참여자 | 예약별 채팅방과 접근 권한 | 예약, 인증 | 김현승 |
| 지급 예정 예약금 | 결제·환불·취소·노쇼 결과 | 사장님 조회용 예상 금액 | 사장님 권한, 관리자 조회 | 김현승 |

구현 데이터 모델은 `Member`, `Restaurant`, `SharedTable`, `TimeSlot`, `Reservation`, `ReservationParticipant`, `Payment`, `Refund`, `ChatRoom`, `ChatMessage`로 연결한다. `NoShowHistory`는 V2 OWNER의 노쇼 처리·해제 이력을 보존하기 위해 `ReservationParticipant`와 OWNER 처리자를 연결한다. `Settlement`, `SeatHold`, `WebhookEvent`는 현재 확정 엔티티가 아니다.

담당자는 단독 소유권을 뜻하지 않는다. 여러 도메인이 연결되는 Issue는 관련 담당자가 계약과 실패 결과를 함께 확인한다.

## 3. 핵심 공동 작업 경계

### 예약 생성

```text
테이블·회차 유효성 확인
→ 모집 마감 확인
→ partySize 검증
→ 좌석 10분 임시 선점
→ Payment READY와 paymentId 생성
→ PortOne 결제
→ 결제 당사자와 Payment.memberId 확인
→ 서버 상태·금액·통화 검증
→ Payment PAID
→ 예약 생성
→ 최초 참여자 등록
→ 현재 참여 인원에 partySize 반영
→ 예약 상태 계산
→ 모집 상태 OPEN
```

필수 공동 검토:

- 김홍기: 테이블·회차 유효성, 중복 회차
- 김현승: PortOne 결제 준비·검증·웹훅·상태와 결제 금액
- 배지현: 예약·참여자·partySize·상태 반영과 중복 생성 방지

### 추가 참여

```text
예약 상태와 모집 상태 확인
→ 동일 사용자 중복 참여 확인
→ 남은 참여 가능 인원 확인
→ 좌석 10분 임시 선점
→ Payment READY와 paymentId 생성
→ PortOne 결제·서버 검증
→ Payment PAID
→ 참여자 등록
→ 현재 참여 인원·임시 선점 인원 재계산
→ 예약 상태와 모집 상태 재계산
```

필수 공동 검토:

- 배지현: 정원·동시성·상태 재계산
- 김현승: PortOne 결제와 실패·만료·웹훅 결과
- 김홍기: 조회 조건과 회차 정보

### 결제 완료 검증과 웹훅

```text
결제 준비와 좌석 10분 임시 선점
→ 프론트 PortOne 결제
→ 완료 검증 API 또는 PortOne 웹훅
→ PortOne 결제 단건 재조회
→ 상태·금액·통화 검증
→ 성공 시 PAID와 예약·참여 반영
→ 실패·만료 시 FAILED와 좌석 해제
```

- 완료 검증 API는 인증된 결제 당사자만 호출하며 `Payment.memberId`와 인증 사용자 ID를 확인한다. PortOne 웹훅은 사용자 인증 대신 서명 검증과 멱등성으로 처리한다.
- 완료 검증 API와 웹훅이 동시에 실행돼도 한 번만 반영한다.
- 환불 완료 시 `RefundStatus.COMPLETED`와 `PaymentStatus.CANCELLED`를 일치시킨다.

### 예약 확정과 모집 마감

```text
현재 참여 인원과 테이블별 확정 기준 비교
→ 기준 이상이면 CONFIRMED
→ 모집 상태 OPEN이면 잔여 정원까지 추가 참여
→ 최초 예약자 수동 마감은 CONFIRMED + OPEN에서만 허용
→ 정원 도달 또는 시작 2시간 전
→ 모집 상태 CLOSED
```

필수 공동 검토:

- 배지현: 테이블별 확정 기준과 상태 전이
- 김홍기: 테이블 정원·회차 시작 시각
- 김현승: 모집 실패 환불 대상과 금액

- 수동 마감은 최초 예약자만 호출할 수 있고, `RECRUITING` 예약·이미 `CLOSED`인 모집·`CLOSED → OPEN` 변경은 허용하지 않는다.
- 수동 마감 자체는 TimeSlot을 복구하지 않는다. Reservation 전체가 `CANCELLED`된 뒤에만 TimeSlot 재사용 가능 여부를 판단한다.

### MEMBER 취소·환불

```text
인증 MEMBER와 본인 ReservationParticipant 확인
→ 서버 시간 기준 식사 시작 2시간 전인지 확인
→ 최초 예약자 여부 분기
→ 최초 예약자: Reservation CANCELLED·모든 유효 참여자 Payment 전액 환불·조건부 TimeSlot 복구
→ 추가 참여자: 본인 ReservationParticipant CANCELLED·본인 Payment 전액 환불
→ currentParticipantCount와 availableCapacity 재계산
→ 모집 OPEN이면 confirmationThreshold 기준으로 RECRUITING 또는 CONFIRMED 계산
→ 수동 마감 CLOSED면 기준 이상은 CONFIRMED + CLOSED 유지, 기준 미달은 전체 CANCELLED·남은 유효 참여자 전액 환불
→ ChatRoom 신규 메시지 전송 규칙과 지급 예정금 반영
```

필수 공동 검토:

- 정용태: 본인 참여 권한, OWNER 식당 소유권, NO_SHOW 이후 취소 차단
- 김현승: Payment 전체 환불, Payment당 Refund 1건, 지급 예정금
- 배지현: 최초·추가 참여자 분기, 참여 인원·예약 상태·모집 상태·채팅 종료 반영
- 김홍기: `CANCELLED` 예약·시작 2시간 전·활성 예약 없음·OWNER 제한 없음 조건의 TimeSlot 복구

- 식사 시작 2시간 이내에는 MEMBER 취소를 허용하지 않는다. 부분 취소·부분 환불·마감 이후 무환불 취소·취소 후 재모집은 이번 범위에 없다.
- OWNER 또는 시스템 귀책 취소는 별도 OWNER 권한 또는 내부 처리로 예약 전체를 `CANCELLED`로 변경하고 유효 참여자 전액 환불을 요청한다. 참여자를 `NO_SHOW`로 처리하지 않는다.

### TimeSlot 활성 예약 정합성

```text
최초 예약 결제 준비 또는 새 Reservation 생성
→ 대상 TimeSlot 행 비관적 락 획득
→ RECRUITING·CONFIRMED 활성 Reservation 존재 여부 조회
→ CREATE면 만료되지 않은 CREATE READY Payment 존재 여부 조회
→ 활성 Reservation과 유효 CREATE READY가 모두 없을 때만 CREATE READY 또는 새 Reservation 생성
→ 트랜잭션 종료까지 TimeSlot 잠금 유지
```

- `reservation.time_slot_id` 단순 UNIQUE는 취소 이력 보존과 TimeSlot 재사용을 막으므로 사용하지 않는다. TimeSlot 1:N Reservation 관계에서 `CANCELLED` 이력은 유지한다.
- CREATE의 정합성 경계는 TimeSlot 행 잠금, 활성 Reservation 조회, 유효 CREATE READY 조회다. 유효 CREATE READY는 만료되지 않은 `paymentPurpose=CREATE`, `paymentStatus=READY` Payment이며 TimeSlot당 최대 1건이다. 실제 구현 Issue에서 동일 TimeSlot 동시 CREATE 요청의 활성 Reservation 또는 유효 CREATE READY 성공 건수가 최대 1건인지 동시성 테스트로 검증한다. JOIN READY는 `availableCapacity`를 기준으로 별도 처리한다.

### 노쇼와 예약 종료

```text
식사 종료
→ 예약 CLOSED
→ 사장님 소유권 확인
→ 예약 참여자 목록 조회
→ RESERVED 참여자를 NO_SHOW 처리
→ 잘못 처리한 경우 NO_SHOW 해제
→ 지급 예정 예약금과 노쇼율 반영
```

- 노쇼 처리와 해제는 참여자 사용자 단위이며 해당 사용자의 `partySize` 전체에 적용한다.
- `NoShowHistory`에는 OWNER가 수행한 처리·해제와 처리 시각을 남긴다. 이는 V2 노쇼 API의 이력 조회를 위한 감사 기록이며, 별도의 방문·체크인 상태를 뜻하지 않는다.

### 예약 참여자 채팅

```text
최초 예약 Payment PAID
→ 예약당 ChatRoom 1개 생성
→ 결제 완료·미취소 유효 참여자만 접근
→ STOMP 전송·구독 또는 cursor 기반 과거 메시지 조회
→ 예약 CANCELLED 또는 CLOSED 시 새 메시지 전송 종료
→ 기존 ChatMessage는 조회 가능
```

- OWNER와 ADMIN은 채팅 참여자가 아니다. `CANCELLED` 참여자는 즉시 접근이 종료된다.
- 채팅 메시지는 DB에 저장한다. Redis Pub/Sub와 Kafka는 현재 채팅 계약 범위에 없다.

## 4. 핵심 계산 계약

```text
현재 참여 인원
= Σ 결제 완료 참여자.partySize

임시 선점 인원
= Σ 만료되지 않은 READY 결제.partySize

남은 참여 가능 인원
= 테이블 정원 - 현재 참여 인원 - 임시 선점 인원

결제 금액
= partySize × 1인당 예약금

지급 예정 예약금
= PAID 결제 금액 합계 - COMPLETED 환불 금액 합계
```

## 5. 정책 변경 영향표

| 변경 정책 | 필수 영향 도메인 | 함께 확인할 항목 |
|---|---|---|
| 테이블 정원 `2·4·6·8` 변경 | 테이블, 예약, 좌석, 검색 | 정원 검증, 확정 기준, 남은 인원, 동시성 테스트 |
| 테이블별 확정 기준 변경 | 예약, 결제, 취소, 환불, 모집 마감 | `RECRUITING/CONFIRMED`, 모집 실패, 환불 대상 |
| `partySize` 규칙 변경 | 예약, 참여자, 결제, 취소, 노쇼 | 입력 검증, 합산, 결제 금액, 전체 단위 처리 |
| 모집 상태 `OPEN/CLOSED` 변경 | 예약, 참여, 취소, 검색 | 참여 가능 조건, 수동 마감, 자동 마감, 재오픈 금지 |
| 모집 마감 `시작 2시간 전` 변경 | 예약, 결제, 취소, 환불, 알림 | 참여 차단, 재모집, 환불 기준 |
| 결제와 참여 등록 순서 변경 | 결제, 예약, 좌석 | 10분 임시 선점, PortOne 검증, 웹훅 중복, 저장 실패, 보상 처리 |
| 예약 상태 기준 변경 | 예약, 취소, 노쇼, 지급 예정금 | 상태 전이, 종료 조건, 테스트 |
| 참여자 상태 기준 변경 | 예약, 취소, 노쇼, 지급 예정금 | 유효 참여 인원, 노쇼 해제, 금액 계산 |
| 최초 예약자 취소 정책 변경 | 취소, 예약, 환불, 좌석, 회차 | 전체 취소, 나머지 환불, 회차 복구 |
| 환불 기준 변경 | 결제, 취소, 지급 예정금, 관리자 | 환불 상태, 귀책, 미환불 금액 |
| 노쇼 처리·해제 변경 | 인증, 사장님, 예약, 참여자, 지급 예정금 | 소유권, 허용 상태, 처리 이력, 노쇼율 |
| 지급 예정금 계산 변경 | 결제, 환불, 취소, 노쇼, 사장님 | 포함·제외 항목, 조회 시점, 테스트 데이터 |

## 6. 문서와 구현 변경 체크리스트

- [ ] [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md)의 확정 정책과 충돌하지 않는가
- [ ] 영향을 받는 도메인과 담당자를 Issue에 적었는가
- [ ] API Base URL `/api/**`, Actuator `/actuator/**`, WebSocket `/ws`, 공통 응답, 역할 계약이 바뀌는가
- [ ] `partySize`, 정원, 확정 기준과 모집 상태가 DB 모델에 반영되는가
- [ ] 예약·참여자·결제·환불 상태 전이가 바뀌는가
- [ ] 트랜잭션 실패 후 남는 데이터가 바뀌는가
- [ ] 동시 요청 결과와 중복 방지 방식이 바뀌는가
- [ ] 권한과 소유권 검증이 바뀌는가
- [ ] 환불·지급 예정금 계산이 바뀌는가
- [ ] PortOne 완료 검증의 결제 당사자 검증, 웹훅 서명·멱등성, 좌석 임시 선점 해제가 검증되는가
- [ ] 채팅 접근자가 결제 완료·미취소 유효 참여자로 제한되고 CANCELLED/CLOSED 시 전송 규칙이 지켜지는가
- [ ] 전체 플로우차트·API·ERD 중 실제 영향 문서만 수정했는가
- [ ] 완료 조건과 테스트가 변경된 계약을 증명하는가
- [ ] 폐기된 `1인 단위·2명 고정·VISITED` 정책이 남지 않았는가

## 7. v1 검증 우선순위

1. PortOne 결제 실패·10분 만료 시 예약·참여자·현재 참여 인원이 반영되지 않고 좌석 선점이 해제된다.
2. `partySize`가 남은 참여 가능 인원을 초과하면 실패한다.
3. 동시에 N명 참여를 요청해도 정원을 초과하지 않는다.
4. 테이블별 확정 기준에 맞춰 `RECRUITING/CONFIRMED`가 계산된다.
5. `CONFIRMED + OPEN`이면 잔여 정원까지 참여할 수 있다.
6. 모집 상태가 `CLOSED`이면 빈자리가 있어도 참여할 수 없다.
7. 한 사용자의 취소·노쇼가 해당 사용자의 `partySize` 전체에 적용된다.

# ADR 0008: ChatRoom 생성 의도의 Transactional Outbox

- 상태: `Accepted`
- 작성일: `2026-08-08`
- 관련 Issue·PR: #176

## 배경

최초 예약 결제 확정 뒤 ChatRoom은 핵심 결제·예약 트랜잭션 밖에서 생성해야 한다. 기존 `AFTER_COMMIT` 메모리 리스너는 저장 실패가 핵심 데이터를 되돌리지 않게 했지만, 커밋 직후 프로세스가 종료되면 재시작 뒤 실행할 영속 근거가 없었다.

## 결정

CREATE 확정 트랜잭션에서 `OutboxEvent(CHAT_ROOM_CREATION_REQUESTED, PENDING)`를 Payment·Reservation·ReservationParticipant와 함께 저장한다. 커밋 뒤 즉시 signal은 fast path이고, scheduler가 due `PENDING`과 5분 stale `PROCESSING`을 보정한다. Processor는 조건부 `PENDING → PROCESSING` claim만 짧은 트랜잭션에서 수행하고, ChatRoom 생성은 잠금 밖의 별도 트랜잭션에서 `createIfAbsent(reservationId)`로 처리한다.

최초 처리 실패 뒤 1·2·4·8·16초 backoff로 5회 재시도하고, 그 다음(여섯 번째) 실패에서 `FAILED`로 남긴다. `FAILED`는 운영 확인 후 `PENDING`으로 안전하게 재등록할 수 있으나, 이번 범위에서 UI/API는 만들지 않는다.

## 선택 이유

DB Outbox는 핵심 데이터와 생성 의도를 원자적으로 보관하면서도 ChatRoom 저장 실패를 핵심 결제 트랜잭션에서 분리한다. at-least-once 전달을 전제로 하며, `chat_room.reservation_id` UNIQUE와 `createIfAbsent`가 중복 부작용을 막는다.

## 대안과 제외

- `AFTER_COMMIT`만 유지: 커밋 뒤 프로세스 종료 유실을 복구할 수 없다.
- 핵심 트랜잭션 안에서 ChatRoom 저장: ChatRoom 저장 실패가 Payment·Reservation을 롤백시킨다.
- Kafka/RabbitMQ·범용 Outbox Framework: 현재 단일 ChatRoom 후속 처리에는 운영·구현 비용이 과도해 별도 요구가 생길 때 검토한다.

## 검증 방법

대표 Before/After 실패 경계와 Outbox 원자 저장·롤백, PENDING 처리, 멱등성, 5회 재시도 후 FAILED, 단일 Claim, stale 회수를 자동 테스트와 `docs/evidence/v3/176-chatroom-outbox/README.md`로 확인한다.

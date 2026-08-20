# ADR 0010: ChatMessage → AI Moderation Outbox + Kafka 전달 파이프라인

- 상태: `Accepted`
- 작성일: `2026-08-11`
- 관련 Issue·PR: #59 (선행: #66, #176, #183)

## 배경

#66은 AI Moderation Core(`ChatModerationService.analyze/recordFinalFailure`)를 완성했지만, ChatMessage 생성 시 이를 자동으로 호출하는 경로는 없었다. 채팅 저장 트랜잭션 안에서 OpenAI를 동기 호출하면 AI 지연·장애가 실시간 채팅에 그대로 전파된다. 반대로 `AFTER_COMMIT`으로 Kafka에 직접 발행만 하면, DB 커밋과 Kafka 발행 사이에 프로세스가 죽거나 Kafka가 장애나는 순간 그 메시지의 AI 분석 의도가 영구히 사라질 수 있다.

## 고려한 대안

1. 동기 AI 호출: AI가 느리거나 실패하면 채팅 전송도 같이 느려지거나 실패한다.
2. `AFTER_COMMIT` + Kafka 직접 발행: DB→Broker 사이 유실 구간이 남는다.
3. Transactional Outbox만 사용(#176/#183 방식): AI 분석은 외부 API 실패 가능성·독립 Consumer·Retry/DLT·재처리 요구가 이메일/ChatRoom 생성보다 크다.
4. Kafka만 사용(Outbox 없음): 대안 2와 동일한 유실 구간이 남는다.
5. **Transactional Outbox + Kafka(채택)**: DB 커밋 뒤 Kafka 발행 전 장애는 Outbox가 다시 발행할 근거를 남기고, Kafka에 들어간 뒤 AI 처리 실패는 Kafka Retry/DLT가 다시 시도하거나 격리한다.

## 결정

`ChatMessage` 저장과 같은 트랜잭션에서 `OutboxEvent(CHAT_MESSAGE_CREATED)`를 저장한다(#176/#183 공통 Outbox 최소 확장, 새 컬럼 없음). `ChatMessageOutboxProcessor`가 커밋 후 신호를 받아 Kafka에 발행하고, Broker ACK 후에만 Outbox를 COMPLETED로 표시한다. `ChatModerationConsumer`는 `ChatModerationService.analyze(messageId)`만 호출하며, Kafka Consumer 재시도 소진 후에만 DLT로 이동시키고 `recordFinalFailure`를 호출한다.

## 선택 이유 — 두 개의 서로 다른 실패 구간

- **Outbox**: DB 커밋 ↔ Kafka 발행 사이의 실패를 책임진다. 발행 실패는 기존 Outbox backoff(최대 5회)로 다시 시도하고, 채팅 저장·실시간 전달은 이 실패 때문에 되돌리지 않는다.
- **Kafka Retry/DLT**: 이미 Broker에 들어간 이벤트를 Consumer가 처리(AI 호출)하다 실패하는 구간을 책임진다. 최초 처리 포함 최대 3회(Human 결정 Q1) 재시도 후 DLT로 격리한다.

이 둘을 하나로 합치지 않는 이유는, Outbox 발행 실패는 "아직 Kafka에 들어가지도 못한 실패"라 Kafka DLT로 보낼 수 없고, Consumer 실패는 "이미 들어간 이벤트의 처리 실패"라 Outbox 재시도로 되돌릴 대상이 아니기 때문이다.

## AI Retry와 환불 Reconciliation의 차이

AI 분석 timeout은 "이번 시도가 실패했다"는 의미가 명확해 그대로 재시도해도 안전하다. 반대로 PortOne 환불 timeout은 외부에서 이미 처리됐을 가능성이 있어, 같은 금전 명령을 무조건 재시도하면 중복 환불로 이어질 수 있다. 그래서 환불은 Kafka Retry로 전환하지 않고 기존 외부 멱등성 + Reconciliation을 유지한다.

## ChatRoom/Email Outbox-only와의 차이

ChatRoom 생성, 이메일 발송은 내부 DB Processor만으로 충분하다(독립 Consumer·Retry/DLT·확장 요구가 크지 않음). AI 분석은 느리고 실패 가능성이 높은 외부 API 호출이라, 실패한 이벤트를 따로 재시도하고 나중에 독립 Worker로 분리할 수 있는 경계(#192)가 필요해 Kafka까지 더한다.

## 장점

- DB 커밋 뒤 Kafka 발행 전 장애가 나도 다시 발행할 근거를 남기며, 기존 Outbox 구조를 재사용한다(신규 컬럼 없음).
- Kafka Consumer 장애가 채팅 저장·실시간 전달에 영향을 주지 않는다.
- `ChatModerationService`의 `isCompleted()` 단락과 `chat_moderation` UNIQUE 제약으로 at-least-once 중복 수신에도 AI 중복 호출·중복 결과가 생기지 않는다(Testcontainers 통합 테스트로 확인).
- Spring AI 내부 `max-attempts=1`(#66)과 Kafka Retry(3회)를 분리해 재시도가 곱해지는 숨은 증폭을 막는다.

## 단점과 위험

- 로컬/CI에 Kafka 운영 부담이 추가된다(docker-compose Kafka 서비스, Testcontainers).
- 같은 이벤트가 한 번 이상 전달될 수 있는 구조(at-least-once)이므로, 외부 시스템까지 포함해 정확히 한 번만 처리된다고 말하지 않는다. 대신 Consumer가 `messageId` 기준으로 중복 결과를 만들지 않게 보완한다.
- Partition 수·Consumer 확장은 #192에서 별도로 측정한다(이번 범위 아님).

## 검증 방법

Testcontainers 기반 통합 테스트로 실제 broker에 대해 다음을 확인했다: (1) 정상 발행 후 Outbox COMPLETED, (2) 발행 실패 시 backoff 후 재시도 성공, (3) 동일 messageId 중복 수신 시 AI 호출·결과 저장 1회만 발생, (4) 일시적 AI 실패 후 재시도로 성공, (5) 반복 실패 시 DLT 이동 + `recordFinalFailure` 호출로 `ANALYSIS_FAILED` 기록, (6) 잘못된 eventVersion은 AI를 호출하지 않고 즉시 최종 실패 처리. 상세 실행 근거는 [#59 Evidence](../evidence/v3/59-kafka-ai-pipeline/README.md)를 따른다.

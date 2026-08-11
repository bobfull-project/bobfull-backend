# Issue #59 — Outbox + Kafka AI Moderation Pipeline Evidence

## 검증 대상

ChatMessage 생성 → `OutboxEvent(CHAT_MESSAGE_CREATED)` → Kafka → `ChatModerationConsumer` → `ChatModerationService.analyze/recordFinalFailure`(#66) 전달 파이프라인. Baseline: `0850f63`(origin/develop, #59 착수 시점).

## 실행 근거

모든 테스트는 실제 Testcontainers Kafka broker(`confluentinc/cp-kafka:7.7.1`) 대상으로 실행했다. 실행 명령: `./gradlew :test --tests "com.bobfull.outbox.service.ChatMessageOutboxProcessorIntegrationTest" --tests "com.bobfull.kafka.consumer.ChatModerationConsumerIntegrationTest"`.

### `ChatMessageOutboxProcessorIntegrationTest` (Outbox → Kafka, Scenario A/B)

| 테스트 | 결과 |
|---|---|
| PENDING 이벤트를 처리하면 실제 broker에 발행하고 COMPLETED로 기록한다 | PASS |
| 발행 실패는 backoff 후 재시도해 복구되면 실제 broker에 발행하고 COMPLETED로 기록한다 | PASS |

두 번째 테스트는 `KafkaOperations.send`를 강제로 1회 실패시킨 뒤(Outbox PENDING·attemptCount=1 확인), 같은 이벤트를 backoff 이후 재처리해 실제 broker에 발행되고 COMPLETED로 전환됨을 확인했다. 발행된 payload에 `content` 필드가 없음을 확인했다(payload 최소화).

### `ChatModerationConsumerIntegrationTest` (Kafka Consumer, Scenario C/D/E + 계약 위반)

| 테스트 | 결과 |
|---|---|
| 동일 messageId 중복 수신에도 AI 호출과 결과 저장은 한번만 일어난다 | PASS |
| 일시 실패 후 Retry로 성공하면 ANALYSIS_FAILED 없이 SAFE/FLAGGED가 저장된다 | PASS |
| 반복 실패는 DLT로 이동하고 recordFinalFailure로 ANALYSIS_FAILED가 기록된다 | PASS |
| 잘못된 eventVersion은 AI를 호출하지 않고 바로 DLT 경로로 최종 실패를 기록한다 | PASS |

동일 이벤트를 두 번 발행했을 때 `AiModerationPort` 호출 수는 1회로 확인했다(`ChatModerationService.analyze()`의 `isCompleted()` 단락 + `chat_moderation` UNIQUE 제약). Q1 결정(최대 3회)에 맞춰 `FixedBackOff(200ms, 2)`로 구성했고, 반복 실패 케이스에서 AI 호출 수가 정확히 3회임을 확인했다. eventVersion 불일치 케이스는 AI 호출 수 0회로, `analyze()` 진입 전 리스너 단계에서 즉시 최종 실패로 격리됨을 확인했다.

### 전체 회귀

`./gradlew :test`(프로젝트 전체) 통과. 기존 outbox/chat/moderation 관련 테스트에 회귀 없음.

## 한계

- 실제 프로세스 kill을 통한 "ACK 후 완료 저장 전 종료" 재현은 하지 않았다. 대신 동일 이벤트 재발행 시나리오로 그 결과(중복 소비에도 안전함)를 검증했다.
- Consumer Lag·처리량 등 대규모 처리 성능은 측정하지 않았다(#192 범위).
- 실제 OpenAI 호출 품질은 이번 검증 대상이 아니다(#66에서 별도 검증). 이 파이프라인 테스트는 `AiModerationPort`를 Fake로 대체해 결정적으로 검증했다.
- 로컬 단일 broker/단일 파티션 그룹 기준이며, 운영 Kafka Cluster HA는 검증하지 않았다.

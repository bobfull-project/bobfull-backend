# ADR 0010: ChatMessage → AI Moderation Outbox + Kafka 전달 파이프라인

- 상태: `Accepted`
- 작성일: `2026-08-11`
- 최종 보강일: `2026-08-18`
- 관련 Issue·PR: #59, #192, #258, #274
- 주요 Evidence: `docs/evidence/v3/59-kafka-ai-pipeline/README.md`, `docs/evidence/v3/192-ai-worker-scaling/README.md`, `docs/evidence/v3/258-moderation-partition-key/README.md`, `docs/evidence/v3/274-outbox-async-vs-kafka/README.md`

## 배경

#66은 AI Moderation Core(`ChatModerationService.analyze/recordFinalFailure`)를 제공하지만, ChatMessage 생성 시 외부 AI 분석을 동기 호출하면 AI 지연·장애가 채팅 저장 경로에 전파된다. 반대로 `AFTER_COMMIT`에서 Kafka를 직접 발행하면 DB Commit과 Broker publish 사이의 작업 의도가 영속적으로 남지 않는 구간이 생긴다.

BobFull은 이미 ChatRoom·Email 후속 작업에 Transactional Outbox를 사용하고 있었으므로, AI 분석에서도 DB와 Broker 사이의 전달 의도는 Outbox가 책임지고, Broker 이후의 독립 Consumer 처리 경계는 Kafka가 책임지도록 분리했다.

## 고려한 대안

1. **동기 AI 호출** — 구현은 단순하지만 외부 AI latency·timeout·장애가 채팅 요청 경로에 직접 영향을 준다.
2. **`AFTER_COMMIT` + Kafka 직접 발행** — 요청 경로는 분리되지만 DB Commit과 Kafka publish 사이의 작업 의도를 재시작 후 복구할 영속 근거가 없다.
3. **Transactional Outbox + Local Async** — 작업 의도 내구성과 재시작 복구는 확보할 수 있고, 로컬 처리 성능도 단순하다.
4. **Kafka only(Outbox 없음)** — DB와 Broker 사이 dual-write 경계가 남는다.
5. **Transactional Outbox + Kafka** — DB→Broker는 Outbox, Broker 이후 AI 처리 실패는 Kafka Consumer Retry/DLT가 담당한다.

## 결정

`ChatMessage`와 `OutboxEvent(CHAT_MESSAGE_CREATED)`를 같은 DB 트랜잭션에 저장한다. `ChatMessageOutboxProcessor`는 Outbox를 claim한 뒤 Kafka에 발행하고 Broker ACK를 받은 뒤 Outbox를 `COMPLETED`로 전이한다.

Kafka Consumer는 `ChatModerationService.analyze(messageId)`를 호출한다. AI 처리 실패는 Kafka Consumer가 최초 처리 포함 최대 3회 재시도하고, 재시도 소진 후 DLT로 격리한 뒤 `recordFinalFailure`를 호출한다. Spring AI 내부 Retry는 `max-attempts=1`로 두어 Kafka Retry와 중첩하지 않는다.

현재 Moderation은 메시지 단위 독립 작업이며, #258 실측을 반영해 Production Kafka key는 `messageId`를 사용한다. 채팅방 전체 Moderation 완료 순서를 보장하기 위해 `chatRoomId` affinity를 유지하지 않는다.

## #274 동일 Outbox 조건 재검증

초기 #192 비교는 Memory Async와 Outbox+Kafka를 비교해 Outbox의 durability와 Kafka의 효과가 함께 섞여 있었다. #274에서는 양쪽 모두 Transactional Outbox를 공통 전제로 두고 Outbox 이후 전달 경계만 통제 비교했다.

조건: 메시지 30건, Fake AI 500ms, worker/consumer concurrency 3, Kafka partition 3, key=`messageId`.

| 지표 | Outbox + Async | Outbox + Kafka |
|---|---:|---:|
| Drain median | **5.394s** | **7.210s** |
| Throughput median | **5.56 msg/s** | **4.16 msg/s** |
| 정상 실행 lost / duplicate | 0 / 0 | 0 / 0 |
| 실제 process crash 후 lost / duplicate | 0 / 0 | 0 / 0 |
| restart → 처리 재개 | 296.825s | 40.614s |
| restart → 전체 완료 | 301.041s | 47.035s |

이 조건에서는 Outbox+Async가 더 빨랐고, 양쪽 모두 Outbox 덕분에 실제 프로세스 강제 종료 뒤 최종 `lost=0`, `duplicate=0`으로 복구됐다. 따라서 **Kafka가 더 빠르다**, **Kafka만이 작업 유실을 막는다**는 설명은 채택 근거가 아니다.

Async의 재개는 `PROCESSING` Outbox가 stale threshold에 도달한 뒤 scheduler가 reclaim하는 경계였고, Kafka는 Broker backlog와 Consumer Group 재연결 경계에서 이어서 처리했다. 위 복구 시간은 Spring 재기동 등 전체 경로를 포함한 실험값이며 실제 AWS 운영 복구시간으로 일반화하지 않는다.

## 선택 이유

Kafka를 유지하는 이유는 단건 속도가 아니라 AI 후속 작업을 **독립적으로 운영 가능한 처리 경계**로 분리하기 위해서다.

- Broker backlog와 Consumer Group으로 Core/Web과 Consumer 처리 속도를 분리한다.
- Consumer Lag과 Partition 분포를 독립적으로 관찰할 수 있다.
- Consumer 처리 실패를 Retry/DLT 경계로 격리한다.
- 같은 코드베이스에서도 향후 필요 시 Consumer 실행 단위를 독립 Worker로 확장할 수 있다.
- Outbox가 DB→Broker 전달 의도를 보존하고 Kafka가 Consumer-side 처리 경계를 담당해 실패 책임이 겹치지 않는다.

## Kafka 적용 범위

Kafka를 프로젝트의 모든 후속 작업에 공통 적용하지 않는다.

- **ChatRoom 생성**: Outbox + 내부 Processor로 현재 요구를 충족한다.
- **Email 발송**: Outbox + 수신자별 Delivery + 내부 Processor로 현재 요구를 충족한다.
- **결제·환불**: 금전 명령 timeout은 외부에서 이미 처리됐을 수 있으므로 Kafka Retry/DLT보다 외부 멱등성 + 상태 조회 + Reconciliation이 우선이다.
- **실시간 채팅 fan-out**: Kafka가 아니라 Redis Pub/Sub을 사용하며, DB cursor가 복구 기준이다.
- **AI Moderation / 같은 ChatMessageCreatedEvent를 소비하는 독립 AI 후속 처리**: Broker backlog, 독립 Consumer Group, Retry/DLT·확장 경계가 필요해 Kafka를 사용한다.

## 장점

- DB Commit과 Kafka publish 사이의 작업 의도를 Outbox로 보존한다.
- AI 장애와 적체가 ChatMessage 저장·실시간 채팅을 롤백하지 않는다.
- Kafka Consumer 실패를 Retry/DLT로 격리할 수 있다.
- at-least-once 중복 전달을 `messageId` 멱등 처리로 수용한다.
- Web/Core와 AI Consumer의 운영·확장 기준을 분리할 수 있다.

## 단점과 위험

- Kafka Broker·Topic·Consumer·Retry/DLT를 운영해야 해 Outbox+Async보다 복잡하다.
- 동일 workload의 단순 처리 성능은 Local Async보다 느릴 수 있다.
- Kafka Broker HA는 현재 단일 전용 EC2/KRaft broker 수준이며 전체 메시징 계층 HA를 보장하지 않는다.
- DLT에 격리된 이벤트를 자동으로 다시 꺼내는 관리자 Replay 도구는 현재 범위에 없다.
- 실제 Provider 429·대규모 AWS workload에서의 Worker Scale-out 효과는 별도 운영 지표가 필요하다.

## 검증 방법

- #59: Kafka publish, Consumer Retry/DLT, 중복 이벤트 멱등성 통합 검증
- #192: AI 지연이 send 경로에 직접 전파되지 않는지, Consumer 중단·복구, Consumer 병렬 처리 실험
- #258: `chatRoomId` Hot-Key 문제와 `messageId` Partition Key 분산 검증
- #274: 동일 Transactional Outbox 조건의 Async/Kafka 성능·실제 process crash 복구 통제 비교

## 재검토 조건

- AI 후속 작업량이 현재보다 충분히 작아 Kafka 운영 복잡도가 실질적 비용으로 커질 때
- 실제 운영에서 Consumer Lag·DLT·독립 Worker 확장 요구가 사라질 때
- Kafka Broker HA 또는 관리형 Kafka 도입 필요성이 생길 때
- 다른 후속 작업에 독립 Consumer·적체·재처리 요구가 실제로 생겨 Kafka 적용 범위를 확대해야 할 때

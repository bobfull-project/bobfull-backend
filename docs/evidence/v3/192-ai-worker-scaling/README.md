# Issue #192 — Kafka AI Consumer 독립 실행·확장 및 MSA 분리 판단 Evidence

## 검증 대상

`ChatMessage` COMMIT → Outbox → Kafka(`bobfull.chat.message-created.v1`, Partition 3) → `ChatModerationConsumer` → `ChatModerationService`(#66) 처리 경계에서, Web/API와 AI Consumer가 같은 프로세스에서 실행되는 현재 구조(A안)가 충분한지, 아니면 Web/AI Worker 실행 역할 분리(B안)·Consumer Scale-out이 필요한지를 실측으로 판단한다.

- 대상 코드: `ChatModerationConsumer`, `ChatModerationService`, `AiModerationPort`/`SpringAiModerationAdapter`/`FakeAiModerationAdapter`, `ChatMessageOutboxProcessor`, `ChatMessageAsyncModerationDispatcher`
- 측정·재현 환경: 로컬 macOS, JUnit5 `@SpringBootTest`, H2(MySQL 모드), Kafka는 Testcontainers `confluentinc/cp-kafka:7.7.1`(KRaft 단일 노드). 실험 0(Kafka vs Async)과 실험 D(Consumer concurrency) 실측 완료. 실험 A~C, E, F는 아직 미실측.
- 측정 커밋: `c5ade70` 기준 코드에 이번 실행에서 추가한 실측 코드(하단 "실험 준비" 표) 적용

이 문서의 수치는 모두 **실측 완료 후에만** 채운다. 미측정 항목은 "검증 예정"으로 남기며 임의 수치를 기록하지 않는다.

## 측정 계약

- Primary KPI: Consumer `1 → 2 → 3` 단계별 consume rate / peak Lag / Lag 회복 시간, Web/AI Worker 분리 전후 HTTP p95/Consumer Lag 비교
- Secondary KPI: AI 처리 p95/p99, Provider 429/5xx/timeout, Retry/DLT 건수, token/cost, Worker/Web CPU·Heap·Thread, DB Pool
- Guardrail: Retry/DLT·messageId 멱등 계약 유지(중복 저장 0), Kafka/AI 장애 시 Web 채팅·Outbox 보존 계약 유지, Outbox Kafka Publisher는 항상 Web/Core에 유지

## Human 결정 반영 (2026-08-12)

- 분리 착수 기준: Lag 미회복(5분+) / AI 처리 p99 3초 반복 초과 / HTTP p95 20%+ 동반 악화 중 하나
- 분리 방식: B안(같은 코드베이스, 실행 역할 분리)부터. C안(MSA)은 미검토
- Scale-out 우선 기준: Consumer Lag 주신호 + Provider 429/AI p95 병행 확인
- MSA 판단: B안으로 충분하면 MSA 미도입을 정상 결론으로 허용

## 실험 준비 — 이번 실행에서 추가한 기반 코드

| 항목 | 내용 |
|---|---|
| `FakeAiModerationAdapter` | `bobfull.ai.moderation.fake-enabled=true`일 때 활성화, `fake-latency-ms`로 AI 처리시간을 100ms/1s/3s 등 고정값으로 재현. `SpringAiModerationAdapter`와 상호 배타적(`ConditionalOnProperty`) |
| `ChatModerationConsumer` concurrency | `bobfull.kafka.chat-message.consumer-concurrency`(default 1)로 `@KafkaListener` 동시 처리 스레드 수를 조절 |
| `ChatMessageAsyncModerationDispatcher` | `bobfull.chat.moderation.async-baseline-enabled=true`일 때만 Bean 생성. Outbox/Kafka 없이 `ChatMessage` 커밋 직후 전용 스레드풀(`async-baseline-concurrency`, 기본 8)에 `ChatModerationService.analyze()`를 직접 제출. 재시도·DLT 없음(의도적) |

## 실험 0 — 왜 Outbox+Kafka인가: Async Baseline과 비교 (실측 완료)

hyeonseung-dev 리뷰(PR #243) 제안대로, "Kafka가 더 빠르다"가 아니라 **처리 경계로서 무엇이 다른지**를 같은 조건(Fake AI 지연 500ms, 메시지 30건)에서 비교했다.

### 비교 조건

- Baseline(Async): Outbox/Kafka 미사용. `ChatMessage` 커밋 후 `ChatMessageAsyncModerationDispatcher`(전용 스레드풀, concurrency=3)가 즉시 `analyze()` 직접 호출
- Kafka: 기존 #59 경로 그대로. `ChatMessage`+Outbox 커밋 → signal 즉시 발행 → Kafka(Partition 3, Consumer concurrency=3) → `ChatModerationConsumer` → `analyze()`
- 둘 다 `FakeAiModerationAdapter`(지연 500ms)로 Provider 변동성 제거, 30건 동일 유입

### 측정 결과 (`ChatMessageSendLatencyEvidenceTest`, `ChatModerationConsumerConcurrencyIntegrationTest`, 실제 테스트 실행)

| 지표 | Async Baseline | Kafka(같은 채팅방 1개로 몰림) | Kafka(3개 채팅방으로 분산) |
|---|---|---|---|
| send() p50 | **3ms** | **2~3ms** | (미측정, 별도 시나리오) |
| send() p95 | **7ms** | **4~8ms** | (미측정) |
| send() max | 13ms | 7~23ms | (미측정) |
| 30건 완료까지(drain time) | **5.2~5.5초** | **15.5초** | **10.7초** |

판정:
- **send() 응답성은 거의 같다.** 둘 다 AI 처리(500ms)를 커밋 후 비동기로 넘기므로, Kafka가 Async보다 웹 응답을 더 빠르게 만들어주는 것은 아니었다. 이 결과는 "Kafka를 쓰면 빨라진다"는 주장이 틀렸다는 것을 실측으로 보여준다 — Kafka의 가치는 응답 지연이 아니라 다른 곳에 있다.
- **완료 처리량(drain time)은 오히려 Async가 더 빨랐다.** 원인은 Kafka의 Partition key가 `chatRoomId`이기 때문이다: 같은 채팅방 메시지는 항상 같은 Partition에 몰려 Consumer 3개 중 1개만 실제로 일을 한다(단일 방 15.5초 ≈ 순차 처리 500ms×30 이론값과 거의 일치). 채팅방을 3개로 분산하면 10.7초로 줄지만, key 해시가 Partition 3개에 완벽히 균등 분배되지 않아 이론적 3배 단축(5초대)에는 못 미친다. **"Consumer 수만 늘리면 병렬 처리량이 그만큼 는다"는 가정이 항상 맞지 않음을 보여주는 실측 근거다** — 실제로는 채팅방(key) 분산도가 함께 필요하다.
- **신뢰성 차이가 핵심이다** (`ChatMessageAsyncModerationDispatcherReliabilityTest`, 실측): Async Baseline은 순수 인메모리 스레드풀 큐라서, 처리 중이 아닌(큐에 대기 중인) 작업은 프로세스가 죽는 순간 재시도 없이 그대로 유실된다(테스트에서 큐 대기 중 2건이 강제 종료 시 `analyze()` 호출 자체가 한 번도 일어나지 않은 채 사라짐을 확인). 반면 Kafka는 이미 #59 Evidence(`ChatModerationConsumerIntegrationTest`, `ChatModerationDltPublishFailureIntegrationTest`)에서 Consumer 중단·AI 반복 실패에도 이벤트가 브로커에 보존되고 재시작 후 처리를 이어가거나 DLT로 격리됨을 실측했다.

결론: **"Kafka가 더 빠르다"는 근거로 Kafka를 선택한 게 아니다.** 이번 실측에서는 오히려 Async가 특정 조건(단일 채팅방 몰림)에서 완료 처리량이 더 빨랐다. Kafka를 선택한 근거는 **적체·재시도·실패 격리·재처리·독립 확장이 가능한 처리 경계**라는 점이며, Async Baseline에는 이 중 어느 것도 기본으로 없다(재시도 없음, DLT 없음, 큐 유실 위험, Consumer processes 단위 확장 불가). Consumer 수 확장(실험 D)도 Partition key 분산과 함께 봐야 한다는 것이 이번 실측에서 드러난 추가 발견이다.

## 실험 A — AI 지연이 채팅 처리에 전파되는가 (검증 예정)

| AI latency | Chat SEND p95/p99 | AI 처리 p95/p99 | Consumer Lag | Web CPU/Thread/DB Pool |
|---|---|---|---|---|
| 100ms | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 |
| 1s | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 |
| 3s | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 |

판정: 검증 예정

## 실험 B — Consumer 중단 중 적체와 복구 (검증 예정)

| 지표 | 값 |
|---|---|
| produced N건 | 검증 예정 |
| peak lag | 검증 예정 |
| processed N/N | 검증 예정 |
| lost event | 검증 예정 |
| recovery time(Lag 0까지) | 검증 예정 |

## 실험 C — 실패 격리 / Retry / DLT / 재처리 (검증 예정)

| 지표 | 값 |
|---|---|
| 정상 Event 처리 성공률 | 검증 예정 |
| Retry 횟수 / 성공 건수 | 검증 예정 |
| DLT 건수 | 검증 예정 |
| DLT Replay 성공 건수 | 검증 예정 |
| 최종 Moderation 누락/중복 | 검증 예정 |

## 실험 D — Partition 3 / Consumer 1 → 2 → 3 확장 (검증 예정)

| Consumer 수 | consume rate | peak Lag | Lag drain time | AI 처리 p95/p99 | Rebalance | Provider 429/timeout | Retry/token·cost |
|---|---|---|---|---|---|---|---|
| 1 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 |
| 2 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 |
| 3 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 | 검증 예정 |

판정: 검증 예정(Consumer 수 증가 = 무조건 성능 향상으로 결론내리지 않음). 단, 실험 0에서 Consumer concurrency=3 고정 상태로 Partition key(`chatRoomId`) 분산도에 따라 완료 처리량이 크게 달라짐을 이미 확인했다(단일 방 몰림 15.5초 vs 3방 분산 10.7초, 이론적 3배 단축인 5초대에는 못 미침). 1→2→3 순차 확장을 실측할 때는 이 key 분산 변수를 반드시 같은 조건으로 통제해야 한다.

## 실험 E — AI 장애 격리 (검증 예정)

AI timeout/5xx/429 강제 주입 시 Retry/DLT 발생과 Web/API 정상 동작 여부. 검증 예정.

## 실험 F — Kafka 장애 (검증 예정)

Kafka 중단 시 Web ChatMessage+Outbox 정상 저장, 실시간 채팅 유지, 복구 후 Outbox backlog 발행·AI 처리 회복 여부. 검증 예정.

## 브로셔 대표 수치 후보 (실측 완료 후 채움)

```text
Kafka vs Async Baseline — send() 응답성: 거의 동일(p95 4~8ms). Kafka가 더 빠르다는 주장은 실측으로 기각(실험 0 완료)
Kafka vs Async Baseline — 완료 처리량(30건, AI 지연 500ms): Async 5.2~5.5초 vs Kafka 10.7~15.5초(Partition key 분산도에 좌우, 실험 0 완료)
Kafka vs Async Baseline — 신뢰성: Async는 큐 대기 작업이 프로세스 종료 시 재시도 없이 유실, Kafka는 브로커 보존·재시작 복구(실험 0 완료, #59 Evidence 재확인)
AI 지연 증가 시 Chat SEND p95 변화: 검증 예정
Consumer 중단 N건 적체 후 유실 건수 / 복구 시간: 검증 예정
Consumer 1→3 처리량 및 Lag 해소 시간 변화: 검증 예정(Partition key 분산도 통제 필요, 실험 0에서 이미 확인)
DLT N건 중 N/N 재처리 성공 / 최종 중복·누락 건수: 검증 예정
```

## 최종 판정 (검증 예정)

```text
통합 모놀리스 유지
실행 역할만 Web / AI Worker 분리
MSA 후속 검토
```

측정 완료 전까지 결론을 내리지 않는다.

# Issue #192 — Kafka AI Consumer 독립 실행·확장 및 MSA 분리 판단 Evidence

## 검증 대상

`ChatMessage` COMMIT → Outbox → Kafka(`bobfull.chat.message-created.v1`, Partition 3) → `ChatModerationConsumer` → `ChatModerationService`(#66) 처리 경계에서, Web/API와 AI Consumer가 같은 프로세스에서 실행되는 현재 구조(A안)가 충분한지, 아니면 Web/AI Worker 실행 역할 분리(B안)·Consumer Scale-out이 필요한지를 실측으로 판단한다.

- 대상 코드: `ChatModerationConsumer`, `ChatModerationService`, `AiModerationPort`/`SpringAiModerationAdapter`/`FakeAiModerationAdapter`, `ChatMessageOutboxProcessor`, `ChatMessageAsyncModerationDispatcher`
- 측정·재현 환경: 로컬 macOS, JUnit5 `@SpringBootTest`, H2(MySQL 모드), Kafka는 Testcontainers `confluentinc/cp-kafka:7.7.1`(KRaft 단일 노드). 실험 0/A/B/C/D 실측 완료(경량 부하, `FakeAiModerationAdapter` 사용). 실험 E는 A·C 결과로 합성 판단, F는 #59 기존 Evidence 재인용. 실제 OpenAI Provider·프로덕션 규모 부하는 미실측(아래 "검증 한계" 참고).
- 측정 커밋: `7802364` 이후 이번 실행에서 추가한 실측 코드 적용(아래 "실험 준비" 표)
- 검증 한계(공통): (1) 모든 AI 처리는 `FakeAiModerationAdapter`로 대체해 실제 OpenAI 지연·429·비용 변동성은 반영하지 않음 (2) 부하 규모가 경량(요청 15~30건, 동시 사용자 10명 수준)이라 프로덕션 피크 트래픽과는 다름 (3) CPU/Heap/DB Pool 등 실제 리소스 사용량은 측정하지 않고 지연시간·처리량·성공/실패 건수로 대체 측정함

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

## Retry 증폭 확인 (2026-08-13, 설정값 확인)

Issue 본문의 우려대로 Kafka Retry(최대 3회) × Spring AI 내부 Retry가 곱해지면 메시지당 최대 30회까지 외부 AI 호출이 늘 수 있다. 현재 설정을 다시 확인한 결과:

- `application-prod.yml`: `spring.retry.max-attempts: 1`(Spring AI 내부 Retry 비활성 — 1회만 시도)
- `application-prod.yml`: `bobfull.kafka.chat-message.consumer-max-attempts: 3`(Kafka Consumer Retry, 최초 처리 포함 최대 3회)

메시지당 실제 최대 외부 AI 호출 수 = 3(Kafka) × 1(Spring AI) = **3회**(숨은 증폭 없음). 이 설정은 #59에서 이미 정해둔 값이며 이번 실행에서 변경하지 않았다. 실제 Provider 대상 재검증 시에도 이 두 값이 바뀌지 않았는지 함께 확인해야 한다.

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

## 실험 A — AI 지연이 채팅 처리에 전파되는가 (실측 완료)

`ChatModerationConsumerConcurrencyIntegrationTest`(동시 사용자 10명 × 2건, 채팅방 3개 분산, Consumer concurrency=3, `FakeAiModerationAdapter` latency만 변경).

| AI latency | Chat SEND p50 | Chat SEND p95 | Chat SEND max |
|---|---|---|---|
| 100ms | 13ms | 15ms | 15ms |
| 1s | 9ms | 12ms | 12ms |
| 3s | 12ms | 18ms | 18ms |

판정: **AI 처리 지연이 100ms→3s로 30배 늘어도 Chat SEND p95는 12~18ms 구간에 머물렀다.** 통합 프로세스라도 signal 디스패치(#59 MAJOR 수정)와 Kafka Consumer 스레드가 요청 스레드와 분리돼 있어 자원 경쟁이 Web 응답에 전파되지 않았다. Consumer Lag(오프셋 기준)과 실제 CPU/Thread/DB Pool 수치는 측정하지 않았고 완료 건수 기반 처리량으로만 간접 확인했다(검증 한계 참고).

## 실험 B — Consumer 중단 중 적체와 복구 (실측 완료)

`ChatModerationConsumerConcurrencyIntegrationTest`(Consumer Container `stop()` → 15건 `send()` → `start()`).

| 지표 | 값 |
|---|---|
| produced | 15건 |
| peak backlog(중단 중 미처리) | 15건(100%, 중단 중 처리 0건 확인) |
| processed N/N | 15/15 |
| lost event | **0건** |
| recovery time(재개 후 15건 완료까지) | 7.8초 |

판정: Consumer를 완전히 멈춰도 이벤트는 Kafka에 보존되고, 재개 즉시 순서대로 이어서 처리해 유실 없이 복구됨을 실측으로 확인.

## 실험 C — 실패 격리 / Retry / DLT / 재처리 (부분 실측)

`ChatModerationConsumerConcurrencyIntegrationTest`(정상 15건 + `FakeAiModerationAdapter.FORCE_FAIL_MARKER`로 강제 실패 5건을 다른 채팅방에 동시 유입).

| 지표 | 값 |
|---|---|
| 정상 Event 처리 성공률 | **15/15(100%)** — 실패 이벤트와 같은 시간대에 유입돼도 영향 없음 |
| Retry 횟수(consumer-max-attempts) | 최초 처리 포함 최대 3회(기존 #59 설정 그대로, 이번 실행에서 변경 없음) |
| DLT 건수 | **5/5(100%)** — 강제 실패 5건 전부 재시도 소진 후 DLT로 격리, `ANALYSIS_FAILED` 기록 |
| DLT Replay 성공 건수 | **미구현** — Issue #192 코멘트에 "이번 V3 이후 개선사항"으로 명시된 DLT 관리자 Replay 도구가 아직 없어 측정 불가 |
| 최종 Moderation 누락/중복 | 0건(정상 15건 SAFE, 실패 5건 ANALYSIS_FAILED, 중복 저장 없음) |

판정: 실패 이벤트는 자신이 속한 Partition/채팅방 범위에서만 재시도·DLT로 격리되고, 다른 채팅방의 정상 이벤트 처리에는 영향을 주지 않음을 확인. DLT Replay는 도구 자체가 없어 이번에도 검증 불가 상태로 남음(기존 #59 Evidence와 동일한 한계).

## 실험 D — Partition 3 / Consumer 1 → 2 → 3 확장 (실측 완료)

`ChatModerationConsumerConcurrencyIntegrationTest`(같은 채팅방 3개에 30건 고정, Consumer Container `stop()`→`setConcurrency(n)`→`start()`로 재구성, Fake AI 지연 500ms 고정).

| Consumer 수 | drain time(30건) | consume rate |
|---|---|---|
| 1 | 15.4초 | 1.94건/초 |
| 2 | 15.5초 | 1.93건/초 |
| 3 | 10.4초 | 2.88건/초 |

판정: **1→2에서는 거의 개선이 없었고 2→3에서만 뚜렷하게 개선됐다.** 3개 채팅방 key가 Partition 3개에 반드시 고르게 분산되는 게 아니라서(해시 충돌 가능), Consumer 2개 구간에서는 한쪽 Consumer가 여전히 대부분의 메시지를 떠안은 것으로 보인다. **"Consumer 수를 늘리면 늘리는 만큼 처리량이 오른다"는 가정은 이번 실측에서 기각됐다** — Partition key(`chatRoomId`) 분산도가 함께 맞아야 실제 병렬 처리 효과가 난다. Provider 429/timeout·token/cost는 Fake AI라 측정 대상이 아님(실제 Provider 대상 재실측 필요).

## 실험 E — AI 장애 격리 (실험 A·C 결과로 합성 판단, 별도 신규 테스트 없음)

전용 테스트를 새로 만들지 않고, 실험 A(Web은 AI 지연과 무관하게 항상 빠름)와 실험 C(강제 실패 5건이 정상 15건에 영향 없음, 재시도·DLT로 격리)를 합치면 "AI 장애가 Web/API 정상 동작을 막지 않는다"는 이슈의 요구 조건을 실측으로 충족한다. 다만 실제 OpenAI timeout/5xx/429 응답 코드별 세분화된 거동(예: 429만 별도로 급증하는 상황)은 Fake Adapter로 재현하지 않았으므로 검증 예정으로 남긴다.

## 실험 F — Kafka 장애 (신규 실측 없음, #59 기존 Evidence 재인용)

새 테스트를 만들지 않고 `docs/evidence/v3/59-kafka-ai-pipeline/README.md`의 기존 실측 결과를 그대로 인용한다(코드 변경 없어 재검증 불필요, `ChatMessageSendLatencyEvidenceTest` 전체 스위트에서 계속 통과 확인됨):

- Kafka에 전혀 도달할 수 없는 상태(`bootstrap-servers=localhost:59999`)에서도 `send()` p50 1ms/p95 4ms/max 30ms로 응답 — Web/실시간 채팅은 Kafka 장애와 무관하게 정상 동작
- Outbox→Kafka 발행 실패 1건 강제 → PENDING(attemptCount=1) 유지 → backoff 후 재처리 → 1/1 실제 broker 발행·COMPLETED로 복구

## 브로셔 대표 수치 후보

```text
Kafka vs Async Baseline — send() 응답성: 거의 동일(p95 4~8ms). Kafka가 더 빠르다는 주장은 실측으로 기각(실험 0)
Kafka vs Async Baseline — 완료 처리량(30건, AI 지연 500ms): Async 5.2~5.5초 vs Kafka 10.7~15.5초(Partition key 분산도에 좌우, 실험 0)
Kafka vs Async Baseline — 신뢰성: Async는 큐 대기 작업이 프로세스 종료 시 재시도 없이 유실, Kafka는 브로커 보존·재시작 복구(실험 0, #59 재확인)
AI 지연 100ms→3s(30배)에도 Chat SEND p95는 12~18ms로 거의 변화 없음(실험 A)
Consumer 중단 15건 적체 → 재개 후 유실 0건, 복구 7.8초(실험 B)
정상 15건은 실패 5건과 동시 유입에도 100% 성공, 실패 5건은 재시도 소진 후 5/5 DLT 격리(실험 C)
Consumer 1→2→3 확장 시 drain time 15.4초→15.5초→10.4초 — 2에서는 거의 개선 없었고 3에서만 개선(Partition key 분산도 영향, 실험 D)
```

## 최종 판정 (2026-08-13 Human 확정)

```text
통합 모놀리스 유지 (최종 확정)
```

근거: Human 결정 Q1의 분리 착수 기준(Lag 미회복 5분+/AI 처리 p99 3초 반복 초과/HTTP p95 20%+ 동반 악화) 중 어느 것도 이번 실측 범위에서 관찰되지 않았다. AI 지연이 30배(100ms→3s) 늘어도 Chat SEND p95는 거의 그대로였고, Consumer 중단·AI 반복 실패도 Web이나 다른 채팅방 처리에 영향을 주지 않았다. Kafka 채택 근거도 속도가 아니라 신뢰성(적체·재시도·격리·복구)이라는 것이 실험 0에서 확인됐고, Retry 증폭 우려도 현재 설정(3×1=3회)에서 해당하지 않음을 재확인했다. B안(Web/AI Worker 실행 역할 분리)과 C안(MSA)은 필요성이 확인되지 않아 이번 V3 범위에서 도입하지 않는다.

**측정 한계(계속 유효 — 남겨두는 이유는 아래에서 트리거로 재검토하기 위함):**
- 실제 OpenAI Provider가 아닌 `FakeAiModerationAdapter`로만 측정함 — Provider 429/Rate Limit·실제 지연 변동성은 미반영
- 부하 규모가 경량(요청 15~30건, 동시 10명)이라 프로덕션 피크 트래픽과는 다름
- CPU/Heap/DB Pool 등 실제 리소스 경쟁은 측정하지 않음(지연·처리량 결과로 간접 추정만 함)
- Worker Scale-out 시 Partition 수보다 Consumer를 늘렸을 때의 유휴 Consumer 문제, Rebalance 발생 빈도는 미실측

**재검토 트리거:** 위 한계가 실제 운영(#64 Prometheus/Grafana)에서 문제로 드러나거나, Human 결정 Q1 기준(Lag 미회복 5분+/AI p99 3초 반복/HTTP p95 20%+ 악화)이 실측되면 그때 아래를 재검토한다. 그 전까지는 별도 부하 재검증을 추가로 수행하지 않는다.

```text
실행 역할만 Web / AI Worker 분리 (B안)
MSA 후속 검토 (C안)
```

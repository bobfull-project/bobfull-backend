# Issue #192 — Kafka AI Consumer 독립 실행·확장 및 MSA 분리 판단 Evidence

## 검증 대상

`ChatMessage` COMMIT → Outbox → Kafka(`bobfull.chat.message-created.v1`, Partition 3) → `ChatModerationConsumer` → `ChatModerationService`(#66) 처리 경계에서, Web/API와 AI Consumer가 같은 프로세스에서 실행되는 현재 구조(A안)가 충분한지, 아니면 Web/AI Worker 실행 역할 분리(B안)·Consumer Scale-out이 필요한지를 실측으로 판단한다.

- 대상 코드: `ChatModerationConsumer`, `ChatModerationService`, `AiModerationPort`/`SpringAiModerationAdapter`/`FakeAiModerationAdapter`, `ChatMessageOutboxProcessor`
- 측정·재현 환경: (기록 예정 — 로컬 macOS Docker Kafka KRaft 단일 노드 예상)
- Before/After 커밋: (기록 예정)

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

판정: 검증 예정 (Consumer 수 증가 = 무조건 성능 향상으로 결론내리지 않음)

## 실험 E — AI 장애 격리 (검증 예정)

AI timeout/5xx/429 강제 주입 시 Retry/DLT 발생과 Web/API 정상 동작 여부. 검증 예정.

## 실험 F — Kafka 장애 (검증 예정)

Kafka 중단 시 Web ChatMessage+Outbox 정상 저장, 실시간 채팅 유지, 복구 후 Outbox backlog 발행·AI 처리 회복 여부. 검증 예정.

## 브로셔 대표 수치 후보 (실측 완료 후 채움)

```text
AI 지연 증가 시 Chat SEND p95 변화: 검증 예정
Consumer 중단 N건 적체 후 유실 건수 / 복구 시간: 검증 예정
Consumer 1→3 처리량 및 Lag 해소 시간 변화: 검증 예정
DLT N건 중 N/N 재처리 성공 / 최종 중복·누락 건수: 검증 예정
```

## 최종 판정 (검증 예정)

```text
통합 모놀리스 유지
실행 역할만 Web / AI Worker 분리
MSA 후속 검토
```

측정 완료 전까지 결론을 내리지 않는다.

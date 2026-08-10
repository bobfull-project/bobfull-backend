# BobFull V3 Final Claim Matrix

## 목적

이 문서는 V3 최종 발표·README·포트폴리오에서 사용할 **핵심 개선 주장과 실제 Evidence를 한눈에 연결하는 인덱스**다.

숫자와 검증 현상의 원본은 각 `docs/evidence/v3/<issue>/README.md`이며, 이 문서에 원본 결과를 중복 저장하지 않는다.

```text
Issue
→ 실제 구현/PR
→ Evidence
→ 이 Matrix 핵심 요약
→ 최종 발표·README
```

## 기록 규칙

- 실제 측정한 값만 작성한다.
- 실행하지 않은 반복 실험의 성공률·유실률·개선율을 소급 생성하지 않는다.
- Before/After 조건이 다르면 직접 개선율을 계산하지 않는다.
- 성능이 아닌 신뢰성 문제는 `N건 중 복구 N건`, `중복 0건`, `PENDING 보존`처럼 검증 가능한 현상을 사용한다.
- `MERGED`와 `MEASURED`는 서로 다른 축이다. 구현이 develop에 Merge된 Issue도 정량 Evidence가 없으면 `Final Claim 상태 = MERGED`, `Evidence 수준 = NOT_MEASURED`일 수 있다.
- Evidence가 부족하면 발표 문구를 `구현함` 또는 `검토함` 수준으로 낮춘다.
- 아직 실제 Evidence 경로가 확정되지 않은 Issue는 임의 경로를 만들지 않고 `구현 시 확정`으로 둔다.

## Final Claim 상태

Issue #67 Final Claim Gate를 이 문서의 최종 상태 기준으로 사용한다.

- `MERGED`: 실제 구현이 develop에 Merge 완료됨
- `MEASURED_AND_REJECTED`: 측정 결과 기술 도입 필요성이 없어 미채택
- `DEFERRED`: 프로젝트 범위에서 의도적으로 보류
- `NOT_VERIFIED`: 구현 또는 측정 일부는 존재하지만 최종 검증 근거 부족

## Evidence 수준

Evidence 수준은 구현·채택 상태가 아니라, 최종 주장에 연결할 Evidence의 검증 수준을 표시한다.

- `MEASURED`: 동일 조건 또는 명확한 실험 조건에서 정량 Evidence가 있음
- `VERIFIED`: 기능·장애·신뢰성·정합성 검증은 있으나 성능 수치가 핵심이 아님
- `NOT_MEASURED`: 최종 Evidence가 아직 없음

예를 들어 Outbox는 `Final Claim 상태 = MERGED`, `Evidence 수준 = VERIFIED`일 수 있고, 성능 개선은 `Final Claim 상태 = MERGED`, `Evidence 수준 = MEASURED`일 수 있다.

## V3 핵심 Claim Matrix

| Issue | 최종 주장 | Final Claim 상태 | Evidence 수준 | Primary KPI·현상 | Before | After | Evidence | 핵심 한계 |
|---|---|---|---|---|---|---|---|---|
| #176 ChatRoom Outbox |  |  |  |  |  |  | `176-chatroom-outbox/README.md` | 반복 JVM kill/restart·유실률 통계는 실제 수행 범위 확인 |
| #183 Email Outbox |  |  |  |  |  |  | `183-email-outbox/README.md` | 실제 SMTP 대량 장애·external exactly-once 범위 확인 |
| #59 Outbox + Kafka AI |  |  |  |  |  |  | 구현 시 확정 |  |
| #60 Reservation Lock |  |  |  |  |  |  | `60-reservation-lock/README.md` |  |
| #61 SQL / Index |  |  |  |  |  |  | `61-search-query/README.md` |  |
| #62 Redis Cache |  |  |  |  |  |  | `62-search-cache/README.md` |  |
| #63 K6 Performance Map |  |  |  |  |  |  | `63-api-k6/README.md` |  |
| #65 Settlement |  |  |  |  |  |  | `65-settlement/README.md` |  |
| #66 AI Moderation |  |  |  |  |  |  | 구현 시 확정 | 데이터셋 크기·Provider·PromptVersion 함께 표기 |
| #142 Reservation Peak |  |  |  |  |  |  | 구현 시 확정 |  |
| #143 Payment Completion |  |  |  |  |  |  | 구현 시 확정 |  |
| #146 Refund K6 |  |  |  |  |  |  | 구현 시 확정 |  |
| #169 ALB / HA / Rolling |  |  |  |  |  |  | 구현 시 확정 | 애플리케이션 계층 HA와 전체 시스템 HA 구분 |
| #170 Redis Pub/Sub Chat |  |  |  |  |  |  | 구현 시 확정 | Pub/Sub은 best-effort, DB cursor가 복구 경로 |
| #191 Auto Scaling |  |  |  |  |  |  | 구현 시 확정 | DB·외부 의존성 병목과 구분 |
| #192 AI Worker Split |  |  |  |  |  |  | 구현 시 확정 | 같은 총 자원 조건 비교 여부 명시 |
| #198 Schema Migration |  |  |  |  |  |  | `db-schema-migration/README.md` | 도입 자체가 성공 기준이 아님 |

실제 Evidence 경로가 Issue 구현 과정에서 달라지면 이 Matrix를 실제 경로에 맞게 갱신한다.

## 최종 발표 문구 변환 규칙

### 좋은 예

```text
문제
→ Before 핵심 지표·현상
→ 변경
→ After 핵심 지표·현상
→ 남은 한계
```

### 금지 예

```text
Kafka를 도입해 대규모 트래픽을 처리했다.
```

실제 처리량 측정이 없다면 이런 표현을 사용하지 않는다.

### 허용 예

```text
Kafka 장애 중에도 ChatMessage와 Outbox가 보존되고,
복구 후 적체 이벤트가 다시 처리되는 것을 N건 반복 실험으로 확인했다.
동일 조건의 Consumer Lag·처리량은 Evidence에 기록했다.
```

위 문구도 실제 N건 반복 실험을 수행한 경우에만 사용한다.

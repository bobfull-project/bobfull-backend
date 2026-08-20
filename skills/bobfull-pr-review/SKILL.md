---
name: bobfull-pr-review
description: BobFull V3 Sprint에서 각 PR의 구현 담당 AI가 Draft PR 생성 직후 별도 Human 명령 없이 최신 Head를 독립 리뷰 패스로 재검토하고, 필요한 Before/After Evidence까지 확인해 중요도 순 PR 댓글을 남기는 기준이다.
---

# BobFull V3 Sprint PR Review

## 목적

이 Skill은 **각 PR을 구현한 담당 AI가 구현 단계와 분리된 리뷰 패스**를 수행하도록 한다.

별도의 GitHub Copilot이나 외부 리뷰 AI가 필요하지 않다.
구현 담당 AI가 Draft PR을 생성한 직후 같은 작업 흐름에서 역할을 `구현자 → 리뷰어`로 전환하고, 구현할 때의 기억을 그대로 신뢰하지 않고 GitHub의 최신 근거를 다시 읽는다.

V3 Sprint Mode의 목표는 완벽한 코드가 아니라 **Merge를 막아야 할 치명적 문제와 근거 없는 개선 주장을 빠르게 찾는 것**이다.

## 자동 실행 시점

다음 시점에는 Human이 `PR #번호 검토하라`고 다시 명령하지 않아도 이 Skill을 즉시 실행한다.

1. Draft PR 생성 직후
2. 리뷰의 BLOCKER/MAJOR를 수정해 Push한 직후
3. 구현 담당 AI가 기존 PR에 새 Commit을 Push한 직후
4. Merge 전 최신 Head가 이전 리뷰 Head와 달라졌을 때

즉 기본 흐름은 다음이다.

```text
Before 재현·구현·After 재검증·Evidence 기록
→ Draft PR 생성
→ 담당 구현 AI의 독립 리뷰 패스
→ PR 리뷰 댓글
→ BLOCKER/MAJOR면 수정·검증·Evidence 갱신·Push
→ 같은 담당 AI가 최신 Head 재리뷰
```

## 독립 리뷰 패스 원칙

리뷰할 때 구현 중 자신의 판단이나 이전 요약을 정답으로 가정하지 않는다.
다음을 GitHub 최신 상태 기준으로 다시 확인한다.

1. 연결 Issue의 최신 계약과 범위
2. 최신 PR Head SHA
3. base 대비 실제 Diff
4. 관련 테스트·전체 build·직접 검증 결과
5. 고도화 PR이면 Before/After Evidence, 비교 조건, Commit SHA, 정합성 회귀 결과
6. PR 본문과 실제 Diff/Evidence의 일치 여부
7. 기존 리뷰 댓글과 미해결 지적

리뷰는 **현재 Head 기준**으로 수행한다. 이전 Head의 PASS를 재사용하지 않는다.

## 최우선 리뷰 기준

다음 순서로 본다.

1. 요구한 핵심 기능이 실제로 깨지는가
2. build 또는 주요 런타임 흐름이 깨지는가
3. 데이터 손실·중복 처리·잘못된 상태 전이가 가능한가
4. 결제·환불·예약·좌석·인증·권한 정합성에 치명적 문제가 있는가
5. Transaction·Rollback·Lock·멱등성·외부 I/O·Event 경계가 잘못돼 주요 실패로 이어지는가
6. 필수 예외·실패 처리가 누락됐는가
7. 테스트·전체 build·직접 검증 결과가 실제 사실과 일치하는가
8. 고도화 효과를 주장한다면 Before/After Evidence가 그 주장을 실제로 지지하는가
9. Before/After 환경·데이터·부하 조건이 비교 가능한가
10. 성능·격리 개선 뒤 정합성 회귀가 없는가
11. PR 설명과 실제 Diff/Evidence가 핵심 동작에서 모순되는가

단순 문서·설정·CRUD도 리뷰하지만 해당하지 않는 고급 아키텍처 문제나 의미 없는 성능 측정을 억지로 만들지 않는다.

## Evidence 리뷰 체크

고도화 PR은 아래를 확인한다.

```text
주장: 무엇이 개선됐다고 하는가?
Before: 기존 문제/기준값을 실제로 재현했는가?
After: 같은 조건에서 다시 검증했는가?
정합성: 기능·상태·멱등성은 유지되는가?
추적성: Before/After SHA와 Evidence 경로가 있는가?
한계: Mock/Sandbox/로컬 등 실제 검증 범위를 숨기지 않았는가?
표현: 측정 범위보다 넓은 보장을 주장하지 않는가?
```

- 핵심 고도화 목적 자체가 Evidence로 검증되지 않으면 `MAJOR` 후보다.
- 검증 결과를 허위로 기록하거나 실제 결과와 정면으로 모순되는 주장은 `BLOCKER`까지 가능하다.
- 비교가 의미 없는 변경에서 `NOT_APPLICABLE` 근거가 타당하면 문제로 만들지 않는다.

## 중요도와 Merge 영향

### BLOCKER — Merge 금지

- 권한 우회·보안 문제
- 데이터 손실
- 중복 결제·잘못된 환불
- 핵심 기능 완전 실패
- build 불가
- 핵심 계약 정면 위반
- 검증 결과를 허위로 기록하거나 실제 Evidence와 핵심 주장이 정면으로 모순됨

### MAJOR — Merge 금지

- 주요 요구사항 실패
- 주요 런타임 오류
- 필수 실패 처리 누락
- 운영 핵심 흐름에서 잘못된 결과 발생 가능성이 큼
- `성능 향상`, `유실 방지`, `무중단`, `장애가 다른 처리로 번지지 않음`, `확장성 개선` 등이 PR 핵심 목적이지만 필요한 Before/After Evidence가 없음
- 서로 다른 조건의 수치를 같은 Before/After처럼 비교해 개선 완료를 결론냄

### MINOR — Merge 가능

- 핵심 기능을 깨지 않는 작은 품질 문제
- 추가 테스트 가치
- 명확성·유지보수성 개선
- 핵심 결론을 바꾸지 않는 Evidence 표현·정리 개선

### SUGGESTION — Merge 가능

- 선택적 리팩터링
- 최적화
- 향후 개선 아이디어

### PASS

현재 Merge를 막을 BLOCKER·MAJOR가 보이지 않고, 필요한 필수 검증과 Evidence가 사실과 일치함.
PASS는 완벽함을 의미하지 않는다.

## PR 댓글

각 리뷰 패스마다 PR Conversation에 결과를 남긴다.

```markdown
## 담당 구현 AI Review

- 기준 Head: `<SHA>`
- 연결 Issue: `#번호`
- 검토 수준: `기본 | 강화`

### BLOCKER
- 없음 또는 실제 지적

### MAJOR
- 없음 또는 실제 지적

### MINOR
- 없음 또는 실제 지적

### SUGGESTION
- 없음 또는 실제 제안

### 판정
`BLOCK | MERGEABLE`

### 검증 근거
- 관련 테스트:
- 전체 build:
- 핵심 기능 직접 검증:
- Before/After Evidence: `PASS | FAIL | NOT_APPLICABLE`
- Evidence 경로:
- 비교 조건/한계:
- 남은 미검증 위험:
```

실제 지적은 항상 `BLOCKER → MAJOR → MINOR → SUGGESTION` 순으로 적는다.
지적을 만들기 위해 리뷰 범위를 부풀리지 않는다.

- BLOCKER/MAJOR가 하나라도 있으면 `BLOCK`
- MINOR/SUGGESTION만 있거나 치명적 문제가 없으면 `MERGEABLE`

## 검증 확인

기능 PR에서는 다음을 우선 확인한다.

```text
관련 테스트 PASS
전체 build PASS
변경 핵심 기능 직접 검증 PASS
고도화 PR이면 Before/After Evidence PASS 또는 NOT_APPLICABLE 근거 명확
```

HTTP/API 변경이면 Postman·curl 등 실제 호출 증거를 우선한다.
문서·설정 PR처럼 해당 검증이 의미 없으면 `NOT_RUN` 또는 `NOT_APPLICABLE`과 이유를 명시한다.
실행하지 않은 검증은 PASS로 인정하지 않는다.

## 리뷰 후 처리

- BLOCKER/MAJOR → 담당 구현 AI가 범위 안에서 수정 후 필요한 검증·Evidence 재실행·Push·즉시 최신 Head 재리뷰
- MINOR/SUGGESTION → 기록 후 Merge 가능
- 후속 가치가 있는 비차단 항목 → Issue 또는 troubleshooting 후보
- 정책·API·DB·권한·트랜잭션 재결정이 필요한 사항 → Human 결정 요청

## Human과의 경계

- 기본 PR Human 이해도: 0개
- 강화 PR Human 이해도: 정확히 3개
- Human Review는 V3 Sprint Mode에서 선택적 추가 리뷰
- 필수 Human Approve 수: 0
- 최종 Merge: 담당 Human 책임

담당 구현 AI는 Human 답변·Checklist·Merge를 대신하지 않는다.

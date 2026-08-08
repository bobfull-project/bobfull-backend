---
name: bobfull-pr-review
description: BobFull V3 Sprint에서 각 PR의 구현 담당 AI가 Draft PR 생성 직후 별도 Human 명령 없이 최신 Head를 독립 리뷰 패스로 재검토하고 중요도 순 PR 댓글을 남기는 기준이다.
---

# BobFull V3 Sprint PR Review

## 목적

이 Skill은 **각 PR을 구현한 담당 AI가 구현 단계와 분리된 리뷰 패스**를 수행하도록 한다.

별도의 GitHub Copilot이나 외부 리뷰 AI가 필요하지 않다.
구현 담당 AI가 Draft PR을 생성한 직후 같은 작업 흐름에서 역할을 `구현자 → 리뷰어`로 전환하고, 구현할 때의 기억을 그대로 신뢰하지 않고 GitHub의 최신 근거를 다시 읽는다.

V3 Sprint Mode의 목표는 완벽한 코드가 아니라 **Merge를 막아야 할 치명적 문제를 빠르게 찾는 것**이다.

## 자동 실행 시점

다음 시점에는 Human이 `PR #번호 검토하라`고 다시 명령하지 않아도 이 Skill을 즉시 실행한다.

1. Draft PR 생성 직후
2. 리뷰의 BLOCKER/MAJOR를 수정해 Push한 직후
3. 구현 담당 AI가 기존 PR에 새 Commit을 Push한 직후
4. Merge 전 최신 Head가 이전 리뷰 Head와 달라졌을 때

즉 기본 흐름은 다음이다.

```text
구현·검증
→ Draft PR 생성
→ 담당 구현 AI의 독립 리뷰 패스
→ PR 리뷰 댓글
→ BLOCKER/MAJOR면 수정·검증·Push
→ 같은 담당 AI가 최신 Head 재리뷰
```

## 독립 리뷰 패스 원칙

리뷰할 때 구현 중 자신의 판단이나 이전 요약을 정답으로 가정하지 않는다.
다음을 GitHub에서 최신 상태로 다시 확보한다.

1. 연결 Issue의 최신 계약과 범위
2. 최신 PR Head SHA
3. base 대비 실제 Diff
4. 관련 테스트·전체 build·직접 검증 결과
5. PR 본문과 실제 Diff의 일치 여부
6. 기존 리뷰 댓글과 미해결 지적

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
8. PR 설명과 실제 Diff가 핵심 동작에서 모순되는가

단순 문서·설정·CRUD도 리뷰하지만 해당하지 않는 고급 아키텍처 문제를 억지로 만들지 않는다.

## 중요도와 Merge 영향

### BLOCKER — Merge 금지

- 권한 우회·보안 문제
- 데이터 손실
- 중복 결제·잘못된 환불
- 핵심 기능 완전 실패
- build 불가
- 핵심 계약 정면 위반

### MAJOR — Merge 금지

- 주요 요구사항 실패
- 주요 런타임 오류
- 필수 실패 처리 누락
- 운영 핵심 흐름에서 잘못된 결과 발생 가능성이 큼

### MINOR — Merge 가능

- 핵심 기능을 깨지 않는 작은 품질 문제
- 추가 테스트 가치
- 명확성·유지보수성 개선

### SUGGESTION — Merge 가능

- 선택적 리팩터링
- 최적화
- 향후 개선 아이디어

### PASS

현재 Merge를 막을 BLOCKER·MAJOR가 보이지 않음.
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
```

HTTP/API 변경이면 Postman·curl 등 실제 호출 증거를 우선한다.
문서·설정 PR처럼 해당 검증이 의미 없으면 `NOT_RUN`과 이유를 명시한다.
실행하지 않은 검증은 PASS로 인정하지 않는다.

## 리뷰 후 처리

- BLOCKER/MAJOR → 담당 구현 AI가 범위 안에서 수정 후 필요한 검증 재실행·Push·즉시 최신 Head 재리뷰
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

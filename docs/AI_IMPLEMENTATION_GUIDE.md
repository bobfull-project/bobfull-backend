# BobFull 담당자 AI 실행 가이드

## 1. V3 Sprint Mode

현재 V3 마무리 기간에는 **병목을 줄이고 핵심 위험만 차단**한다.

구현 담당자 AI의 목표는 완벽한 코드를 오래 다듬는 것이 아니라 다음을 빠르게 만족시키는 것이다.

```text
요구 기능 정상 동작
+ 관련 테스트 PASS
+ 전체 build PASS
+ 직접 검증 PASS
+ 자동 AI Review BLOCKER/MAJOR 없음
```

MINOR·SUGGESTION·추가 리팩터링·있으면 좋은 테스트는 Merge를 막지 않는다.

## 2. 역할

구현 담당자 AI는 Issue 계약에 따라 구현·테스트·PR Explain·리뷰 반영을 담당한다.

PR 최초 코드 리뷰는 구현 담당자 AI가 수행하지 않는다.
GitHub Automatic Copilot Code Review가 별도 리뷰어 역할을 맡는다.

자동 리뷰 기준:

- `.github/copilot-instructions.md`
- `.github/skills/bobfull-pr-review/SKILL.md`

## 3. Issue 실행 — 결정이 없으면 바로 진행

Issue 단계에서 학습용 질문을 구현 착수 조건으로 만들지 않는다.

다음이 확정돼 있으면 바로 구현한다.

- 요구 기능
- 변경 범위와 제외 범위
- 기존 코드에서 확인 가능한 상태/API/DB 계약

Human 질문은 실제로 선택이 필요한 경우에만 한다.

- 정책 재결정
- API·DB·상태·권한 변경
- 금액·환불·트랜잭션·보상 정책 결정
- 다른 담당자 범위 충돌

불필요한 질문으로 구현을 대기시키지 않는다.

## 4. 구현·Draft PR

`status:in-progress` 이후 다음 순서로 진행한다.

1. 대상 Issue 전용 브랜치 확인 또는 최신 `develop` 기준 생성
2. Issue 최종 계약과 제외 범위 확인
3. 최소 변경 계획 수립
4. 코드·테스트·필요 문서 구현
5. 변경 기능 관련 테스트 실행
6. 핵심 기능 직접 검증
7. 전체 build 실행
8. 전체 Diff 자체 검토
9. `skills/bobfull-pr-explain/SKILL.md` 적용
10. Issue 관련 변경만 Commit·Push
11. develop 대상 Draft PR 생성

### 직접 검증 기준

- HTTP/API: Postman, curl 또는 동등한 실제 요청으로 원하는 성공 흐름 확인
- 결제·예약·환불: 핵심 상태 변화와 외부/내부 결과 확인
- Scheduler/Event/Consumer: 해당 동작을 실제 실행 가능한 테스트·트리거·로그로 확인
- 문서/설정: 정적 검사 또는 실제 적용 결과 확인

핵심 성공 흐름이 정상이고 전체 build가 통과하면, 범위 밖 시나리오까지 무제한으로 늘리지 않는다.

## 5. PR Explain

PR 설명은 팀원이 빠르게 이해할 수 있게 작성한다.

```text
한 줄 요약 / 관련 Issue
→ PR 이해 요약
→ 상세 변경 및 검증
→ Human 검토
```

필수:

- 무엇을 왜 바꿨는지
- 핵심 실행 흐름
- 중요한 기술 개념
- 전체 build / 직접 검증 결과
- 남아 있는 실제 위험

단순 변경에 의미 없는 Mermaid·개념·트러블슈팅을 억지로 만들지 않는다.

## 6. 자동 독립 AI Review

Repository Ruleset:

```text
Automatically request Copilot code review = Enabled
Review draft pull requests = Enabled
Review new pushes = Enabled
```

최초 리뷰를 위해 `PR #번호 검토하라`를 요구하지 않는다.

### Merge 차단 기준

- `BLOCKER`: 반드시 수정
- `MAJOR`: 반드시 수정

### 비차단 기준

- `MINOR`: 기록 후 Merge 가능
- `SUGGESTION`: 기록 후 Merge 가능

자동 리뷰가 실제 생성되지 않았다면 검증 완료로 간주하지 않는다.

## 7. 리뷰 반영

### 즉시 수정

- 기능 실패
- 전체 build 실패 원인
- 직접 검증 실패
- BLOCKER
- MAJOR
- 데이터 정합성·권한·결제·환불·예약 핵심 오류

### 후속으로 남겨도 됨

- MINOR
- SUGGESTION
- 범위 밖 리팩터링
- 추가 최적화
- 현재 요구 기능을 깨지 않는 추가 테스트 제안

후속 가치가 있으면 Issue 또는 트러블슈팅으로 남긴다.

수정 후에는 필요한 테스트·직접 검증·전체 build를 다시 수행하고 Push한다. `Review new pushes`로 최신 Head가 자동 재리뷰된다.

## 8. PR Human 이해도

### 기본

```text
질문 0개
```

### 강화

```text
정확히 3개
```

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 적용 위치·이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

코드 줄 암기보다 실제 기능을 설명할 수 있는지를 확인한다.

## 9. 승인과 Merge

V3 Sprint Mode의 필수 Human Approve 수는 `0`이다.

별도 리뷰어의 Approve를 기다리지 않는다.
Human 리뷰는 도움이 되면 수행하지만 필수 Gate가 아니다.

담당 Human은 다음을 확인하고 직접 Merge한다.

```text
전체 build PASS
핵심 기능 직접 검증 PASS
Automatic Copilot Review 실행 확인
미해결 BLOCKER 없음
미해결 MAJOR 없음
Human 결정 필요 사항 없음
강화 PR이면 Human 3문항 완료
```

위 조건을 만족하면 MINOR·SUGGESTION이 남아 있어도 진행할 수 있다.

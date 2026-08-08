---
name: bobfull-pr-review
description: GitHub Copilot 자동 코드 리뷰가 BobFull V3 Sprint PR에서 구현 에이전트와 독립된 리뷰어 관점으로 치명적 결함을 우선 검토하는 기준이다.
---

# BobFull V3 Sprint Automatic PR Review

## 목적

이 Skill은 PR을 구현한 담당자 AI의 자기리뷰가 아니다.
GitHub Automatic Copilot Code Review가 **독립 리뷰어**로 최신 Diff를 검토할 때 사용한다.

현재 V3 Sprint Mode의 목표는 완벽한 코드가 아니라 **Merge를 막아야 할 치명적 문제를 빠르게 찾는 것**이다.

## 실행

Repository Ruleset:

```text
Automatically request Copilot code review = Enabled
Review draft pull requests = Enabled
Review new pushes = Enabled
```

사람의 수동 리뷰 명령을 최초 실행 조건으로 두지 않는다.

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

### BLOCKER

Merge 금지.

예:

- 권한 우회·보안 문제
- 데이터 손실
- 중복 결제·잘못된 환불
- 핵심 기능 완전 실패
- build 불가
- 핵심 계약 정면 위반

### MAJOR

Merge 금지.

예:

- 주요 요구사항 실패
- 주요 런타임 오류
- 필수 실패 처리 누락
- 운영 핵심 흐름에서 잘못된 결과 발생 가능성이 큼

### MINOR

Merge 가능.

- 핵심 기능을 깨지 않는 작은 품질 문제
- 추가 테스트 가치
- 명확성·유지보수성 개선

### SUGGESTION

Merge 가능.

- 선택적 리팩터링
- 최적화
- 향후 개선 아이디어

### PASS

현재 Merge를 막을 BLOCKER·MAJOR가 보이지 않음.

PASS는 완벽함을 의미하지 않는다.

## 리뷰 출력

실제 발견 사항을 높은 중요도부터 정리한다.

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
판정: BLOCK | MERGEABLE
```

- BLOCKER/MAJOR가 하나라도 있으면 `BLOCK`
- MINOR/SUGGESTION만 있으면 `MERGEABLE`
- 의미 없는 지적을 만들기 위해 리뷰 범위를 부풀리지 않는다.

코드 위치가 명확하면 inline review comment를 사용한다.

## 검증 확인

기능 PR에서는 다음을 우선 확인한다.

```text
관련 테스트
전체 build
변경 핵심 기능 직접 검증
```

HTTP/API 변경이면 Postman·curl 등 실제 호출 증거가 있는지 확인한다.

실행하지 않은 검증은 PASS로 인정하지 않는다.

## 리뷰 후 처리

- BLOCKER/MAJOR → 수정 후 새 Push, 최신 Head 자동 재리뷰
- MINOR/SUGGESTION → 기록 후 Merge 가능
- 후속 가치가 있는 비차단 항목 → Issue 또는 troubleshooting 후보

## Human과의 경계

- 기본 PR Human 이해도: 0개
- 강화 PR Human 이해도: 정확히 3개
- Human Review는 V3 Sprint Mode에서 선택적 추가 리뷰
- 필수 Human Approve 수: 0
- 최종 Merge: 담당 Human 책임

자동 리뷰 에이전트는 Human 답변이나 Merge를 대신하지 않는다.

---
name: bobfull-pr-review
description: GitHub Copilot 자동 코드 리뷰가 BobFull PR을 구현 에이전트와 독립된 리뷰어 관점에서 검토할 때 적용하는 기준이다.
---

# BobFull Automatic PR Review

## 목적

이 Skill은 **PR을 구현한 담당자 AI가 자기 코드를 다시 보는 절차가 아니다.**
GitHub의 Automatic Copilot Code Review가 별도 리뷰어 역할에서 최신 Diff를 검토하도록 하는 기준이다.

모든 PR은 검토 수준과 관계없이 자동 AI Review 대상이다. `기본 | 강화`는 Human 이해도 질문 수만 결정한다.

## 실행 주체와 트리거

- 실행 주체: GitHub Copilot Code Review
- 구현 담당자 AI와 리뷰 에이전트의 역할을 분리한다.
- Repository Ruleset의 `Automatically request Copilot code review`를 사용한다.
- `Review draft pull requests`를 켜 Draft PR 생성부터 자동 리뷰한다.
- `Review new pushes`를 켜 새 Commit Push마다 최신 Head를 자동 재리뷰한다.
- 사람이 `PR #번호 검토하라` 같은 명령을 입력하는 것을 최초 리뷰의 전제조건으로 두지 않는다.

## 리뷰 기준

1. PR 설명의 목적·범위와 실제 Diff가 일치하는가
2. 핵심 정상 흐름과 실패·중복·경계 흐름이 올바른가
3. Transaction, Lock, Event, 외부 I/O, 멱등성, 권한 등 변경에 중요한 경계가 안전한가
4. 예외 처리와 상태 전이가 데이터 정합성을 깨뜨리지 않는가
5. 테스트가 변경 동작과 회귀 위험을 충분히 검증하는가
6. `PASS`, `NOT_RUN`, CI 상태와 검증 결과가 과장되지 않았는가
7. PR 이해 요약·Mermaid·주요 개념·코드 읽는 순서가 최신 Head와 일치하는가
8. 이전 리뷰 지적이 최신 Push에서 해결됐는가

단순 문서·설정·CRUD PR도 리뷰는 실행하되, 의미 없는 지적을 만들지 않는다.

## 중요도

- **BLOCKER**: 보안·데이터 손실·중복 결제·권한 우회·핵심 계약 훼손 등 Merge하면 안 되는 문제
- **MAJOR**: 요구사항 실패, 주요 런타임 오류, 필수 실패 처리 누락, 핵심 설계 문제
- **MINOR**: Merge를 직접 막지는 않지만 명확성·유지보수성·테스트 정확도 개선 가치가 있는 문제
- **SUGGESTION**: 현재 구현도 허용 가능하며 선택적으로 개선할 수 있는 제안
- **PASS**: 실제 근거를 검토했지만 보고할 BLOCKER·MAJOR·MINOR가 없음

리뷰 흔적을 만들기 위해 지적을 억지로 생성하지 않는다.

## 리뷰 출력

가능하면 리뷰 요약을 다음 순서로 구성한다.

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
판정: PASS | 수정 후 재검토 필요
```

실제 코드 위치에 대한 지적은 GitHub inline review comment를 우선 사용한다. 전체 판단이나 공통 위험은 review summary에 남긴다.

BLOCKER 또는 MAJOR가 발견되면 `수정 후 재검토 필요`로 판단한다. 새 Push가 올라오면 이전 PASS를 재사용하지 않고 최신 Head를 다시 검토한다.

## Human 검토와의 경계

- 자동 AI Review: 코드·계약·테스트의 결함과 불일치를 찾는다.
- PR Human 이해도: 기본 0개, 강화 정확히 3개만 사용한다.
- Human Review Checklist: 변경 목적·기본 흐름·중요 개념·검증 위험을 이해했는지 확인한다.
- Human Approve와 Merge: Human 책임이다.

자동 리뷰 에이전트는 Human 답변·Checklist 체크·Approve·Merge를 대신하지 않는다.

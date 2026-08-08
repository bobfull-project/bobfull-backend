## 한 줄 요약

무엇을:
왜:
기술부채(있다면):
의도부채(있다면, Issue 논의와 달라진 부분):

<!-- `무엇을`, `왜`는 실제 Issue·Diff를 바탕으로 작성합니다. 기술부채와 의도부채가 없으면 각각 `없음`으로 기록하며, 기술부채는 현재 제한사항과 혼동하지 않습니다. -->

## 관련 Issue

- Closes #
- 검토 수준: `기본 | 강화`

<!-- GitHub Repository Ruleset의 `Automatically request Copilot code review`가 구현 담당 AI와 별개의 Copilot reviewer를 자동 요청합니다. `Review draft pull requests`와 `Review new pushes`를 사용해 Draft PR과 새 Push도 자동 재리뷰합니다. 리뷰 기준은 `.github/skills/bobfull-pr-review/SKILL.md`와 `.github/copilot-instructions.md`를 따릅니다. 최초 AI Review를 위해 `PR #번호 검토하라` 같은 수동 명령을 요구하지 않습니다. -->

## PR 이해 요약

### 쉬운 설명

<!-- 처음 보는 팀원도 이해할 수 있게 실제 Issue·Diff를 근거로 3~5문장으로 작성합니다. 필요한 전문용어는 아래 주요 개념에서 바로 풉니다. -->

-

### 주요 실행 흐름

<!-- 요청 → 검증 → Transaction/Lock → 상태 변경 → 이벤트/외부 I/O → 응답 중 실제 변경에 필요한 흐름만 짧게 작성합니다. CREATE/JOIN, 성공/실패처럼 핵심 분기가 있으면 분리합니다. -->

1.
2.
3.

### Mermaid 시각화

<!-- 의미 있는 실행 흐름이 있는 기능 PR은 최신 Head의 실제 클래스·호출·분기·상태와 일치하는 Mermaid를 작성합니다. 단순 문서·설정·DTO·정적 상수 변경이면 `해당 없음`과 이유를 작성합니다. -->

해당 없음:

### 주요 개념

<!-- 실제 PR 이해에 필요한 개념만 2~5개 작성합니다. 단순 문서·설정·CRUD처럼 별도 핵심 개념이 없으면 `해당 없음`과 이유를 작성합니다. -->

| 개념 | 쉽게 말하면 | 이 PR에서 왜 필요한가 |
|---|---|---|
|  |  |  |

### 핵심 트러블슈팅

<!-- 실제 구현 과정의 의미 있는 문제만 3~5문장으로 요약합니다. 없으면 `해당 없음`과 이유를 작성합니다. -->

해당 없음:

### 코드 읽는 순서

<!-- 실제 변경 파일·호출 흐름을 따라 3~6단계로 작성합니다. -->

1.
2.
3.

## 상세 변경 및 검증

- BLOCKER·FAIL·NOT_RUN·미검증 위험:

<details>
<summary>상세 변경 및 검증 펼치기</summary>

### 주요 변경

-

### 예외·실패·중복·경계 상황

-

### 트레이드오프

-

### 현재 제한사항과 후속 개선

-

### 제외 범위

-

### 추가·수정 테스트

| 테스트 클래스·파일 | 검증 시나리오 | 이 테스트가 보장하는 것 |
|---|---|---|
|  |  |  |

### 완료 조건·검증 결과

| Issue 완료 조건 | 구현 위치 | 테스트·직접 검증 증거 | 결과 |
|---|---|---|---|
|  |  |  | `PASS | FAIL | HOLD | NOT_RUN` |

### 테스트·build·직접 검증

| 검증 방법 | 실행 명령·환경 | 결과 | 실제 증거·한계 |
|---|---|---|---|
| 관련 테스트 |  | `PASS | FAIL | HOLD | NOT_RUN` |  |
| 전체 테스트·build |  | `PASS | FAIL | HOLD | NOT_RUN` |  |
| 직접 검증 |  | `PASS | FAIL | HOLD | NOT_RUN` |  |
| CI |  | `PASS | FAIL | 진행 중 | 미등록 | 미실행` |  |

- 최신 검증 Commit SHA:
- 미검증 범위와 남은 위험:

</details>

## Human 검토

### Human 이해도

<!--
- 기본: Human 이해도 질문 0개. 아래 `해당 없음: 기본 검토`만 유지합니다.
- 강화: 아래 문구를 제거하고 최신 Diff 기준 질문을 정확히 3개 삽입합니다.
강화 질문의 세 축은 `핵심 실행 흐름`, `중요 기술 개념과 적용 이유`, `설계 선택·실패 처리·남은 한계`입니다.
-->

해당 없음: 기본 검토

### 자동 AI Review·반영 기록

<!-- 실제 리뷰 내용은 GitHub Copilot Review/inline comment에 남습니다. 구현 담당자는 리뷰 반영 후 최신 상태만 요약합니다. -->

- 자동 리뷰 방식: `GitHub Automatic Copilot Code Review`
- Ruleset: `Automatically request Copilot code review`
- Draft 자동 리뷰: `Review draft pull requests`
- 새 Push 자동 재리뷰: `Review new pushes`
- 최신 자동 AI Review 기준 Head:
- 최신 자동 AI Review 결과: `PASS | 수정 필요 | 실행 실패 | 미실행`
- 반영한 항목:
- 반영하지 않은 항목과 이유:
- Human 결정이 필요한 항목:
- 재실행 테스트·build 결과:
- 남은 미검증 위험:

### Human Review Checklist

- [ ] 이 PR이 무엇을 왜 변경하는지 이해했다.
- [ ] 변경 후 기본 실행 흐름과 중요한 분기를 이해했다.
- [ ] 이 PR에 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했다.
- [ ] 테스트·검증 결과와 남아 있는 미검증 위험을 확인했다.

#### 리뷰 댓글 작성

<!-- Human 리뷰어는 이해되지 않거나 추가 설명이 필요한 부분, 수정이 필요한 부분, 제안 사항을 PR 댓글에 직접 작성합니다. -->

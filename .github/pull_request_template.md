## 한 줄 요약

무엇을:
왜:
기술부채(있다면):
의도부채(있다면, Issue 논의와 달라진 부분):

## 관련 Issue

- Closes #
- 검토 수준: `기본 | 강화`
- 운영 모드: `V3 Sprint Mode`

<!-- GitHub Automatic Copilot Code Review를 독립 리뷰어로 사용합니다. 기본 PR Human 이해도는 0개, 강화 PR은 정확히 3개입니다. V3 Sprint Mode의 필수 Human Approve 수는 0명입니다. -->

## PR 이해 요약

### 쉬운 설명

<!-- 처음 보는 팀원이 3~5문장으로 무엇을 왜 바꿨는지 이해할 수 있게 작성합니다. -->

-

### 주요 실행 흐름

<!-- 실제 핵심 흐름과 중요한 분기만 작성합니다. -->

1.
2.
3.

### Mermaid 시각화

<!-- 의미 있는 실행 흐름이 있을 때만 작성합니다. 단순 문서·설정·CRUD면 `해당 없음`과 이유를 작성합니다. -->

해당 없음:

### 주요 개념

<!-- 실제 PR 이해에 필요한 개념만 작성합니다. -->

| 개념 | 쉽게 말하면 | 이 PR에서 왜 필요한가 |
|---|---|---|
|  |  |  |

### 핵심 트러블슈팅

<!-- 실제 의미 있는 문제 해결이 있었을 때만 작성합니다. 없으면 `해당 없음`과 이유를 작성합니다. -->

해당 없음:

### 코드 읽는 순서

1.
2.
3.

## 상세 변경 및 검증

- 현재 Merge를 막는 위험(BLOCKER·MAJOR·FAIL):
- Merge를 막지 않는 후속 항목(MINOR·SUGGESTION·기술부채):

<details>
<summary>상세 변경 및 검증 펼치기</summary>

### 주요 변경

-

### 예외·실패·중복·경계 상황

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

| Issue 완료 조건 | 구현 위치 | 검증 증거 | 결과 |
|---|---|---|---|
|  |  |  | `PASS | FAIL | NOT_RUN` |

</details>

## V3 Sprint 필수 검증

<!-- 기능 PR은 아래 세 항목을 우선합니다. 문서·설정 전용이면 해당하지 않는 항목에 NOT_RUN 이유를 적습니다. -->

| Merge Gate | 실행 명령·환경 | 결과 | 증거·한계 |
|---|---|---|---|
| 관련 테스트 |  | `PASS | FAIL | NOT_RUN` |  |
| 전체 build |  | `PASS | FAIL | NOT_RUN` |  |
| 핵심 기능 직접 검증 | Postman/curl/직접 트리거 등 | `PASS | FAIL | NOT_RUN` |  |
| Automatic Copilot Review | GitHub Review | `MERGEABLE | BLOCK | 미실행` |  |

- 최신 검증 Commit SHA:
- 미해결 BLOCKER:
- 미해결 MAJOR:
- Human 결정 필요 사항:
- Merge를 막지 않는 MINOR/SUGGESTION:

## Human 이해 확인

### Human 이해도

<!-- 기본: 질문 0개, 아래 문구 유지. 강화: 아래 문구를 제거하고 정확히 3문항을 삽입합니다. -->

해당 없음: 기본 검토

<!-- 강화 PR 3문항 축
1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계
-->

### 자동 AI Review·반영 기록

- 자동 리뷰 방식: `GitHub Automatic Copilot Code Review`
- 최신 자동 AI Review 기준 Head:
- 최신 판정: `MERGEABLE | BLOCK | 미실행`
- BLOCKER/MAJOR 반영 내용:
- MINOR/SUGGESTION 후속 처리:
- 리뷰 후 재실행 검증:

### Human 이해 Checklist

<!-- 별도 리뷰어 Approve Gate가 아니라 담당자와 팀원이 PR을 빠르게 이해하기 위한 기준입니다. -->

- [ ] 이 PR이 무엇을 왜 변경하는지 이해했다.
- [ ] 기본 실행 흐름과 중요한 분기를 이해했다.
- [ ] 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했다.
- [ ] 전체 build·직접 검증·자동 AI Review 결과와 남은 위험을 확인했다.

## V3 Sprint Merge Gate

<!-- 필수 Human Approve: 0명 -->

- [ ] 전체 build `PASS`
- [ ] 변경 핵심 기능 직접 검증 `PASS`
- [ ] Automatic Copilot Review 실행 완료
- [ ] 미해결 `BLOCKER` 없음
- [ ] 미해결 `MAJOR` 없음
- [ ] Human 결정 필요 사항 없음
- [ ] 강화 PR인 경우 Human 이해도 3문항 완료

`MINOR`와 `SUGGESTION`은 기록 후 Merge를 막지 않습니다.

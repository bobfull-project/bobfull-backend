# BobFull AI 협업 워크플로우

## 1. 역할

BobFull은 구현 AI와 리뷰 AI를 분리한다.

- 구현 AI: Issue 계약에 따라 구현·테스트·PR 설명 작성
- 자동 리뷰 AI: GitHub Automatic Copilot Code Review
- 담당자 Human: Issue 정책 판단과 강화 PR Human 이해도 답변
- Human 리뷰어: 최종 코드 리뷰·Approve
- Merge: Human 책임

## 2. 전체 흐름

```text
Issue 분석
→ Human 질문·정책 확정
→ status:in-progress
→ 구현·테스트·Diff 자체 검토
→ bobfull-pr-explain 적용
→ Draft PR 생성
→ GitHub Ruleset이 Copilot reviewer 자동 요청
→ 독립 AI Review 댓글
→ BLOCKER/MAJOR면 구현 담당자가 수정·Push
→ Review new pushes로 자동 재리뷰
→ 기본 PR: Human 이해도 0개
→ 강화 PR: Human 이해도 정확히 3개
→ Human Review Checklist
→ status:final-human-review
→ Human Approve
→ Merge
```

**최초 AI Review를 위해 `PR #번호 검토하라` 같은 수동 명령을 요구하지 않는다.**

## 3. Issue 단계

Issue 단계 Human 질문 정책은 기존 규칙을 유지한다.

- 기본: 필요한 질문 1~2개
- 강화: 필요한 질문 2~3개
- 강화: 담당자가 직접 작성한 이해 근거 한 줄 이상 필요

실제 실행 상태는 GitHub `status:*` Label 하나로 관리한다.

```text
status:human-answer-required
status:in-progress
status:final-human-review
```

## 4. 구현과 Draft PR

`status:in-progress` 이후 구현 AI는 다음을 수행한다.

1. 대상 Issue 전용 브랜치 확인 또는 최신 `develop` 기준 생성
2. Issue 최종 계약 범위의 최소 변경 계획 수립
3. 강화 검토 대상이면 구현 전 설계 확인
4. 코드·테스트·필요 문서 구현
5. 관련 테스트·직접 검증 실행
6. 실제 Diff 자체 검토
7. `skills/bobfull-pr-explain/SKILL.md` 적용
8. Issue 관련 변경만 Commit·Push
9. develop 대상 Draft PR 생성

PR 본문은 다음 4계층을 유지한다.

```text
한 줄 요약 / 관련 Issue
→ PR 이해 요약
→ 상세 변경 및 검증
→ Human 검토
```

## 5. 자동 독립 AI Review

### 5.1 실행 주체

PR 코드 리뷰는 구현 담당 AI의 자기리뷰가 아니다.

**GitHub Automatic Copilot Code Review를 독립 리뷰어로 사용한다.**

리뷰 기준:

- `.github/copilot-instructions.md`
- `.github/skills/bobfull-pr-review/SKILL.md`

### 5.2 Repository Ruleset

저장소 Ruleset에서 다음을 사용한다.

```text
Automatically request Copilot code review = Enabled
Review draft pull requests = Enabled
Review new pushes = Enabled
```

따라서:

```text
Draft PR 생성
→ 자동 리뷰

새 Commit Push
→ 자동 재리뷰
```

리뷰를 시작하기 위한 Human 명령은 필요 없다.

### 5.3 리뷰 중요도

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
PASS
```

단순 문서·설정·CRUD도 리뷰는 실행하되 의미 없는 지적을 만들지 않는다.

BLOCKER 또는 MAJOR가 있으면 구현 담당자가 범위 안에서 수정·재검증·Push한다. `Review new pushes`가 최신 Head를 자동 재리뷰한다.

### 5.4 자동 리뷰 실패

Copilot Review가 실제 PR에 생성되지 않았으면 이를 PASS로 보지 않는다.

- Copilot Code Review 정책·라이선스·Ruleset 상태 확인
- 실제 Review/inline comment 생성 여부 확인
- 미생성 시 `자동 AI Review 미실행`으로 기록

자동 리뷰는 Human Approve를 대체하지 않는다.

## 6. PR Human 이해도

### 기본

```text
Human 이해도 질문: 0개
```

문서·설정·단순 CRUD·기존 패턴 반복에는 질문을 만들지 않는다.

### 강화

```text
Human 이해도 질문: 정확히 3개
```

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

특정 클래스·메서드 암기나 프레임워크 내부 구현 퀴즈로 만들지 않는다.

## 7. Human Review Checklist

모든 PR은 다음 공통 체크를 사용한다.

- [ ] 이 PR이 무엇을 왜 변경하는지 이해했다.
- [ ] 변경 후 기본 실행 흐름과 중요한 분기를 이해했다.
- [ ] 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했다.
- [ ] 테스트·검증 결과와 남아 있는 미검증 위험을 확인했다.

## 8. 리뷰 반영

### 범위 안 자동 반영 가능

- Issue 계약 안의 명확한 기능 오류
- 예외 처리·검증·테스트 누락
- PR 설명과 Diff 불일치
- 정책 재결정이 필요 없는 BLOCKER·MAJOR·MINOR

### Human 결정 필요

- Issue 범위 확장
- 정책·API·DB·상태·권한·트랜잭션 재결정
- 새로운 라이브러리·인프라 도입
- 다른 담당자 계약 변경

실제 파일 수정을 시작하면 `status:in-progress`로 되돌리고 수정·검증·Push 후 자동 재리뷰를 확인한다.

## 9. Merge 전 경계

- 최신 Head 필수 테스트·build·직접 검증 상태 확인
- 최신 Head Automatic Copilot Review 실제 생성 확인
- 해결되지 않은 BLOCKER 없음
- 강화 PR이면 Human 이해도 3문항 답변과 AI 대조 완료
- 남은 Human 결정 필요 사항 명시
- Human Review Checklist 확인 가능 상태

Approve와 Merge는 Human이 수행한다.

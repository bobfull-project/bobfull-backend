# BobFull AI 협업 워크플로우

## 1. 목적

BobFull은 구현 AI, 자동 리뷰 AI, 담당자 Human, Human 리뷰어의 역할을 분리한다.

- 구현 AI: Issue 계약에 따라 구현·테스트·PR 설명 작성
- 자동 리뷰 AI: GitHub Copilot Code Review가 구현 AI와 독립된 리뷰어 역할로 PR을 검토
- 담당자 Human: Issue 정책 판단과 강화 PR Human 이해도 답변
- Human 리뷰어: 최종 코드 리뷰·Approve
- Merge: Human 책임

Issue 단계 시작 명령은 다음과 같다.

```text
Issue #번호 구현하라
```

새 Issue는 다음 순서를 따른다.

```text
새 Issue 초안 작성하라
→ Human 확인
→ 이 초안으로 Issue 생성하라
```

## 2. 전체 흐름

```text
Issue 분석
→ Human 질문·정책 확정
→ status:in-progress
→ 구현·테스트·Diff 자체 검토
→ bobfull-pr-explain 적용
→ Draft PR 생성
→ GitHub PR event 발생
→ copilot-auto-review workflow
→ GitHub Copilot reviewer 자동 요청
→ 독립 AI Review 댓글
→ BLOCKER/MAJOR면 구현 담당자가 수정·Push
→ synchronize event로 자동 재리뷰
→ 기본 PR: Human 이해도 0개
→ 강화 PR: Human 이해도 정확히 3개
→ Human Review Checklist 확인
→ status:final-human-review
→ Human Approve
→ Merge
```

**최초 AI Review를 시작하기 위해 `PR #번호 검토하라` 같은 수동 명령을 요구하지 않는다.**
PR이 열리거나 새 Push가 올라오면 자동 리뷰가 시작되는 것이 공식 흐름이다.

## 3. Issue 단계

### 3.1 Issue 분석

구현 AI는 최신 Issue, 확정 문서, 관련 코드와 테스트를 확인한다.

- 목적·범위·제외 범위
- 정상·실패·경계 흐름
- API·DB·상태·권한·트랜잭션
- 선행 작업과 도메인 의존성
- 완료 조건과 검증 계획
- 문서·Issue·코드 충돌

Human 결정이 필요한 사항이 있으면 Issue 본문의 `Human 이해도`에 질문을 작성하고 `status:human-answer-required`를 적용한다.

### 3.2 Issue Human 질문

Issue 단계 질문 정책은 기존 규칙을 유지한다.

- 기본 검토: 필요한 질문 1~2개
- 강화 검토: 필요한 질문 2~3개
- 강화 검토는 담당자가 직접 작성한 이해 근거 한 줄 이상 필요

담당자 AI는 Human 원문·최종 확인·강화 이해 근거를 대신 작성하지 않는다.

### 3.3 상태 Label

실제 실행 상태는 GitHub `status:*` Label 하나를 기준으로 한다.

```text
status:human-answer-required
status:in-progress
status:final-human-review
```

상태를 바꿀 때 기존 `status:*` Label을 제거하고 새 상태 하나만 적용한다.

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

`BLOCKER`, `FAIL`, `NOT_RUN`, 미검증 위험은 접힌 `<details>` 안에만 숨기지 않는다.

## 5. 자동 독립 AI Review

### 5.1 실행 주체

PR 코드 리뷰는 구현 담당 AI가 자기 PR을 다시 읽고 PASS하는 구조가 아니다.

**GitHub Copilot Code Review를 독립 리뷰어로 사용한다.**

리뷰 기준:

- `.github/copilot-instructions.md`
- `.github/skills/bobfull-pr-review/SKILL.md`

### 5.2 자동 트리거

`.github/workflows/copilot-auto-review.yml`은 다음 PR event에서 Copilot reviewer를 자동 요청한다.

```text
opened
synchronize
reopened
ready_for_review
```

따라서:

```text
PR 생성
→ 자동 리뷰

새 Commit Push
→ synchronize
→ 자동 재리뷰
```

리뷰를 시작하기 위한 Human 명령은 필요 없다.

### 5.3 리뷰 출력

자동 리뷰는 실제 코드 위치의 inline comment와 review summary를 사용한다.

발견 사항은 다음 중요도 순으로 판단한다.

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
PASS
```

의미 있는 문제가 없으면 억지 지적을 생성하지 않는다.

BLOCKER 또는 MAJOR가 있으면 구현 담당자가 범위 안에서 수정·재검증·Push한다. 새 Push가 올라오면 자동 재리뷰가 다시 실행된다.

### 5.4 자동 리뷰 실패

Copilot reviewer 요청 자체가 실패하면 이를 `PASS`로 보지 않는다.

- workflow 실패 원인 확인
- Copilot Code Review 사용 가능 여부 확인
- 리뷰가 실제 생성되기 전까지 `자동 AI Review 미실행`으로 기록

자동 리뷰는 Human Approve를 대체하지 않는다.

## 6. PR Human 이해도

PR 단계 질문 수는 검토 수준으로 고정한다.

### 기본

```text
Human 이해도 질문: 0개
```

문서·설정·단순 CRUD·기존 패턴 반복처럼 별도 학습 검증 가치가 낮은 PR에는 질문을 만들지 않는다.

### 강화

```text
Human 이해도 질문: 정확히 3개
```

세 축을 고정한다.

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

특정 클래스·메서드 암기나 프레임워크 내부 구현 퀴즈로 만들지 않는다.

## 7. Human Review Checklist

PR마다 임의의 세부 구현 질문을 생성하지 않는다.

모든 PR은 다음 공통 체크를 사용한다.

- [ ] 이 PR이 무엇을 왜 변경하는지 이해했다.
- [ ] 변경 후 기본 실행 흐름과 중요한 분기를 이해했다.
- [ ] 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했다.
- [ ] 테스트·검증 결과와 남아 있는 미검증 위험을 확인했다.

Human 리뷰어는 필요한 의견을 PR 댓글에 직접 남긴다.

## 8. 리뷰 반영

자동 AI Review 또는 Human Review에서 실제 결함이 발견되면 구현 담당 AI는 Issue 계약과 최신 코드로 근거를 확인한다.

### 자동 수정 가능

- Issue 계약 안의 명확한 기능 오류
- 예외 처리·검증·테스트 누락
- PR 설명과 Diff 불일치
- 정책 재결정이 필요 없는 BLOCKER·MAJOR·MINOR

### Human 결정 필요

- Issue 범위 확장
- 정책·API·DB·상태·권한·트랜잭션 재결정
- 새로운 라이브러리·인프라 도입
- 다른 담당자 계약 변경

실제 파일 수정을 시작하면 `status:in-progress`로 되돌리고, 수정·검증·Push 후 자동 재리뷰 결과까지 확인한다.

## 9. Merge 전 경계

다음을 확인한 뒤 `status:final-human-review`로 전환한다.

- 최신 Head의 필수 테스트·build·직접 검증 상태 확인
- 최신 Head에 대한 자동 AI Review 실제 생성 확인
- 해결되지 않은 BLOCKER 없음
- 강화 PR이면 Human 이해도 3문항 답변과 AI 대조 완료
- 남은 Human 결정 필요 사항 명시
- Human Review Checklist 확인 가능 상태

Approve와 Merge는 Human이 수행한다.

## 10. 역할 요약

```text
구현 AI
= 만든다 + 테스트한다 + PR을 설명한다

GitHub Copilot Reviewer
= PR event로 자동 실행되는 독립 AI 리뷰어

담당자 Human
= 정책 판단 + 강화 PR 이해도 3문항

Human Reviewer
= 최종 검토 + Approve
```

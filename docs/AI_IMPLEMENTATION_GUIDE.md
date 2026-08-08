# BobFull 담당자 AI 실행 가이드

## 1. 역할

구현 담당자 AI는 Issue 계약에 따라 구현·테스트·PR 설명 작성과 리뷰 반영을 담당한다.

**PR의 최초 AI 코드 리뷰는 구현 담당자 AI가 수행하지 않는다.**
PR 생성/업데이트 시 GitHub가 별도의 Copilot reviewer를 자동 요청한다.

자동 리뷰 기준:

- `.github/copilot-instructions.md`
- `.github/skills/bobfull-pr-review/SKILL.md`
- `.github/workflows/copilot-auto-review.yml`

## 2. Issue 실행

Issue 단계 시작 명령:

```text
Issue #번호 구현하라
```

새 Issue:

```text
새 Issue 초안 작성하라
→ Human 승인
→ 이 초안으로 Issue 생성하라
```

### Issue Human 질문

Issue 단계의 기존 정책을 유지한다.

- 기본: 필요한 질문 1~2개
- 강화: 필요한 질문 2~3개
- 강화: `이해함` 외 담당자가 직접 작성한 이해 근거 한 줄 이상 필요

담당자 AI는 Human 원문을 대신 작성하지 않는다.

## 3. 구현·Draft PR

`status:in-progress` 이후 다음 순서로 진행한다.

1. 현재 브랜치와 작업 트리를 확인한다.
2. 대상 Issue 전용 브랜치가 없으면 최신 `develop`에서 생성한다.
3. Issue 최종 계약과 제외 범위를 확인한다.
4. 강화 검토 대상이면 구현 전 설계 확인을 수행한다.
5. 코드·테스트·필요 문서를 구현한다.
6. 관련 테스트와 직접 검증을 실제 실행한다.
7. 전체 Diff를 자체 검토한다.
8. `skills/bobfull-pr-explain/SKILL.md`를 적용해 PR 본문을 작성한다.
9. Issue 관련 변경만 Commit·Push한다.
10. develop 대상 Draft PR을 생성한다.

Draft PR 생성 이후 **구현 담당자 AI가 자기 PR을 AI Review하고 댓글을 만드는 단계를 넣지 않는다.**

PR 생성 event가 발생하면 GitHub 자동화가 별도 Copilot reviewer를 요청한다.

```text
Draft PR 생성
→ pull_request.opened
→ Copilot reviewer 자동 요청
→ 독립 AI Review
```

## 4. 구현 전 강화 설계 확인

강화 검토 대상은 코드 작성 전에 다음 네 항목을 Human과 확인한다.

```markdown
## 구현 전 설계 확인 기록

- 책임 클래스:
- 상태 변경 위치:
- 트랜잭션 범위:
- 실패 처리 방식:
- 담당자 Human 확인: `확인 | 수정 요청`
- 구현 중 달라진 점: `없음 | 변경 내용과 이유`
```

정책·API·DB·권한·트랜잭션 재결정이 필요하면 구현을 중단하고 Human 판단을 요청한다.

## 5. PR Explain 작성

PR 본문은 다음 구조를 유지한다.

```text
한 줄 요약 / 관련 Issue
→ PR 이해 요약
→ 상세 변경 및 검증
→ Human 검토
```

작성 원칙:

- 최신 Issue 계약과 실제 Diff를 근거로 한다.
- 실행하지 않은 검증을 PASS로 기록하지 않는다.
- `BLOCKER`, `FAIL`, `NOT_RUN`, 미검증 위험을 접힌 영역에만 숨기지 않는다.
- 의미 없는 Mermaid·주요 개념·트러블슈팅을 억지로 생성하지 않는다.
- 단순 문서·설정·CRUD는 해당 없는 항목에 이유를 적는다.

## 6. 자동 AI Review

### 6.1 구현 담당자와 리뷰 에이전트 분리

```text
구현 담당자 AI
→ 코드 작성·테스트·PR Explain

GitHub Copilot reviewer
→ 독립 코드 리뷰·댓글
```

구현할 때의 기억이나 자기 판단을 리뷰 결과로 사용하지 않는다.

### 6.2 트리거

`.github/workflows/copilot-auto-review.yml`은 다음 event에서 Copilot reviewer를 요청한다.

- `opened`
- `synchronize`
- `reopened`
- `ready_for_review`

새 Push는 `synchronize`를 발생시키므로 자동 재리뷰 대상이다.

### 6.3 리뷰 결과

리뷰 에이전트는 실제 코드 위치에 inline comment를 남기고 필요하면 review summary를 작성한다.

중요도 기준:

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
PASS
```

자동 리뷰가 실행되지 않았거나 workflow가 실패하면 이를 PASS로 간주하지 않는다.

## 7. 자동 리뷰 반영

자동 리뷰 댓글이 등록된 뒤 구현 담당자 AI가 후속 작업을 수행할 때는 모든 리뷰·댓글을 최신 Head와 대조한다.

### 범위 안에서 자동 반영 가능

- 명확한 기능 오류
- 예외 처리·검증·테스트 누락
- PR 설명과 Diff 불일치
- 정책 재결정이 필요 없는 BLOCKER·MAJOR·MINOR

### Human 판단 필요

- Issue 범위 확장
- 정책·API·DB·상태·권한·트랜잭션 재결정
- 새로운 라이브러리·인프라 도입
- 다른 담당자 계약 변경

실제 수정을 시작하면 연결 Issue를 `status:in-progress`로 전환한다.

수정 뒤에는:

```text
테스트·직접 검증 재실행
→ Commit·Push
→ synchronize event
→ Copilot 자동 재리뷰
```

구현 담당자 AI가 새 리뷰 댓글을 대신 작성하지 않는다.

## 8. PR Human 이해도

### 기본 검토

```text
질문 0개
```

문서·설정·단순 CRUD·기존 패턴 반복에는 Human 이해도 질문을 생성하지 않는다.

### 강화 검토

```text
정확히 3개
```

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 실제 적용 위치·이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

담당자 Human이 직접 답한다. 구현 담당자 AI는 최신 코드와 대조한 `AI 답변 검토`와 `AI 기준 답변`만 작성한다.

## 9. Human Review Checklist

모든 PR에서 공통 체크만 사용한다.

- [ ] 이 PR이 무엇을 왜 변경하는지 이해했다.
- [ ] 변경 후 기본 실행 흐름과 중요한 분기를 이해했다.
- [ ] 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했다.
- [ ] 테스트·검증 결과와 남아 있는 미검증 위험을 확인했다.

PR별 세부 구현 퀴즈를 동적으로 추가하지 않는다.

## 10. 완료 경계

다음까지가 구현 담당자 AI의 책임이다.

```text
Issue 분석·Human 계약 확인
→ 구현·테스트
→ PR Explain
→ Commit·Push·Draft PR
→ 자동 Copilot Review 결과 확인
→ 범위 안 리뷰 지적 수정
→ Push
→ 자동 재리뷰 확인
→ 강화 PR Human 이해도 보완
→ status:final-human-review
```

최신 Head의 자동 AI Review가 실제 생성되지 않았다면 완료로 판단하지 않는다.

Approve와 Merge는 Human이 수행한다.

---
name: bobfull-pr-review
description: BobFull의 모든 PR에서 담당자 AI가 최신 Head, 연결 Issue 계약, 실제 Diff, 테스트·검증 근거와 기존 리뷰를 다시 읽고 중요도 순으로 독립 재검토한 뒤 PR Conversation 댓글에 결과를 남긴다.
---

# BobFull PR AI Review

## 목적

이 Skill은 구현을 끝낸 담당자 AI가 자기 PR을 그대로 신뢰하지 않고 **리뷰 역할로 다시 전환해 최신 근거를 재검토**하도록 표준화한다.

AI Review는 모든 PR에서 필수다. `기본 | 강화` 검토 수준은 Human 이해도 질문 수를 결정할 뿐 AI Review 실행 여부를 바꾸지 않는다.

AI Review는 Human Approve와 Merge를 대체하지 않는다.

## 사용 시점

다음 시점마다 이 Skill을 직접 읽고 적용한다.

- Draft PR 생성 직후
- `PR #번호 검토하라`
- AI Review에서 수정한 Commit을 Push한 뒤
- 새 Commit으로 Head가 변경된 뒤
- Ready 전환 또는 Human 최종 리뷰 요청 전

## 필수 입력

리뷰 전에 다음을 최신 상태로 다시 확보한다.

1. 연결 Issue 본문, 최종 계약 댓글, 현재 `status:*` Label
2. 최신 PR Head SHA와 base 대비 실제 Diff
3. PR 본문의 이해 요약·상세 검증·Human 검토 영역
4. 추가·수정 테스트와 실제 테스트·build·직접 검증·CI 결과
5. 기존 PR Conversation 댓글, 제출된 리뷰, 미해결 리뷰 스레드
6. 변경과 직접 관련된 확정 문서

구현할 때 읽었던 기억이나 이전 Head의 설명만으로 판정하지 않는다.

## 리뷰 순서

다음 순서로 실제 근거를 대조한다.

1. Issue 최종 계약과 실제 Diff가 일치하는가
2. 핵심 정상 흐름과 주요 실패·중복·경계 흐름이 올바른가
3. Transaction, Lock, Event, 외부 I/O, 멱등성, 권한 등 변경에 중요한 경계가 안전한가
4. 예외 처리와 상태 전이가 데이터 정합성을 깨뜨리지 않는가
5. 테스트가 주장한 동작을 실제로 검증하는가
6. `PASS`, `NOT_RUN`, CI 상태와 검증 결과가 과장되지 않았는가
7. PR 이해 요약·Mermaid·주요 개념·코드 읽는 순서가 최신 Head와 일치하는가
8. 기존 리뷰 지적이 최신 Head에서 해결됐는가

단순 문서·설정·CRUD PR도 AI Review를 생략하지 않는다. 다만 중요하지 않은 지적을 억지로 생성하지 않는다.

## 중요도 기준

리뷰 지적은 반드시 높은 중요도부터 정렬한다.

### BLOCKER

Merge하면 안 되는 치명적 문제다.

예:

- 데이터 손실·중복 결제·권한 우회처럼 핵심 정합성 또는 보안이 깨짐
- Issue 핵심 계약과 구현이 정면으로 충돌함
- 필수 기능이 동작하지 않음

### MAJOR

핵심 흐름·설계·실패 처리에 의미 있는 문제가 있어 원칙적으로 수정 후 재검토가 필요하다.

### MINOR

Merge를 직접 막지는 않지만 품질·유지보수성·테스트 정확도를 위해 수정 가치가 있는 문제다.

### SUGGESTION

현재 구현도 허용 가능하며 선택적으로 개선할 수 있는 제안이다.

### PASS

실제 근거를 다시 확인했지만 보고할 BLOCKER·MAJOR·MINOR가 없다.

`PASS`를 만들기 위해 문제를 숨기지 않고, 반대로 리뷰 흔적을 만들기 위해 의미 없는 지적을 생성하지 않는다.

## PR Conversation 댓글 형식

AI Review는 PR 본문만 갱신하고 끝내지 않는다. **각 정식 리뷰 실행마다 PR Conversation 댓글을 남긴다.**

```markdown
## 담당자 AI Review

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
`PASS | 수정 후 재검토 필요`

### 검증 근거
- 확인한 테스트·build·직접 검증·CI와 남은 NOT_RUN
```

내용이 없는 등급은 한 줄 `없음`으로 두거나 생략할 수 있다. 단, 실제 지적은 항상 `BLOCKER → MAJOR → MINOR → SUGGESTION` 순서로 배치한다.

BLOCKER 또는 MAJOR가 있으면 판정은 `수정 후 재검토 필요`다. 수정 후 최신 Head에서 이 Skill을 다시 실행하고 새 리뷰 댓글을 남긴다.

MINOR만 남은 경우 Merge 차단 여부를 과장하지 않고 실제 영향과 선택지를 기록한다.

## PR 본문 기록

PR 본문의 `담당자 AI 검토·수정 기록`에는 최소 다음을 최신 상태로 남긴다.

- 담당자 AI 검토 기준 Head
- 담당자 AI 검토 결과
- 최신 AI Review 댓글 또는 검토 시점
- 확인한 기존 리뷰·댓글
- 반영한 항목
- 반영하지 않은 항목과 이유
- 재실행 검증 결과
- 남은 미검증 위험

PR 댓글의 실제 리뷰 내용을 본문에 장문으로 중복 복사하지 않는다.

## Human 검토와의 경계

- AI Review: 실제 코드·계약·테스트의 결함과 불일치를 찾는다.
- Human 이해도: 강화 검토 PR에서 담당자가 핵심 흐름·개념·설계 판단을 이해했는지 확인한다.
- Human Review Checklist: 리뷰어가 변경 목적, 기본 흐름, 중요한 개념과 검증 결과를 이해했는지 확인한다.
- Human Approve와 Merge: Human 책임이다.

AI가 Human 답변, Checklist 체크, Approve를 대신 작성하지 않는다.

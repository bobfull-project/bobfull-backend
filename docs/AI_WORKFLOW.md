# BobFull AI 협업 워크플로우

## 1. 목적

팀원은 별도 Skill 등록이나 온보딩 명령 없이 Issue 단계에서 다음 명령으로 작업을 시작한다.

```text
Issue #번호 구현하라
```

새 작업이 필요하면 구현 Issue를 만들기 전에 다음 명령으로 초안을 검토한다.

```text
새 Issue 초안 작성하라
이 초안으로 Issue 생성하라
```

첫 명령은 GitHub를 변경하지 않는다. Human이 초안을 확인하고 두 번째 명령으로 명시 승인한 경우에만 실제 Issue를 생성한다.

담당자 AI는 Issue 단계에서 현재 Issue·Human 답변·Issue 댓글·현재 Label을 읽고 다음 단계부터 재개한다. 연결 PR의 Head·Diff·리뷰와 댓글은 PR 단계에서만 읽는다. 담당자 Human은 이해도 답변과 정책 판단을 담당하고, Human 리뷰어는 실제 Diff를 검토하며, Approve와 Merge는 Human이 수행한다.

PR 단계의 검토·리뷰 반영에는 다음 명령을 사용한다.

```text
PR #번호 검토하라
```

다른 팀원의 AI 리뷰는 이 워크플로우의 단계나 필수 조건으로 관리하지 않는다. PR에 어떤 리뷰나 댓글이 등록되면 담당자 AI가 작성 주체와 관계없이 실제 코드 근거를 확인해 반영 여부를 판단한다.

## 2. 전체 흐름

```text
새 작업 필요
→ `새 Issue 초안 작성하라`
→ 중복·확정 범위·제목 규칙 확인
→ 버전 판단 근거와 Issue 템플릿 전체 초안 제시
→ Human 검토
→ `이 초안으로 Issue 생성하라`
→ `Issue #번호 구현하라`
→ 구현·검증·Diff 자체 검토
→ `skills/bobfull-pr-explain/SKILL.md` 직접 읽기·적용
→ 템플릿 전체 구조로 develop 대상 Draft PR 생성
→ PR 담당자 Human의 이해도 답변·Human 리뷰
→ `PR #번호 검토하라`
→ 담당자 AI 검토·범위 안 수정·재검증
→ `status:final-human-review`
→ Human Approve와 Merge
```

Human 답변, Human 리뷰와 외부 리뷰·댓글의 작성 순서는 고정하지 않는다. 담당자 AI는 명령을 받을 때마다 현재 GitHub 상태를 읽고 누적된 입력을 함께 처리한다.

담당자 AI의 PR 검토는 구현 품질을 보완하는 절차이며 독립적인 Human Approve나 Merge 판단을 대체하지 않는다.

## 관련 기록 문서

- [AI Human 검토 기록](AI_휴먼_검토_기록.md): Human이 AI 제안의 누락·위험을 발견하고 계약을 수정한 근거를 기록한다.
- [트러블슈팅 기록](troubleshooting/README.md): 기술 문제의 원인, 후보 해결안, 검증 상태를 기록한다.

### 2.1 새 Issue 초안·승인·생성

초안 단계는 중복 확인·범위와 유형 판단·Issue 템플릿 전체 초안 제시까지이며 GitHub를 변경하지 않는다. Human이 명시 승인하면 생성 직전에 범위·중복·승인된 제목과 본문을 재확인한 뒤 실제 Issue를 생성하고 결과를 보고한다. 범위 판단은 `ISSUE_TITLE_RULES`, 담당자 AI의 체크리스트는 `AI_IMPLEMENTATION_GUIDE`를 따른다.

`.github/ISSUE_TEMPLATE/config.yml`의 `blank_issues_enabled: false`는 웹 UI에서 템플릿 사용을 유도하는 최소 가드레일일 뿐 CLI·API·자동화 도구의 본문 형식까지 강제하지 않는다.

## 3. Issue 단계

### 3.1 Issue 분석

담당자 AI는 Issue, 확정 문서, 관련 코드와 테스트를 확인한다.

- 목적과 범위·제외 범위
- 확정 정책과 미결정 사항
- 정상·실패·경계 흐름
- API·DB·상태·권한·트랜잭션
- 선행 작업과 도메인 의존성
- 완료 조건과 검증 계획
- 문서·Issue·코드 충돌

Human 결정이 필요하면 최초 Human 질문을 Issue 본문에 구체적으로 작성하고
`status:human-answer-required`를 적용한 뒤 구현하지 않는다. Issue 본문의 Human 질문과 답변은 수정하지 않는다.

### 3.2 Human 답변

담당자 Human은 1차 답변과 최종 확인을 직접 작성한다. 1차 답변은 `모르겠습니다`여도 된다. 담당자 AI는 실제 계약·코드·문서·테스트 근거의 AI 기준 답변을 제공하지만, Human 원문과 최종 확인을 대신 작성하지 않는다.

기본 검토는 AI 기준 답변 뒤 Human이 `이해함`을 선택하면 답변 재작성 요구 없이 이해 확인을 끝낸다. 질문 수·난이도·생성 기준, 강화 검토 대상과 직접 작성 이해 근거 조건은 `AI_REVIEW_GUIDE`를 단일 기준으로 따른다. `추가 설명 필요`는 AI 기준 답변을 보완하고, `동의하지 않음`은 다음 단계로 진행하지 않고 Human 판단을 요청한다.

### 3.3 담당자 AI 검증·최종 계약 기록과 자동 진행

Human이 답변을 작성한 뒤 같은 명령을 다시 받으면 담당자 AI는 Human 답변을 문서·코드와 대조한다.

- 질문별 `일치 | 보완 필요 | 미작성`과 근거를 대화창과 별도 Issue 댓글에 기록한다.
- Human 답변 원문과 Issue 본문 전체를 수정하거나 재작성하지 않는다.
- 답변을 반영한 최종 계약에는 범위·정책·흐름·완료 조건·검증 계획·구현 진행 또는 중단 판정을 포함한다.
- 충돌이나 미결정 사항이 남으면 `status:human-answer-required`를 적용하고 중단한다.
- 필수 답변이 모두 있고 충돌·미결정 사항이 없으면 `status:in-progress`를 적용한 같은 실행에서 구현을 계속한다.

Human 최종 확인만 후속 대화로 도착해도 구현은 자동 시작하지 않는다. 구현은 Human이 `Issue #번호 구현하라`를 실행 또는 재실행한 맥락에서만 시작한다.

구현 중 새로 발생한 정책 결정·범위 변경·계약 충돌의 추가 질문만 Issue 댓글에 기록한다.

Issue 본문은 목적·범위·완료 조건·Human 질문과 답변을 보존하고, AI 검토·최종 계약·구현 기록은 댓글에 남긴다.
실행 상태는 다음 GitHub Label로 관리한다.

```text
status:human-answer-required
status:in-progress
status:final-human-review
```

실제 상태의 유일한 기준은 GitHub `status:*` Label이며, Issue 본문의 상태 문자열은 비권위 정보다.
상태 전환 시 기존 `status:*` Label을 모두 제거한 뒤 새 상태 Label 하나만 적용한다.

## 4. 구현과 Draft PR

답변 검토와 계약 확인을 마쳐 `status:in-progress`가 적용되면 담당자 AI는 같은 실행에서 다음을 수행한다.

```text
현재 브랜치·작업 트리 확인
→ 대상 Issue 기존 브랜치 확인
→ 기존 브랜치가 있으면 전환
→ 없으면 미커밋 변경의 소속 확인
→ 작업 트리가 깨끗하면 최신 develop 전환·갱신
→ 최신 develop에서 Issue 전용 브랜치 생성
→ 브랜치 재확인
→ 최소 변경 계획
→ 구현
→ 테스트·직접 검증
→ 실제 Diff 자체 검토
→ PR Human 이해도 질문 작성
→ Commit·Push
→ develop 대상 Draft PR 생성
```

필수 원칙:

- `AGENTS.md` 브랜치 안전 규칙에 따라 `main`, `master`, `develop`에서는 직접 수정하지 않고, 다른 Issue의 작업 브랜치에서도 새 작업을 시작하지 않으며, 새 Issue 브랜치는 항상 최신 `develop` 기준으로 생성하고 브랜치 재확인 후에만 파일을 수정한다. 다른 Issue의 미커밋 변경은 임의로 이동하지 않는다.
- Issue 댓글에 기록된 최종 계약 범위만 구현한다.
- 실행하지 않은 테스트를 `PASS`로 기록하지 않는다.
- build 실패를 성공으로 표현하지 않는다.
- 정책·API·DB 재결정이 필요하면 중단한다.
- PR에는 실제 변경·실행 결과·미검증 위험만 기록한다.
- PR 템플릿에 담당자 Human 이해도와 PR별 Human Review Checklist의 빈 구조를 포함한다.
- Draft PR 생성 전 `skills/bobfull-pr-explain/SKILL.md`를 직접 읽고 적용한다. Skill이 요구하는 최신 템플릿·연결 Issue 계약·실제 Diff·검증 근거를 확보하지 못하면 PR 내용을 추측해 작성하지 않는다.
- Explain Diff는 변경 전 동작·문제, 핵심 아이디어, 변경 후 실행 흐름, 코드 확인 순서, 예외·트레이드오프·제한사항과 실제 검증 근거를 기록한다. 의미 있는 실행 흐름이 있는 기능 PR은 최신 Head와 일치하는 Mermaid 다이어그램을 최소 1개 포함하고, 실행 흐름이 없는 단순 변경만 `해당 없음`과 이유로 생략할 수 있다.
- 새 Commit 뒤와 `PR #번호 검토하라`, Ready 전 최종 확인에서는 Skill을 다시 적용해 PR 설명·다이어그램·Checklist를 최신 Head에 맞춘다.
- `담당자 AI 검토·수정 기록`과 PR 본문 마지막의 Human Review Checklist를 삭제하거나 축약하지 않는다. Ready 전환, Approve와 Merge는 Human 책임이다.

## 5. PR Human 입력

### 5.1 담당자 Human 이해도

Draft PR 생성 후 PR 담당자는 실제 Diff와 테스트 결과를 읽고 PR 본문의 질문에 직접 답한다.

담당자 AI는 최신 코드와 1차 답변을 대조해 각 질문 아래에 다음을 작성한다.

- `AI 답변 검토`: `일치 | 보완 필요 | 미작성`
- `AI 기준 답변`: 실제 코드 흐름·예외·테스트 근거를 포함한 현재 확정 계약과 구현 기준의 설명

담당자 AI는 Human 답변 원문을 수정하거나 대신 작성하지 않는다. 답변이 아직 없어도 코드 검토는 진행할 수 있지만, Merge 전에는 답변과 AI 검토가 필요하다.

### 5.2 Human Review Checklist와 댓글

Draft PR 생성 시 담당자 AI는 PR 본문 마지막에 연결 Issue의 최종 결정·완료 조건, 최신 실제 Diff, 추가·수정 테스트, 실제 테스트 결과와 PR 범위를 근거로 PR별 `Human Review Checklist`를 작성한다. 구현 확인과 테스트 확인을 분리하고, 실제 확인 가능한 동작 또는 검증을 한 항목씩 작성한다.

PR 작성자가 아닌 Human 리뷰어는 PR 본문을 수정하지 않고 체크리스트를 참고하거나 복사해 PR 댓글에 체크 여부와 의견을 직접 작성한다. 댓글의 리뷰어와 리뷰 시각은 GitHub 메타데이터를 사용하며, 기준 Head SHA는 기록하거나 추정하지 않는다. 새 Commit 이후 정식 Approve의 유효성은 GitHub Branch Ruleset의 `dismiss_stale_reviews_on_push: true` 설정으로 관리한다.

Human 리뷰 원문은 담당자 AI가 대신 작성하거나 덮어쓰지 않는다. 담당자 AI는 `PR #번호 검토하라` 명령마다 최신 Diff를 기준으로 댓글 의견을 다시 대조한다.

## 6. 담당자 AI PR 검토

연결된 PR이 존재하면 `PR #번호 검토하라` 명령으로 담당자 AI가 현재 Head를 검토한다.
PR 번호로 연결된 모든 Issue, 각 Issue 댓글의 최종 계약과 현재 `status:*` Label을 먼저 확인한다.

### 최소 검토 기준

- 최신 Head Commit SHA
- 최신 Head의 실제 Diff
- Issue 또는 PR에서 확인되는 작업 범위

위 항목이 있으면 테스트·Human 입력이 미완료여도 확인 가능한 범위까지 검토한다.

검토 대상:

- Issue 댓글의 최종 계약과 현재 상태 Label
- 최종 계약 이후 변경 여부
- PR 설명과 실제 Diff
- 요청부터 응답·저장까지 코드 흐름
- 입력 검증·예외 처리
- 권한·소유권·트랜잭션·정합성·동시성
- 테스트·build·직접 검증·CI 증거
- 미검증 범위와 남은 위험
- 담당자 Human 답변과 AI 보완 필요 사항
- PR 댓글의 Human 리뷰 의견과 최신 Diff의 대조
- PR에 등록된 모든 리뷰·댓글

테스트 실패·미실행, `FAIL`·`HOLD`·`NOT_RUN`, Human 답변 미작성과 Human 리뷰 댓글 미작성은 검토 중단 사유가 아니다. 현재 상태와 위험으로 기록한다.

담당자 AI 검토 결과는 다음 중 하나로 기록한다.

- `수정 필요`
- `확인 필요`
- `특이사항 없음`

이 결과는 Merge 가능 판정이 아니다.

## 7. PR 리뷰·댓글 판단과 수정

PR에 등록된 리뷰·댓글은 작성 주체와 관계없이 참고 입력이다. 특정 팀원 또는 다른 AI의 리뷰가 있어야 다음 단계로 진행하는 구조가 아니다.

담당자 AI는 각 의견을 실제 Issue 계약과 코드로 검증한다.

PR을 읽고 보고만 할 때는 기존 Label을 유지한다. 실제 파일 수정을 시작할 때만 연결된 모든 Issue에서
기존 `status:*` Label을 제거하고 `status:in-progress` 하나를 적용한다. 수정·검증·Push·최신 Head
자체 검토가 끝나면 연결된 모든 Issue에서 `status:final-human-review` 하나만 적용한다.

### 자동 반영 가능한 항목

- Issue 계약 안의 명확한 기능 오류
- 예외 처리·검증·테스트 누락
- PR 설명과 Diff 불일치
- 정책 재결정이 필요 없는 BLOCKER·MAJOR·MINOR
- Human 리뷰에서 실제 코드 근거가 확인된 결함

### 자동 반영하지 않는 항목

- 근거를 확인할 수 없는 의견
- 단순 질문이나 설명 요청
- Issue 범위 확장
- 정책·API·DB·상태·권한·트랜잭션 재결정
- 다른 담당자 계약 변경
- 새로운 라이브러리·인프라 도입

담당자 AI는 반영한 항목과 반영하지 않은 항목의 이유를 PR에 기록한다. 정책 결정이 필요한 항목은 Human에게 보고한다.

수정 후에는 영향받는 테스트·직접 검증·전체 build를 다시 실행하고 실제 Diff, PR 본문, 검증 증거와 최신 Head를 갱신한다.

## 8. 최신 Head 재검토

수정 Commit이 올라오면 담당자 AI는 이전 검토 결과를 그대로 재사용하지 않는다.

- 현재 Head SHA와 수정 Diff 확인
- 기존 지적 해결 여부 확인
- 영향받은 테스트·build·직접 검증·CI 확인
- 새 결함과 PR 설명 불일치 확인
- 담당자 Human 답변과 AI 기준 답변 재대조
- PR 댓글의 Human 리뷰 의견과 최신 Diff의 대조
- 남은 리뷰·댓글 확인

해결되지 않은 항목과 미검증 위험을 최신 상태로 다시 기록한다.

## 9. 최종 Human 확인과 Merge

담당자 AI가 연결된 모든 Issue에 `status:final-human-review`를 적용하고 Issue 댓글과 PR에 최종 검토 결과를 기록하면,
Human이 실제 코드, 테스트·CI, Human 입력과 리뷰 반영 결과를 확인한다.

다음을 충족하지 않으면 Merge 준비 완료로 판단하지 않는다.

- 담당자 Human 이해도 답변과 담당자 AI 보완 확인
- PR 댓글의 Human 리뷰 의견과 최신 Diff의 대조 완료
- 필수 테스트·build·직접 검증 결과 확인
- 최신 Head 기준 담당자 AI 검토 완료
- 해결되지 않은 BLOCKER 없음
- GitHub Rules의 Human Approve 조건 충족

PR 작성자가 Merge하며 AI는 Approve와 Merge를 수행하지 않는다.

## 10. 실행 재개 원칙

담당자 AI는 Human이 Issue를 수정하거나 Issue 본문에 답변을 작성한 뒤 자동으로 실행되지 않는다.
Issue 단계 재개에는 다음 명령을 사용한다.

```text
Issue #번호 구현하라
```

담당자 AI는 처음부터 반복하지 않고 실제 Issue·PR 상태를 읽어 다음 단계부터 재개한다.

PR 답변 또는 Human 리뷰가 작성된 뒤에는 다음 명령을 사용한다.

```text
PR #번호 검토하라
```

PR 단계에서 `Issue #번호 구현하라`를 다시 입력하도록 요구하지 않는다.

# BobFull 담당자 AI 실행 가이드

## 1. 실행 명령

Skill 등록·자동 매칭을 지원하는 환경에서는 선택적으로 등록할 수 있다. 그러나 등록 여부는 작업 시작 조건이 아니다.
새 Issue 최초 처리 때 담당자 AI는 `AGENTS.md` 지시에 따라 저장소의 `skills/bobfull-onboarding/SKILL.md`를 직접 읽고 적용해 필요한 문서를 선택한다.

Issue 단계에는 다음 명령을 사용한다.

```text
Issue #번호 구현하라
```

새 작업의 Issue 생성에는 다음 명령을 사용한다.

```text
새 Issue 초안 작성하라
이 초안으로 Issue 생성하라
```

초안 작성은 GitHub 변경 없이 대화창에서만 수행하며, Human이 승인 명령을 명시한 뒤에만 실제 Issue를 생성한다.

담당자 AI는 Issue 명령을 받을 때마다 GitHub Issue, Human 답변, Issue 댓글의 최종 계약, 현재 `status:*` Label, 현재 브랜치와 작업 트리를 다시 읽고 Issue 단계의 다음 작업부터 재개한다. 연결 PR의 최신 Head·Diff·리뷰·댓글 검토와 수정은 `PR #번호 검토하라`에서 수행한다.

## 2. 상태별 자동 동작

### A. Issue 분석과 Human 질문

조건:

- Issue에 Human 답변이 없음

동작:

1. 확정 문서·Issue·코드·테스트를 분석한다.
2. 새 Issue 최초 처리라면 `skills/bobfull-onboarding/SKILL.md`를 직접 읽고 필요한 기준 문서를 선택한다.
3. 충돌과 미결정 사항을 확인한다.
4. 최초 구현에 필요한 질문을 Issue 본문의 `Human 이해도`에 작성한다. Issue 단계 질문 난이도·수·자기 검증 기준은 `docs/AI_REVIEW_GUIDE.md`를 따른다.
5. `status:human-answer-required`를 적용한다.
6. 구현하지 않고 중단한다.

### B. Human 답변 검증·최종 계약 기록과 자동 구현 진행

조건:

- Human이 Issue 단계의 필수 1차 답변과 최종 확인을 작성함

동작:

1. Human 1차 답변을 문서·코드와 대조하고 실제 근거의 `AI 답변 검토`와 `AI 기준 답변`을 작성한다.
2. Human 답변 원문과 최종 확인을 덮어쓰거나 대리 작성하지 않는다.
3. `추가 설명 필요`면 근거를 보완하고, `동의하지 않음`, 답변 누락·모순, 문서 충돌, 정책 결정, 범위 변경, 보안·권한·상태 계약 불명확이면 `status:human-answer-required`를 적용하고 중단한다.
4. 위 문제가 없고 Human이 `Issue #번호 구현하라`를 실행 또는 재실행한 경우에만 `status:in-progress`를 적용하고 같은 실행에서 구현을 계속한다.

### C. 구현·Draft PR

구현 동작:

1. `git branch --show-current`와 작업 트리 상태로 현재 브랜치를 확인한다. 보호 브랜치나 다른 Issue 브랜치에서는 직접 수정하지 않는다.
2. 대상 Issue에 연결된 기존 작업 브랜치가 있으면 해당 브랜치로 전환한다.
3. 기존 작업 브랜치가 없고 작업 트리가 깨끗하면 최신 `develop`으로 전환·갱신한다.
4. 최신 `develop`에서 `docs/GITHUB_RULES.md`에 맞춰 Issue 전용 브랜치(기본 형식 `<type>/<issue-number>-<summary>`)를 생성한다.
5. 다른 Issue의 작업 브랜치에서 미커밋 변경이 발견되면 임의 이동하지 않고 중단해 Human 판단을 요청한다.
6. 브랜치 생성·전환 후 대상 Issue 브랜치인지 다시 확인한다.
7. Issue 댓글에 기록한 최종 계약 범위의 최소 변경 계획을 세운다.
8. 강화 검토 대상이면 `docs/AI_REVIEW_GUIDE.md` 기준에 따라 코드 작성 전에 책임 클래스, 상태 변경 위치, 트랜잭션 범위, 실패 처리 방식의 초안을 제시한다.
9. 담당자 Human이 초안을 확인하거나 수정 의견을 남긴 뒤 구현한다. 정책·API·DB·권한·트랜잭션 재결정이 필요하면 중단한다.
10. 코드·테스트·필요 문서를 구현한다.
11. 테스트와 직접 검증을 실제로 실행한다.
12. 전체 실제 Diff를 자체 검토한다.
13. Draft PR 생성 전에 `skills/bobfull-pr-explain/SKILL.md`를 직접 읽고 적용한다.
14. Issue 관련 변경만 Commit·Push한다.
15. develop 대상 Draft PR을 생성한다.
16. Draft PR 생성 직후 `skills/bobfull-pr-review/SKILL.md`를 직접 읽고 최신 Head·Issue 계약·실제 Diff·테스트·검증 근거를 다시 검토한다.
17. AI Review 결과를 PR Conversation 댓글에 `BLOCKER → MAJOR → MINOR → SUGGESTION` 순으로 남긴다. 지적할 결함이 없으면 `PASS`를 기록한다.
18. BLOCKER 또는 MAJOR가 있으면 범위 안에서 수정·재검증·Push한 뒤 최신 Head에서 AI Review를 다시 실행하고 새 댓글을 남긴다.
19. 강화 검토 PR에만 Human 이해도 질문 정확히 3개를 유지한다. 기본 검토 PR은 질문을 생성하지 않는다.
20. 최신 Head AI Review와 필수 검증이 끝나면 `status:final-human-review`를 적용하고 Human 최종 리뷰 사항을 기록한다.

강화 검토 대상이었다면 PR Conversation 댓글에 다음 형식으로 구현 전 설계 확인 기록을 남긴다.

```markdown
## 구현 전 설계 확인 기록

- 책임 클래스:
- 상태 변경 위치:
- 트랜잭션 범위:
- 실패 처리 방식:
- 담당자 Human 확인: `확인 | 수정 요청`
- 구현 중 달라진 점: `없음 | 변경 내용과 이유`
```

Draft PR 본문은 `bobfull-pr-explain` Skill의 실제 근거 수집·PR 이해 요약·상세 검증·Mermaid 선택·최신 Head 대조 절차를 따른다. 단순 변경에 불필요한 Mermaid·주요 개념·트러블슈팅은 이유와 함께 `해당 없음`으로 기록한다. 상세 검증의 긴 정보만 `<details>`로 접고 `BLOCKER`·`FAIL`·`NOT_RUN`·미검증 위험은 접힌 영역에만 두지 않는다. 실행하지 않은 검증은 `NOT_RUN | 미실행`과 이유·한계를 기록한다.

### 새 Issue 초안·생성

초안·생성의 전체 생명주기와 승인 경계는 `AI_WORKFLOW`, 범위·유형 판단 근거는 `ISSUE_TITLE_RULES`를 따른다.

1. 실제 GitHub Issue와 확정 문서에서 동일·유사 작업을 검색한다.
2. 판단 근거와 `.github/ISSUE_TEMPLATE/feature.md` 전체 구조로 초안을 작성한다.
3. 초안 단계에서는 GitHub를 변경하지 않고 대화창에만 제시한다.
4. Human의 명시 승인 뒤 생성 직전 범위·중복·승인된 제목·본문을 재확인하고 생성한다.

구현 중 계약이 변경되거나 새로운 정책 결정이 필요해지면 구현을 중단하고 `status:human-answer-required`를 적용한 뒤 구현 중 새로 발생한 추가 Human 질문만 댓글에 기록한다.

### D. 연결된 PR 검토와 리뷰 반영

조건:

- 대상 Issue에 연결된 PR이 존재함

시작 명령:

```text
PR #번호 검토하라
```

모든 PR에서 담당자 AI Review는 필수다. 다른 팀원 AI 또는 외부 AI 리뷰 존재 여부는 시작 조건이 아니다.

동작:

1. 최신 Head SHA와 실제 Diff를 확인한다.
2. PR 번호로 연결된 모든 Issue, 각 Issue 댓글의 최종 계약, 현재 상태 Label과 계약 이후 변경 여부를 확인한다.
3. `skills/bobfull-pr-explain/SKILL.md`를 다시 적용해 PR 이해 요약·상세 검증·Mermaid가 최신 Head와 일치하는지 확인한다.
4. `skills/bobfull-pr-review/SKILL.md`를 적용해 Issue 계약·실제 Diff·테스트·기존 리뷰를 독립적으로 다시 검토한다.
5. 실제 지적을 `BLOCKER → MAJOR → MINOR → SUGGESTION` 순으로 PR Conversation 댓글에 남긴다.
6. 강화 검토 PR이면 Human 3문항 답변을 실제 코드와 대조하고 `AI 답변 검토`와 `AI 기준 답변`을 작성한다. 기본 검토 PR에는 이 단계를 요구하지 않는다.
7. Human Review Checklist와 PR 댓글의 Human 리뷰 의견을 최신 Diff와 대조한다.
8. PR에 등록된 모든 리뷰·댓글을 작성 주체와 관계없이 읽는다.
9. 실제 코드 근거가 있는 범위 안 결함만 수정한다. 수정 없이 보고만 하면 기존 Label을 유지한다.
10. 실제 파일 수정을 시작하면 연결된 모든 Issue에서 기존 `status:*` Label을 제거하고 `status:in-progress` 하나를 적용한다.
11. 단순 질문·설명 요청은 확인 결과로 정리하고 정책·API·DB·권한·트랜잭션 재결정은 Human에게 보고한다.
12. 영향받는 테스트·직접 검증·전체 build를 다시 실행한다.
13. 실제 Diff, PR 본문, 검증 증거와 최신 Head를 갱신한다.
14. 수정이 있었다면 최신 Head에서 `bobfull-pr-review`를 다시 실행해 새 AI Review 댓글을 남긴다.
15. 최신 AI Review에 해결되지 않은 BLOCKER가 없고 필수 검증이 끝나면 `status:final-human-review`를 적용한다.

## 3. 구현 원칙

- 한 번에 하나의 Issue만 처리한다.
- Issue 댓글에 기록된 최신 최종 계약과 `status:in-progress`를 기준으로 구현한다.
- 실제 상태는 GitHub `status:*` Label 하나로만 관리한다.
- 존재하지 않는 코드·설정·정책을 추정하지 않는다.
- 기존 패키지 구조와 코드 컨벤션을 유지한다.
- 범위 밖 기능·리팩터링·새 기술을 추가하지 않는다.
- 인증 사용자 ID를 클라이언트 입력값으로 신뢰하지 않는다.
- 상태·금액·수량·권한은 실패 후 결과까지 확인한다.
- 일반 컴파일 오류와 테스트 오류는 범위 안에서 수정한다.
- 정책·구조 재결정이 필요한 오류는 Human에게 보고한다.
- 리뷰·댓글은 작성 주체가 아니라 실제 근거로 판단한다.
- 담당자 AI Review는 모든 PR의 필수 절차다. 다른 팀원 또는 외부 AI Review는 선택적 추가 입력이다.

## 4. 테스트와 검증

`docs/TEST_CONVENTION.md`를 따른다.

- 한글 설명형 테스트명
- `given → when → then`
- 완료 조건과 실제 실패 위험에 연결된 테스트
- 실제 실행 명령과 환경
- `PASS | FAIL | HOLD | NOT_RUN`
- 통과 건수 또는 실패 로그
- 직접 검증 결과
- 보장 범위와 미검증 범위

실행하지 않은 테스트를 `PASS`로 기록하지 않는다. 전체 build가 실패하면 성공 또는 완료로 표현하지 않는다.

## 5. 실제 Diff 자체 검토

Commit 전과 PR 처리 시 다음을 확인한다.

- Issue 범위 밖 변경
- 요청부터 응답·저장까지 실제 흐름
- 입력 검증과 예외 처리
- 권한·소유권
- 트랜잭션·Rollback·부분 성공
- 상태·금액·수량 정합성
- 중복·동시 요청 위험
- 테스트와 완료 조건 연결
- 문서·API·DB 변경 반영
- 비밀정보·개인정보 포함 여부

Commit 전 자체 검토는 Draft PR 이후의 `bobfull-pr-review`를 대체하지 않는다.

## 6. Human 입력

### 6.1 담당자 Human 이해도

Issue 단계 Human 질문 정책과 PR 단계 Human 이해도 정책을 구분한다.

PR 단계에서는 검토 수준에 따라 다음을 적용한다.

- `기본`: 질문 0개
- `강화`: 질문 정확히 3개

강화 3문항은 다음 세 축으로 작성한다.

1. 핵심 실행 흐름과 주요 분기
2. 가장 중요한 기술 개념과 적용 위치·이유
3. 설계 선택 이유, 주요 실패 처리와 남은 한계

담당자 Human은 1차 답변과 최종 확인을 직접 작성한다. 담당자 AI는 실제 코드와 대조한 `AI 답변 검토`와 `AI 기준 답변`만 작성한다.

### 6.2 Human Review Checklist와 댓글

PR마다 세부 구현 질문을 동적으로 생성하지 않는다. 템플릿의 공통 Human Review Checklist를 유지한다.

- 이 PR이 무엇을 왜 변경하는지 이해했는가
- 변경 후 기본 실행 흐름과 중요한 분기를 이해했는가
- 중요한 기술 개념이 있다면 어디에 왜 적용됐는지 이해했는가
- 테스트·검증 결과와 남아 있는 미검증 위험을 확인했는가

Human 리뷰어는 PR 본문을 수정하지 않고 체크리스트를 참고해 PR 댓글에 체크 여부와 의견을 직접 작성한다. 담당자 AI는 Human 리뷰 원문을 작성하거나 덮어쓰지 않는다.

## 7. PR 리뷰·댓글 분류

담당자 AI Review 댓글은 모든 PR의 필수 기록이다. 다른 리뷰·댓글은 추가 참고 입력이다.

담당자 AI Review의 중요도는 다음 순서로 사용한다.

```text
BLOCKER
MAJOR
MINOR
SUGGESTION
PASS
```

### 자동 수정 가능한 항목

- Issue 계약 안의 명확한 기능 오류
- 예외 처리·검증·테스트 누락
- PR 설명·Diff 불일치
- 정책 재결정이 필요 없는 BLOCKER·MAJOR·MINOR
- Human 리뷰에서 실제 코드 근거가 확인된 결함

### 자동 수정하지 않는 항목

- 근거를 확인할 수 없는 의견
- 단순 질문·설명 요청
- Issue 범위 확장
- 정책·API·DB·상태·권한·트랜잭션 재결정
- 다른 담당자 계약 변경
- 새로운 라이브러리·인프라 도입

반영 여부와 이유를 PR의 `담당자 AI 검토·수정 기록`에 남긴다.

## 8. Draft PR 기록

PR에는 실제 확인한 내용만 기록한다.

- 관련 Issue와 검토 수준
- Issue 댓글의 최종 계약과 현재 상태 Label
- PR 이해 요약과 코드 읽는 순서
- 주요 변경과 제외 범위
- 완료 조건별 구현·검증 증거
- 실제 테스트·build·직접 검증 결과
- 최신 Commit SHA와 CI 상태
- 미검증 범위와 남은 위험
- 기본 검토면 Human 이해도 질문 없음, 강화 검토면 정확히 3문항
- 공통 Human Review Checklist
- 최신 담당자 AI Review 기준 Head·결과·댓글

## 9. 즉시 중단 조건

- 문서·Issue·코드가 충돌함
- Human 답변이 모호하거나 미결정 사항이 남음
- 정책·API·DB·상태·권한·트랜잭션 재결정이 필요함
- 다른 담당자 계약 변경이 필요함
- 새로운 라이브러리나 인프라 결정이 필요함
- 데이터 손실·정합성·보안 위험이 있음
- 핵심 검증 환경이나 권한이 없음
- Issue 범위 밖 변경이 필요함

이미 존재하는 PR은 확인 가능한 최신 Diff까지 AI Review를 수행하고 중단 원인과 Human 결정 필요 사항을 보고한다.

## 10. 완료 경계

담당자 AI는 다음까지 수행한다.

```text
Issue 분석·Human 답변 검토·Issue 댓글 최종 계약 기록
→ 구현·테스트
→ Commit·Push·Draft PR
→ PR Explain 최신 Head 대조
→ 담당자 AI Review·PR 댓글
→ 범위 안 지적 수정·재검증·Push
→ 최신 Head AI Review 재실행
→ 강화 검토면 Human 3문항 대조
→ Human Review 의견 반영 확인
```

Approve와 Merge는 Human이 수행한다.

# BobFull AI 작업 진입점

이 파일은 BobFull 저장소에서 사용하는 담당자 AI 에이전트의 공통 실행 규칙이다.

담당자 AI는 Issue 정리·구현·검증·PR 자체 검토·리뷰 반영을 수행한다. Human은 이해도 답변, Human 리뷰, 정책 판단, Approve와 최종 Merge를 책임진다.

## 1. AI 환경별 최초 1회 Skill 등록

각 팀원은 자신이 사용하는 Codex·Claude Code·AI 에이전트 환경에
[`skills/bobfull-onboarding/SKILL.md`](./skills/bobfull-onboarding/SKILL.md)를 최초 1회 등록한다.

- 저장소에 Skill 원본 파일이 있다는 사실만으로 해당 AI 환경에 자동 등록·적용됐다고 간주하지 않는다.
- Skill 등록은 환경별 최초 1회 작업이며, Issue마다 팀원이 반복하지 않는다.
- 등록 여부를 확인할 수 없거나 해당 환경이 Skill 자동 적용을 지원하지 않을 때만 다음 대체 명령을 사용한다.

```text
skills/bobfull-onboarding/SKILL.md를 읽고
Issue #번호를 대상으로 BobFull 온보딩을 수행하라.
```

## 2. 담당자 AI 실행 명령과 새 Issue 최초 처리

팀원은 새 Issue와 기존 Issue 모두 다음 명령만 사용한다.

```text
Issue #번호 구현하라
```

담당자 AI가 새로운 Issue를 처음 처리하면 다음 순서로 동작한다.

```text
AGENTS.md 확인
→ 대상 Issue·연결 PR과 현재 상태 확인
→ skills/bobfull-onboarding/SKILL.md 적용
→ 대상 Issue에 직접 필요한 기준 문서만 선택
→ 관련 코드·테스트·브랜치 상태 확인
→ 문서·Issue·코드 충돌 확인
→ 현재 Issue 상태에 맞는 단계 수행
```

온보딩 Skill은 대상 Issue·현재 상태 확인, 필요한 기준 문서 선택, 불필요 문서 제외,
직접 충돌 확인, 현재 수행 가능한 단계와 다음 Human 게이트 판단만 담당한다.
구현·코드 리뷰·Merge를 직접 수행하는 별도 워크플로우가 아니다.

최상위 `README.md`는 면접관·채용 담당자·외부 방문자·팀원 Human을 위한 프로젝트 소개 문서다.
담당자 AI는 README를 정책, API, DB, 상태, 인증·권한, 트랜잭션, Issue 범위,
AI 상태 흐름, 승인·Merge 조건의 판단 근거로 사용하지 않는다.
실제 판단 근거는 `AGENTS.md`, 대상 Issue, onboarding Skill이 선택한 기준 문서, 실제 코드와 테스트다.

## 3. 같은 Issue 재처리

같은 Issue에 다시 `Issue #번호 구현하라`를 입력하면 담당자 AI는 최초 온보딩을 형식적으로 반복하지 않는다.
실제 GitHub Issue, 연결된 PR, 최신 Head, Human 답변, 리뷰·댓글, 테스트·build·CI 결과,
현재 브랜치와 작업 트리, 승인 이후 계약 변경 여부를 다시 확인한 뒤 현재 단계의 다음 작업부터 재개한다.

Issue 또는 계약이 크게 변경되어 필요한 기준 문서가 달라진 경우에만 onboarding Skill 기준으로 문서를 다시 선택한다.

Human이 Issue 또는 PR에 답변하거나 리뷰를 작성한 뒤에도 같은 명령을 다시 입력한다.

별도의 다른 팀원 AI 리뷰는 워크플로우 단계나 완료 조건으로 사용하지 않는다. PR에 리뷰·댓글이 등록되면 작성 주체와 관계없이 담당자 AI가 실제 코드 근거를 확인해 반영 여부를 판단한다.

## 4. `Issue #번호 구현하라` 상태별 동작

현재 실행 상태는 Human 답변이 포함된 Issue 본문이 아니라 GitHub Label로 기록한다.

| Label | 의미 |
|---|---|
| `status:human-answer-required` | Human 답변 누락·불명확, 문서 충돌, 정책 결정 또는 범위 변경으로 구현을 중단한 상태 |
| `status:in-progress` | Human 답변 검토와 계약 확인을 마치고 구현·검증·PR 갱신을 진행하는 상태 |
| `status:final-human-review` | 최신 Head의 구현·검증·AI 검토를 마쳐 Human 최종 리뷰를 기다리는 상태 |

Issue 본문은 목적·범위·완료 조건·Human 질문과 답변을 보존한다.
AI 답변 검토·보완 설명·최종 계약·구현 착수와 완료 기록은 별도 Issue 댓글에 남긴다.

| 현재 조건 | 담당자 AI 동작 |
|---|---|
| 필수 Human 답변이 없음 | Issue·문서·코드를 분석하고 질문을 댓글로 기록한 뒤 `status:human-answer-required`를 적용하고 중단 |
| Human 답변이 모두 있음 | 답변을 문서·코드와 대조하고, 질문별 검토·보완 설명·최종 계약·구현 진행 또는 중단 판정을 대화창과 Issue 댓글에 기록 |
| 답변·계약에 충돌이나 미결정 사항이 없음 | 같은 `Issue #번호 구현하라` 실행에서 `status:in-progress`를 적용하고 구현·테스트·Diff 검토·Commit·Push·Draft PR 갱신 진행 |
| 정책·API·DB 재결정, 답변 불명확, 보안·권한·상태 계약 불명확, 범위 변경이 필요함 | `status:human-answer-required`를 적용하고 Human 질문을 댓글에 기록한 뒤 중단 |
| 연결된 PR이 존재함 | 최신 Head·실제 Diff·테스트 증거·담당자 Human 답변·Human 리뷰·모든 PR 리뷰와 댓글을 확인하고 범위 안 지적을 수정·재검증·Push |
| 담당자 AI 검토·수정·재검증 완료 | `status:final-human-review`를 적용하고 Human 최종 리뷰가 필요한 사항을 Issue 댓글과 PR에 기록 |

Human이 답변을 작성한 뒤 `Issue #번호 구현하라`를 입력하는 것은 답변 검토와,
충돌이 없을 때 같은 실행의 구현 진행을 요청하는 신호다. 별도의 `AI_FINALIZED → HUMAN_APPROVED`
명령 왕복은 사용하지 않는다. Ready 전환, Approve와 Merge는 계속 Human 책임이다.

## 5. PR Human 입력과 담당자 AI 검토

PR 담당자는 PR 본문의 `Human 이해도` 질문에 직접 답한다.

담당자 AI는 Human 답변을 최신 코드와 대조하고 다음 항목만 작성한다.

- `AI 답변 검토`: `일치 | 보완 필요 | 미작성`
- `AI 보완 설명`: 코드 흐름과 테스트 근거

담당자 AI는 Human 답변 원문을 대신 작성하거나 덮어쓰지 않는다.

PR 작성자가 아닌 Human 리뷰어는 PR 템플릿의 `Human 리뷰`에 다음을 작성한다.

- 리뷰어
- 기준 Head SHA
- 리뷰 시각
- 수정 후 재확인 상태
- 내가 이해한 구현 흐름
- 이해되지 않거나 추가 설명이 필요한 부분
- 문제로 보이거나 다시 확인할 부분

새 Commit으로 Head가 바뀌면 기존 Human 리뷰의 재확인 상태는 `필요`로 본다.

PR에 등록된 리뷰·댓글은 공식 선행 단계가 아니다. 담당자 AI는 작성 주체와 관계없이 다음처럼 처리한다.

- 실제 코드 근거가 있는 범위 안 결함: 수정·재검증
- 설명 또는 확인 요청: 답변 또는 확인 결과 정리
- 정책·API·DB·권한·트랜잭션 재결정: Human 판단 요청
- 근거 없음·범위 밖 제안: 반영하지 않고 이유 기록

## 6. 문서 라우팅

| 작업 | 기준 문서 |
|---|---|
| 프로젝트 정책·버전·역할 | `docs/PROJECT_CONTEXT.md` |
| 실제 HTTP API 계약 | `docs/BOBFULL_API_SPEC_COMPLETE.md` |
| 관계형 데이터 모델·정합성 제약 | `docs/ERD.md` |
| 논리 구성 요소·책임 경계 | `docs/ARCHITECTURE.md` |
| 중요한 기술·구조 결정 기록 | `docs/adr/README.md` |
| AI 전체 절차 | `docs/AI_WORKFLOW.md` |
| 담당자 AI 실행 | `docs/AI_IMPLEMENTATION_GUIDE.md` |
| 담당자 AI PR 검토·리뷰 반영 | `docs/AI_REVIEW_GUIDE.md` |
| 코드 작성 | `docs/CODE_CONVENTION.md` |
| 테스트·증거 | `docs/TEST_CONVENTION.md` |
| Git·PR·Merge | `docs/GITHUB_RULES.md` |
| 도메인 영향 | `docs/DOMAIN_DEPENDENCIES.md` |
| Issue 제목 | `docs/ISSUE_TITLE_RULES.md` |

API 계약은 `docs/BOBFULL_API_SPEC_COMPLETE.md`, 프로젝트 정책·버전·역할은 `docs/PROJECT_CONTEXT.md`, 데이터 모델과 저장값·계산값 구분은 `docs/ERD.md`를 기준으로 한다. 세 문서가 충돌하면 임의로 선택하거나 덮어쓰지 않고 중단한다.

API 변경은 API 명세와 `PROJECT_CONTEXT`, ERD, 영향 문서의 동기화 범위를 확인한다. 도메인 정책 변경은 API 명세·PROJECT_CONTEXT·ERD와 영향 문서를 함께 검토하고, 데이터 모델 변경은 ERD와 관련 API의 Request·Response·계산값·정합성 제약을 함께 검토한다.

## 7. 필수 경계

- 한 번에 하나의 Issue만 처리한다.
- AI가 Human 답변이나 Human 리뷰를 대신 작성한 것처럼 표시하지 않는다.
- AI 보완 내용은 반드시 `AI 보완 설명`으로 구분한다.
- Issue 범위 밖 기능과 불필요한 리팩터링을 추가하지 않는다.
- 정책·API·DB·상태·권한·트랜잭션을 임의로 결정하지 않는다.
- 필수 Human 답변 검토·최종 계약 확인·`status:in-progress` 기록 없이 구현하지 않는다.
- 실행하지 않은 테스트를 `PASS`로 기록하지 않는다.
- 전체 build가 실패하면 완료로 표현하지 않는다.
- 비밀정보와 실제 개인정보를 입력·Commit·PR에 포함하지 않는다.
- 담당자 AI의 PR 검토는 독립적인 Human Approve를 대체하지 않는다.
- 다른 팀원 AI 리뷰의 존재 여부를 작업 시작·수정·Merge 조건으로 사용하지 않는다.
- AI는 Approve와 Merge를 수행하지 않는다.
- API Response의 계산값을 근거 없이 DB 컬럼으로 중복 저장하지 않는다. 저장이 필요하면 갱신 책임·정합성·동시성 전략을 Human과 별도 결정한다.
- `READY` Payment의 임시 좌석 선점·만료 정책을 바꾸거나, `Settlement`, `SeatHold`, `WebhookEvent` 엔티티를 추가하려면 Human 승인과 기준 문서 반영이 필요하다.

## 8. 파일 수정 안전 규칙

- 기존 문서 수정 요청은 별도 `SUMMARY`, `UPDATED`, `FINAL` 파일을 만들지 않고 기존 파일을 직접 수정한다.
- 사용자의 사전 승인 없이 임시 폴더, 압축·Base64 파일, trigger 파일, 일회성 GitHub Actions Workflow를 저장소에 추가하지 않는다.
- 완료 보고 전 대상 파일을 다시 읽고, PR 최종 변경 목록에 요청하지 않은 파일이 없는지 확인한다.
- 도구 제한으로 정상 수정이 불가능하면 임의 우회하지 말고 작업을 중단하여 사용자에게 보고한다.

## 9. 즉시 중단 조건

- 확정 문서·Issue·코드가 충돌함
- Human 답변이 모호하거나 핵심 미결정 사항이 남음
- 구현 승인 기록이 없거나 승인 이후 최종 계약이 변경됨
- 다른 담당자의 계약 변경이 필요함
- 새로운 정책·API·DB·인프라 결정이 필요함
- 데이터 정합성·권한·보안·손실 위험이 발견됨
- 핵심 검증 환경이나 권한이 없음
- Issue 범위 밖 변경이 필요함

위 중단 조건은 담당자 AI가 임의 구현·수정을 멈추는 기준이다. 이미 존재하는 PR은 확인 가능한 최신 Diff까지 검토하고 위험과 필요한 Human 결정을 보고한다.

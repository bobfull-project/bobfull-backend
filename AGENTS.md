# BobFull AI 작업 진입점

이 파일은 BobFull 저장소에서 사용하는 AI 에이전트의 공통 실행 규칙이다.

AI는 Issue 정리·구현·검증·리뷰 보완을 수행하고, Human은 담당자 이해도 답변·리뷰어 Human 리뷰와 최종 Merge를 책임진다.

## 1. 최초 1회 온보딩

각 팀원의 AI 에이전트는 저장소를 처음 열었을 때 다음을 실제 파일 기준으로 확인한다.

1. `AGENTS.md`
2. `README.md`
3. `docs/PROJECT_CONTEXT.md`
4. `docs/AI_WORKFLOW.md`
5. 작업에 필요한 `docs` 문서
6. Issue·PR 템플릿
7. 현재 코드·브랜치·작업 트리
8. 빌드·테스트 방법

온보딩 단계에서는 코드·문서·Issue·PR·브랜치를 변경하지 않는다. 문서 충돌과 확인하지 못한 내용만 보고한다.

## 2. 이후 사용하는 짧은 명령

구현 에이전트:

```text
Issue #번호 구현하라
```

리뷰 에이전트:

```text
PR #번호 1차 리뷰하라
```

에이전트는 명령을 받을 때마다 실제 GitHub Issue·PR과 로컬 상태를 다시 읽고 현재 단계에서 허용된 작업만 수행한다.

Human이 Issue 또는 PR에 답변하거나 Human 리뷰를 작성한 뒤에는 같은 짧은 명령을 다시 입력해 다음 단계부터 재개한다.

리뷰 에이전트는 최신 Head SHA, 실제 Diff와 작업 범위를 확인할 수 있으면 PR 상태와 관계없이 1차 리뷰를 수행한다. 테스트 실패·미실행, `HOLD`·`NOT_RUN`, Human 답변 미작성과 Issue 미완료는 리뷰 중단 사유가 아니라 리뷰에서 기록할 발견 사항이다.

최신 Head SHA나 실제 Diff가 없거나 작업 범위를 전혀 확인할 수 없을 때만 `1차 리뷰 준비 미완료`로 보고한다.

## 3. `Issue #번호 구현하라` 상태별 동작

| 현재 상태 | 구현 에이전트 동작 |
|---|---|
| Issue에 Human 답변이 없음 | Issue·문서·코드를 분석하고 질문을 작성한 뒤 `HUMAN_ANSWER_REQUIRED`로 남기고 중단 |
| Issue Human 답변이 작성됨 | 답변을 검증하고 `AI 보완 설명`을 추가한 뒤 최종 Issue 계약으로 재작성하고 `AI_FINALIZED`로 남긴 후 중단 |
| Issue가 `AI_FINALIZED`이고 같은 명령을 다시 받음 | 해당 명령을 Human 승인 신호로 해석 → Issue에 승인 기록 작성 → 상태를 `HUMAN_APPROVED`로 변경 → 같은 실행에서 구현·테스트·Diff 검토·Commit·Push·Draft PR 생성 |
| PR에 Human 이해도 답변과 1차 AI 리뷰가 등록됨 | Human 리뷰가 있으면 함께 확인하고, Issue 범위 안의 명확한 지적만 수정·재검증·Push |
| 정책·API·DB 재결정이 필요한 항목이 있음 | 임의 수정하지 않고 Human 판단 요청 |
| 수정·재검증 완료 | `FINAL_HUMAN_REVIEW` 상태로 보고하고 중단 |

`AI_FINALIZED` 이후의 같은 명령은 Human 승인 신호다. 구현 AI는 Human에게 별도 승인 체크나 GitHub 입력을 요구하지 않고 Issue에 다음을 기록한다.

- 승인 주체: 명령을 입력한 Human 담당자
- 승인 신호: `Issue #번호 구현하라`
- 기록 주체: 구현 AI
- 승인 대상: 현재 AI 최종 Issue 계약
- 승인 시각
- 승인 상태: `HUMAN_APPROVED`

승인 이후 최종 계약이 변경되면 기존 승인은 무효다. 구현 AI는 구현을 중단하고 상태를 `AI_FINALIZED` 또는 `HOLD`로 되돌린 뒤 새 Human 승인 명령을 기다린다.

PR 담당자는 Issue와 PR의 이해도 질문에 직접 답한다. PR 작성자가 아닌 Human 리뷰어는 PR 템플릿의 `Human 리뷰`에 다음 세 항목을 작성한다.

- 내가 이해한 구현 흐름
- 이해되지 않거나 추가 설명이 필요한 부분
- 문제로 보이거나 다시 확인할 부분

Human 리뷰어는 확실하지 않은 내용을 추정하지 않는다. 별도의 체크리스트·승인자·확인 범위는 작성하지 않는다.

## 4. 문서 라우팅

| 작업 | 기준 문서 |
|---|---|
| 프로젝트 정책·버전·역할 | `docs/PROJECT_CONTEXT.md` |
| AI 전체 절차 | `docs/AI_WORKFLOW.md` |
| 구현 에이전트 실행 | `docs/AI_IMPLEMENTATION_GUIDE.md` |
| AI 1차 PR 리뷰 | `docs/AI_REVIEW_GUIDE.md` |
| 코드 작성 | `docs/CODE_CONVENTION.md` |
| 테스트·증거 | `docs/TEST_CONVENTION.md` |
| Git·PR·Merge | `docs/GITHUB_RULES.md` |
| 도메인 영향 | `docs/DOMAIN_DEPENDENCIES.md` |
| Issue 제목 | `docs/ISSUE_TITLE_RULES.md` |

확정 정책은 `docs/PROJECT_CONTEXT.md`를 우선한다. 작업 방식은 각 책임 문서를 따른다. 충돌하면 임의로 선택하지 않고 중단한다.

## 5. 필수 경계

- 한 번에 하나의 Issue만 처리한다.
- AI가 Human 답변이나 Human 리뷰를 대신 작성한 것처럼 표시하지 않는다.
- AI 보완 내용은 반드시 `AI 보완 설명`으로 구분한다.
- Issue 범위 밖 기능과 불필요한 리팩터링을 추가하지 않는다.
- 정책·API·DB·상태·권한·트랜잭션을 임의로 결정하지 않는다.
- `HUMAN_APPROVED` 기록이 없는 Issue를 구현하지 않는다.
- 실행하지 않은 테스트를 `PASS`로 기록하지 않는다.
- 전체 build가 실패하면 완료로 표현하지 않는다.
- 비밀정보와 실제 개인정보를 입력·Commit·PR에 포함하지 않는다.
- 구현 AI의 자체 검토는 PR 1차 리뷰로 계산하지 않는다.
- 1차 AI 리뷰는 PR 작성자가 아닌 다른 팀원의 AI 에이전트가 수행한다.
- Human 리뷰는 Human 리뷰어가 실제 Diff를 읽고 작성하며 AI 1차 리뷰와 구분한다.
- 1차 리뷰 완료를 구현 완료나 Merge 가능으로 표현하지 않는다.
- AI는 Approve와 Merge를 수행하지 않는다.

## 6. 즉시 중단 조건

- 확정 문서·Issue·코드가 충돌함
- Human 답변이 모호하거나 핵심 미결정 사항이 남음
- 구현 승인 기록이 없거나 승인 이후 최종 계약이 변경됨
- 다른 담당자의 계약 변경이 필요함
- 새로운 정책·API·DB·인프라 결정이 필요함
- 데이터 정합성·권한·보안·손실 위험이 발견됨
- 핵심 검증 환경이나 권한이 없음
- Issue 범위 밖 변경이 필요함

위 중단 조건은 구현 에이전트가 임의 구현·수정을 멈추는 기준이다. 리뷰 에이전트는 확인 가능한 최신 Diff를 계속 검토하고 해당 위험을 발견 사항으로 기록한다.

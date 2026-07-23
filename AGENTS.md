# BobFull AI 작업 진입점

이 파일은 BobFull 저장소에서 사용하는 AI 에이전트의 공통 실행 규칙이다.

AI는 Issue 정리·구현·검증·리뷰 보완을 수행하고, Human은 이해도 답변과 최종 Merge를 책임진다.

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

Human이 Issue 또는 PR에 답변한 뒤에는 같은 짧은 명령을 다시 입력해 다음 단계부터 재개한다.

## 3. `Issue #번호 구현하라` 상태별 동작

| 현재 상태 | 구현 에이전트 동작 |
|---|---|
| Issue에 Human 답변이 없음 | Issue·문서·코드를 분석하고 질문을 작성한 뒤 `HUMAN_ANSWER_REQUIRED`로 남기고 중단 |
| Issue Human 답변이 작성됨 | 답변을 검증하고 `AI 보완 설명`을 추가한 뒤 최종 Issue 계약으로 재작성하고 `AI_FINALIZED`로 남긴 후 중단 |
| Issue가 `AI_FINALIZED`이고 같은 명령을 다시 받음 | 해당 명령을 구현 지시로 해석하고 브랜치 준비 → 구현 → 테스트 → Diff 검토 → Commit·Push → Draft PR 생성 |
| PR에 Human 이해도 답변과 1차 AI 리뷰가 등록됨 | 리뷰를 확인하고 Issue 범위 안의 명확한 지적만 수정·재검증·Push |
| 정책·API·DB 재결정이 필요한 항목이 있음 | 임의 수정하지 않고 Human 판단 요청 |
| 수정·재검증 완료 | `FINAL_HUMAN_REVIEW` 상태로 보고하고 중단 |

Human은 Issue와 PR의 이해도 질문에 직접 답한다. 별도의 체크리스트·승인자·확인 범위를 작성하지 않는다.

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
- AI가 Human 답변을 대신 작성한 것처럼 표시하지 않는다.
- AI 보완 내용은 반드시 `AI 보완 설명`으로 구분한다.
- Issue 범위 밖 기능과 불필요한 리팩터링을 추가하지 않는다.
- 정책·API·DB·상태·권한·트랜잭션을 임의로 결정하지 않는다.
- 실행하지 않은 테스트를 `PASS`로 기록하지 않는다.
- 전체 build가 실패하면 완료로 표현하지 않는다.
- 비밀정보와 실제 개인정보를 입력·Commit·PR에 포함하지 않는다.
- 구현 AI의 자체 검토는 PR 1차 리뷰로 계산하지 않는다.
- 1차 리뷰는 PR 작성자가 아닌 다른 팀원의 AI 에이전트가 수행한다.
- AI는 Approve와 Merge를 수행하지 않는다.

## 6. 즉시 중단 조건

- 확정 문서·Issue·코드가 충돌함
- Human 답변이 모호하거나 핵심 미결정 사항이 남음
- 다른 담당자의 계약 변경이 필요함
- 새로운 정책·API·DB·인프라 결정이 필요함
- 데이터 정합성·권한·보안·손실 위험이 발견됨
- 핵심 검증 환경이나 권한이 없음
- Issue 범위 밖 변경이 필요함

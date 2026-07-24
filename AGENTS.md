# BobFull AI 작업 진입점

이 파일은 BobFull 저장소에서 사용하는 담당자 AI 에이전트의 공통 실행 규칙이다.

담당자 AI는 Issue 정리·구현·검증·PR 자체 검토·리뷰 반영을 수행한다. Human은 이해도 답변, Human 리뷰, 정책 판단, Approve와 최종 Merge를 책임진다.

## 1. 최초 1회 온보딩

각 팀원의 담당자 AI 에이전트는 저장소를 처음 열었을 때 다음을 실제 파일 기준으로 확인한다.

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

담당자 AI에는 다음 명령만 사용한다.

```text
Issue #번호 구현하라
```

담당자 AI는 명령을 받을 때마다 실제 GitHub Issue, 연결된 PR, 현재 Head, PR 리뷰·댓글과 로컬 상태를 다시 읽고 현재 단계의 다음 작업부터 재개한다.

Human이 Issue 또는 PR에 답변하거나 리뷰를 작성한 뒤에도 같은 명령을 다시 입력한다.

별도의 다른 팀원 AI 리뷰는 워크플로우 단계나 완료 조건으로 사용하지 않는다. PR에 리뷰·댓글이 등록되면 작성 주체와 관계없이 담당자 AI가 실제 코드 근거를 확인해 반영 여부를 판단한다.

## 3. `Issue #번호 구현하라` 상태별 동작

| 현재 상태 | 담당자 AI 동작 |
|---|---|
| Issue에 Human 답변이 없음 | Issue·문서·코드를 분석하고 질문을 작성한 뒤 `HUMAN_ANSWER_REQUIRED`로 남기고 중단 |
| Issue Human 답변이 작성됨 | 답변을 검증하고 `AI 보완 설명`을 추가한 뒤 최종 Issue 계약으로 재작성하고 `AI_FINALIZED`로 남긴 후 중단 |
| Issue가 `AI_FINALIZED`이고 같은 명령을 다시 받음 | 명령을 Human 승인 신호로 해석 → Issue에 승인 기록 작성 → `HUMAN_APPROVED`로 변경 → 같은 실행에서 구현·테스트·Diff 검토·Commit·Push·Draft PR 생성 |
| 연결된 PR이 존재함 | 최신 Head·실제 Diff·테스트 증거·담당자 Human 답변·Human 리뷰·모든 PR 리뷰와 댓글을 확인하고 범위 안 지적을 수정·재검증·Push |
| 정책·API·DB 재결정이 필요한 항목이 있음 | 임의 수정하지 않고 Human 판단 요청 |
| 담당자 AI 검토·수정·재검증 완료 | `FINAL_HUMAN_REVIEW` 상태로 보고하고 중단 |

`AI_FINALIZED` 이후의 같은 명령은 Human 승인 신호다. 담당자 AI는 Human에게 별도 승인 체크나 GitHub 입력을 요구하지 않고 Issue에 다음을 기록한다.

- 승인 주체: 명령을 입력한 Human 담당자
- 승인 신호: `Issue #번호 구현하라`
- 기록 주체: 담당자 AI
- 승인 대상: 현재 AI 최종 Issue 계약
- 승인 시각
- 승인 상태: `HUMAN_APPROVED`

승인 이후 최종 계약이 변경되면 기존 승인은 무효다. 담당자 AI는 구현을 중단하고 상태를 `AI_FINALIZED` 또는 `HOLD`로 되돌린 뒤 새 Human 승인 명령을 기다린다.

## 4. PR Human 입력과 담당자 AI 검토

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

## 5. 문서 라우팅

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

## 6. 필수 경계

- 한 번에 하나의 Issue만 처리한다.
- AI가 Human 답변이나 Human 리뷰를 대신 작성한 것처럼 표시하지 않는다.
- AI 보완 내용은 반드시 `AI 보완 설명`으로 구분한다.
- Issue 범위 밖 기능과 불필요한 리팩터링을 추가하지 않는다.
- 정책·API·DB·상태·권한·트랜잭션을 임의로 결정하지 않는다.
- `HUMAN_APPROVED` 기록이 없는 Issue를 구현하지 않는다.
- 실행하지 않은 테스트를 `PASS`로 기록하지 않는다.
- 전체 build가 실패하면 완료로 표현하지 않는다.
- 비밀정보와 실제 개인정보를 입력·Commit·PR에 포함하지 않는다.
- 담당자 AI의 PR 검토는 독립적인 Human Approve를 대체하지 않는다.
- 다른 팀원 AI 리뷰의 존재 여부를 작업 시작·수정·Merge 조건으로 사용하지 않는다.
- AI는 Approve와 Merge를 수행하지 않는다.
- API Response의 계산값을 근거 없이 DB 컬럼으로 중복 저장하지 않는다. 저장이 필요하면 갱신 책임·정합성·동시성 전략을 Human과 별도 결정한다.
- `READY` Payment의 임시 좌석 선점·만료 정책을 바꾸거나, `Settlement`, `SeatHold`, `WebhookEvent` 엔티티를 추가하려면 Human 승인과 기준 문서 반영이 필요하다.

## 7. 파일 수정 안전 규칙

- 기존 문서 수정 요청은 별도 `SUMMARY`, `UPDATED`, `FINAL` 파일을 만들지 않고 기존 파일을 직접 수정한다.
- 사용자의 사전 승인 없이 임시 폴더, 압축·Base64 파일, trigger 파일, 일회성 GitHub Actions Workflow를 저장소에 추가하지 않는다.
- 완료 보고 전 대상 파일을 다시 읽고, PR 최종 변경 목록에 요청하지 않은 파일이 없는지 확인한다.
- 도구 제한으로 정상 수정이 불가능하면 임의 우회하지 말고 작업을 중단하여 사용자에게 보고한다.

## 8. 즉시 중단 조건

- 확정 문서·Issue·코드가 충돌함
- Human 답변이 모호하거나 핵심 미결정 사항이 남음
- 구현 승인 기록이 없거나 승인 이후 최종 계약이 변경됨
- 다른 담당자의 계약 변경이 필요함
- 새로운 정책·API·DB·인프라 결정이 필요함
- 데이터 정합성·권한·보안·손실 위험이 발견됨
- 핵심 검증 환경이나 권한이 없음
- Issue 범위 밖 변경이 필요함

위 중단 조건은 담당자 AI가 임의 구현·수정을 멈추는 기준이다. 이미 존재하는 PR은 확인 가능한 최신 Diff까지 검토하고 위험과 필요한 Human 결정을 보고한다.

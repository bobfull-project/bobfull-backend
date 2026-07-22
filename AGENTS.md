# BobFull AI 작업 진입점

이 파일은 AI가 `bobfull-backend` 저장소에서 작업할 때 가장 먼저 확인하는 최소 실행 규칙이다.

AI는 반복 구현 비용을 줄이고, Human은 설계·흐름·성능·검증과 최종 판단에 집중한다.

## 작업 시작

1. 대상 GitHub Issue를 먼저 읽는다.
2. [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md)의 확정 정책과 충돌하지 않는지 확인한다.
3. 승인된 구현 범위와 완료 조건을 확인한다.
4. 관련 코드와 테스트를 읽는다.
5. 필요한 문서만 추가로 읽는다.

승인된 구현 계약이 없거나 정책 결정이 남아 있으면 구현하지 않는다.

## 라우팅

| 작업 | 확인할 대상 |
|---|---|
| 프로젝트 확정 정책·버전 범위·역할 | `docs/PROJECT_CONTEXT.md` |
| Issue 제목 버전·유형 규칙 | `docs/ISSUE_TITLE_RULES.md` |
| Git 브랜치·커밋·PR·Merge | `docs/GITHUB_RULES.md` |
| Java·Spring 구현 | `docs/CODE_CONVENTION.md` |
| 테스트 작성·실행·증거 | `docs/TEST_CONVENTION.md` |
| 도메인 의존성과 변경 영향 | `docs/DOMAIN_DEPENDENCIES.md` |
| 전체 서비스 플로우 | `docs/bobfull_full_flowchart_mermaid.md` |
| AI 협업 절차 | `docs/AI_WORKFLOW.md` |
| 구현 AI 실행·검증·이해도 질문 | `docs/AI_IMPLEMENTATION_GUIDE.md` |
| 기능 구현 | 대상 Issue, 관련 코드와 테스트 |
| Issue 작성 | `docs/ISSUE_TITLE_RULES.md`, `.github/ISSUE_TEMPLATE/feature.md` |
| PR 작성 | `.github/pull_request_template.md` |
| PR 검토 | `docs/AI_REVIEW_GUIDE.md` |

문서별 책임은 다음처럼 구분한다.

- 확정 정책과 v1·v2·v3 범위는 `docs/PROJECT_CONTEXT.md`가 정한다.
- Issue 제목의 버전과 유형은 `docs/ISSUE_TITLE_RULES.md`가 정한다.
- AI가 어디까지 작업할 수 있는지는 `docs/AI_WORKFLOW.md`와 `docs/AI_IMPLEMENTATION_GUIDE.md`가 정한다.
- Java·Spring 코드 작성 방식은 `docs/CODE_CONVENTION.md`가 정한다.
- 테스트 코드 작성 방식과 증거 기준은 `docs/TEST_CONVENTION.md`가 정한다.
- 정책 변경 시 관련 도메인·담당자·문서·테스트 영향은 `docs/DOMAIN_DEPENDENCIES.md`로 확인한다.

문서가 충돌하면 `docs/PROJECT_CONTEXT.md`의 확정 정책을 우선한다. 존재하지 않는 문서나 확정되지 않은 정책을 추정하지 않는다.

## 필수 경계

- 한 번에 하나의 승인된 Issue만 처리한다.
- Human이 `READY`로 승인한 Issue는 구현 AI가 코드·테스트·검증·Draft PR까지 수행할 수 있다.
- Git 작업은 `docs/GITHUB_RULES.md`를 따른다.
- Java·Spring 구현은 `docs/CODE_CONVENTION.md`를 따른다.
- 테스트는 `docs/TEST_CONVENTION.md`에 따라 한글 테스트명과 given-when-then 구조로 작성한다.
- Issue 범위 밖 기능과 불필요한 리팩터링을 추가하지 않는다.
- 서비스 정책, API, DB 구조를 임의로 결정하거나 변경하지 않는다.
- API Key, 비밀번호, JWT, 운영 환경변수와 실제 개인정보를 AI 입력에 포함하지 않는다.
- 운영 데이터가 필요하면 식별 정보를 제거한 최소 재현 데이터만 사용한다.
- 실행하지 않은 테스트를 성공으로 기록하지 않는다.
- 확인된 사실, 추정, 미검증 범위를 구분한다.
- AI가 작성한 코드도 담당자가 설명할 수 있어야 한다.
- AI는 리뷰 반영 범위와 Merge를 결정하지 않는다.

## 중단 조건

다음 상황에서는 작업을 중단하고 Human 판단을 요청한다.

- 요구사항이나 문서가 서로 충돌함
- 승인되지 않은 정책 결정이 필요함
- API 또는 DB 구조 재결정이 필요함
- 다른 담당자 영역의 계약 변경이 필요함
- 데이터 정합성, 권한, 보안 또는 손실 위험이 발견됨
- 테스트 삭제·약화 또는 Issue 범위 밖 변경이 필요함
- 필요한 환경이나 권한이 없어 핵심 검증을 할 수 없음

상세 협업 흐름은 `docs/AI_WORKFLOW.md`, 구현 AI 실행 기준은 `docs/AI_IMPLEMENTATION_GUIDE.md`를 따른다.

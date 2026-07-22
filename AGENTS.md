# BobFull AI 작업 진입점

이 파일은 AI가 `bobfull-backend` 저장소에서 작업할 때 가장 먼저 확인하는 최소 실행 규칙이다.

AI는 반복 구현 비용을 줄이고, Human은 설계·흐름·성능·검증과 최종 판단에 집중한다.

## 작업 시작

1. 대상 GitHub Issue를 먼저 읽는다.
2. 승인된 구현 범위와 완료 조건을 확인한다.
3. 관련 코드와 테스트를 읽는다.
4. 필요한 문서만 추가로 읽는다.

승인된 구현 계약이 없거나 정책 결정이 남아 있으면 구현하지 않는다.

## 라우팅

| 작업 | 확인할 대상 |
|---|---|
| Git 브랜치·커밋·PR·Merge | `docs/GITHUB_RULES.md` |
| Java·Spring 구현과 테스트 | `docs/CODE_CONVENTION.md` |
| AI 협업 절차 | `docs/AI_WORKFLOW.md` |
| 기능 구현 | 대상 Issue, 관련 코드와 테스트 |
| Issue 작성 | `.github/ISSUE_TEMPLATE/feature.md` |
| PR 작성 | `.github/pull_request_template.md` |
| PR 검토 | `docs/AI_REVIEW_GUIDE.md` |

존재하지 않는 문서나 확정되지 않은 정책을 추정하지 않는다.

## 필수 경계

- 한 번에 하나의 승인된 Issue만 처리한다.
- Git 작업은 `docs/GITHUB_RULES.md`를 따른다.
- Java·Spring 구현은 `docs/CODE_CONVENTION.md`를 따른다.
- Issue 범위 밖 기능과 불필요한 리팩터링을 추가하지 않는다.
- 서비스 정책, API, DB 구조를 임의로 결정하거나 변경하지 않는다.
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

상세 실행·검증·리뷰 절차는 `docs/AI_WORKFLOW.md`를 따른다.

---
name: bobfull-onboarding
description: BobFull 저장소에서 처음 작업하거나 새로운 Issue를 처음 인계받아, 현재 작업에 필요한 기준 문서와 다음 Human 게이트를 선택적으로 확인할 때 사용한다.
---

# BobFull 최초 온보딩

이 파일은 저장소에서 버전 관리하는 Skill 원본이다. 저장소에 존재한다는 사실만으로 Codex 또는 ChatGPT에 자동 설치·자동 적용된다고 간주하지 않는다.

## 사용 시점

- BobFull 저장소에서 처음 작업할 때
- 새로운 Issue를 처음 인계받았을 때
- 현재 작업에 필요한 기준 문서를 판단해야 할 때

## 필수 입력

- 대상 Issue 번호
- 요청받은 현재 단계
- 사용자가 명시한 작업 범위

정보가 없으면 추측하지 말고 누락된 정보를 보고한다.

## 최초 온보딩 순서

1. 저장소·현재 브랜치·작업 트리를 확인한다.
2. [AGENTS.md](../../AGENTS.md)와 대상 Issue를 확인한다.
3. Issue 상태와 승인 여부를 확인한다.
4. 작업 종류에 필요한 문서만 선택한다.
5. Issue·문서·코드 사이의 직접 충돌을 확인한다.
6. 현재 상태와 다음 Human 게이트를 보고한다.

## 문서 선택 기준

현재 Issue와 직접 관련된 원본만 읽는다.

- 서비스 정책·버전 범위: [PROJECT_CONTEXT](../../docs/PROJECT_CONTEXT.md)
- HTTP·WebSocket 계약: [API 명세](../../docs/BOBFULL_API_SPEC_COMPLETE.md)
- 데이터 모델: [ERD](../../docs/ERD.md)
- 책임 경계: [ARCHITECTURE](../../docs/ARCHITECTURE.md), [DOMAIN_DEPENDENCIES](../../docs/DOMAIN_DEPENDENCIES.md)
- 구현·리뷰 절차: [AI_WORKFLOW](../../docs/AI_WORKFLOW.md), [AI_IMPLEMENTATION_GUIDE](../../docs/AI_IMPLEMENTATION_GUIDE.md), [AI_REVIEW_GUIDE](../../docs/AI_REVIEW_GUIDE.md)
- Git·Issue·PR 규칙: [GITHUB_RULES](../../docs/GITHUB_RULES.md), [ISSUE_TITLE_RULES](../../docs/ISSUE_TITLE_RULES.md)
- 공통 구현 기준: [CODE_CONVENTION](../../docs/CODE_CONVENTION.md), [COMMON_SKELETON_GUIDE](../../docs/COMMON_SKELETON_GUIDE.md), [TEST_CONVENTION](../../docs/TEST_CONVENTION.md)
- 기술 결정: [ADR 목록](../../docs/adr/README.md)과 현재 작업에 관련된 개별 ADR
- 저장소 공통 지침: [AGENTS.md](../../AGENTS.md)

## 핵심 실행 규칙

- 모든 문서를 무조건 읽지 않는다.
- 원본 문서 내용을 이 Skill에 길게 복제하지 않는다.
- 현재 Issue와 직접 관련된 문서만 선택한다.
- 완료 여부, 테스트 결과, 일정, 상태를 추측하지 않는다.
- Human 승인이 필요한 게이트를 임의로 넘지 않는다.
- Issue에서 허용하지 않은 파일을 수정하지 않는다.
- 문서 간 표현 차이와 실제 계약 충돌을 구분한다.
- 치명적인 충돌이 없으면 불필요한 수정 작업을 만들지 않는다.
- 구현 또는 리뷰 Skill의 역할까지 확장하지 않는다.

## 최초 보고 형식

다음 항목만 간단하게 보고한다.

- 대상 Issue와 현재 상태
- 현재 브랜치와 작업 트리
- 이번 작업에서 선택한 기준 문서
- 확인된 직접 충돌
- 현재 수행 가능한 단계
- 다음 Human 게이트
- HOLD 사유가 있다면 그 사유

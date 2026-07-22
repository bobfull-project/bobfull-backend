# BobFull Backend

제주 혼자 여행자를 위한 합석형 좌석 예약 플랫폼 밥풀(BobFull)의 Spring Boot 백엔드 저장소입니다.

## 프로젝트 정보

- 프로젝트명: 밥풀(BobFull)
- 팀명: 밥조
- 프로젝트 기간: 2026.07.21 ~ 2026.08.24
- 초기 타깃: 제주 평일 저녁, 혼자 여행하는 사용자

## 핵심 문제

- 혼자 방문하기 어려운 식당의 이용 제한
- 2인 이상 주문 조건으로 인한 1인 여행자의 불편
- 식당의 빈 좌석 및 노쇼 관리 문제
- 함께 식사할 사용자를 찾기 어려운 문제

## 핵심 흐름

```text
사장님
→ 식당·예약 시간·테이블 등록

사용자 A
→ 합석 예약 생성
→ 본인 예약금 결제
→ 참여자 모집

사용자 B
→ 모집 중인 예약 참여
→ 본인 예약금 결제

결제 완료 인원 2명 이상
→ 예약 확정
→ 모집 마감 전 잔여 좌석 추가 참여 가능
```

## 주요 문서

| 문서 | 역할 |
|---|---|
| [`docs/PROJECT_CONTEXT.md`](./docs/PROJECT_CONTEXT.md) | 프로젝트 확정 정책·버전 범위·역할의 단일 기준 |
| [`AGENTS.md`](./AGENTS.md) | AI 작업 진입점과 필수 경계 |
| [`docs/bobfull_full_flowchart_mermaid.md`](./docs/bobfull_full_flowchart_mermaid.md) | 최신 정책 기준 전체 업무 플로우 |
| [`docs/DOMAIN_DEPENDENCIES.md`](./docs/DOMAIN_DEPENDENCIES.md) | 도메인 의존성과 정책 변경 영향 확인 |
| [`docs/AI_WORKFLOW.md`](./docs/AI_WORKFLOW.md) | Issue부터 Human Merge까지의 AI 협업 절차 |
| [`docs/AI_IMPLEMENTATION_GUIDE.md`](./docs/AI_IMPLEMENTATION_GUIDE.md) | 구현 AI의 코드·테스트·검증·Draft PR 기준 |
| [`docs/AI_REVIEW_GUIDE.md`](./docs/AI_REVIEW_GUIDE.md) | ChatGPT PR 리뷰와 최신 Head 재검토 기준 |
| [`docs/CODE_CONVENTION.md`](./docs/CODE_CONVENTION.md) | Java·Spring 코드 작성 규칙 |
| [`docs/TEST_CONVENTION.md`](./docs/TEST_CONVENTION.md) | 테스트명·given-when-then·실행 증거 규칙 |
| [`docs/GITHUB_RULES.md`](./docs/GITHUB_RULES.md) | 브랜치·커밋·리뷰·Merge 규칙 |

## Repositories

- `bobfull-backend` — Spring Boot 백엔드
- `bobfull-frontend` — 사용자 및 사장님 프론트엔드
- `.github` — Organization profile 관리

## Team

| 이름 | 핵심 도메인 | 공통·도전 기술 |
|---|---|---|
| 김현승 | 예약금 결제·환불·지급 예정 예약금 | AI·채팅 |
| 김홍기 | 합석 테이블·예약 시간·검색 | AWS·CI/CD·로그·모니터링 |
| 배지현 | 예약·가용 인원·좌석 재고·동시성 | 프론트엔드·Kafka |
| 정용태 | 회원·인증·사장님·식당·관리자·예약 완료·취소 | 캐시·조회 성능·K6 |

## Development Principles

- 이해하지 못한 코드는 병합하지 않습니다.
- 테스트하지 않은 기능은 완료로 판단하지 않습니다.
- 기술은 실제 문제와 검증 근거를 기준으로 도입합니다.
- 핵심 예약 흐름 완성을 부가 기술보다 우선합니다.
- 미확정 정책을 구현 편의를 위해 임의로 결정하지 않습니다.

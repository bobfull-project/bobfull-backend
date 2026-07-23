# BobFull Backend

혼자 이용하기 어려운 식당에서 1인 사용자 여러 명을 연결해 함께 예약할 수 있도록 지원하는 합석 예약 서비스 **밥풀(BobFull)**의 Spring Boot 백엔드 저장소입니다.

## 프로젝트 정보

- 프로젝트명: 밥풀(BobFull)
- 팀명: 밥조
- 프로젝트 기간: 2026.07.21 ~ 2026.08.24
- 핵심 가치: 합석 모집, 사용자별 인원 단위 예약금, 예약·환불·노쇼·지급 예정금 통합 관리

## 핵심 문제

- 2인 이상 주문 또는 테이블 단위 운영으로 1인 고객이 이용하기 어려운 문제
- 함께 식사할 사람을 직접 구해야 하는 불편
- 합석 테이블의 빈 좌석과 낮은 좌석 활용도
- 취소와 노쇼로 인한 식당 손실
- 예약·결제·환불·노쇼·지급 예정금을 개별적으로 관리해야 하는 문제

## 핵심 정책

- API 기능 단계는 `V1·V2·V3`로 구분하지만 호출 URL에는 버전을 넣지 않고 `/api/**`를 사용합니다.
- 합석 테이블 정원은 `2인·4인·6인·8인` 중 하나입니다.
- 최초 예약자와 추가 참여자는 각각 본인을 포함한 `partySize`만큼 신청합니다.
- 결제 금액은 `partySize × 1인당 예약금`이며 PortOne으로 결제합니다.
- 현재 참여 인원은 결제 완료 참여자들의 `partySize` 합계입니다.
- 예약 확정 기준은 테이블별로 다릅니다.

| 테이블 정원 | 예약 확정 인원 |
|---:|---:|
| 2인 | 2명 |
| 4인 | 3명 |
| 6인 | 5명 |
| 8인 | 7명 |

- 예약 상태와 모집 상태를 분리합니다.
  - 예약 상태: `RECRUITING`, `CONFIRMED`, `CANCELLED`, `CLOSED`
  - 모집 상태: `OPEN`, `CLOSED`
- `CONFIRMED`는 식사 진행 확정이며 추가 모집 종료를 뜻하지 않습니다.
- 모집이 `OPEN`이고 잔여 정원이 있으면 추가 참여할 수 있습니다.
- 최초 예약자는 `CONFIRMED` 이후 모집을 직접 마감할 수 있으며 다시 열 수 없습니다.
- 테이블 정원 도달 또는 식사 시작 2시간 전 도달 시 모집을 자동 마감합니다.

## 핵심 흐름

```text
사장님
→ 식당·합석 테이블·예약 가능 시간 등록
→ 테이블 정원 2·4·6·8인 중 선택

최초 예약자
→ 식당·시간·테이블 선택
→ partySize 입력
→ 좌석 10분 임시 선점·Payment READY 생성
→ PortOne 예약금 결제
→ 서버가 결제 상태·금액·통화 검증
→ 결제 성공 시 예약·최초 참여자 생성·Payment PAID
→ 결제 실패·만료 시 좌석 해제

추가 참여자
→ 모집 상태 OPEN인 예약 조회
→ 남은 참여 가능 인원 이내에서 partySize 입력
→ 좌석 10분 임시 선점·PortOne 예약금 결제
→ 서버 검증 성공 시 참여자 등록
→ 현재 참여 인원과 예약 상태 재계산

모집 종료
→ 최초 예약자 수동 마감 또는 정원 도달 또는 식사 시작 2시간 전
→ 모집 상태 CLOSED
→ 확정 기준 미달이면 전체 취소·전액 환불
```

## v1 참여 단위

v1에서는 한 사용자가 신청한 인원을 하나의 예약 참여 단위로 관리합니다.

- 부분 인원 변경 미지원
- 부분 취소 미지원
- 부분 노쇼 처리 미지원
- 취소 또는 노쇼 처리 시 해당 사용자의 신청 인원 전체에 동일하게 적용

## 주요 문서

| 문서 | 역할 |
|---|---|
| [`docs/PROJECT_CONTEXT.md`](./docs/PROJECT_CONTEXT.md) | 확정 정책·버전·역할의 단일 기준 |
| [`docs/BOBFULL_API_SPEC_COMPLETE.md`](./docs/BOBFULL_API_SPEC_COMPLETE.md) | 전체 HTTP API 계약 |
| [`docs/ISSUE_TITLE_RULES.md`](./docs/ISSUE_TITLE_RULES.md) | Issue 버전·유형 제목 규칙 |
| [`AGENTS.md`](./AGENTS.md) | AI 작업 진입점 |
| [`docs/bobfull_full_flowchart_mermaid.md`](./docs/bobfull_full_flowchart_mermaid.md) | 전체 업무 흐름 |
| [`docs/DOMAIN_DEPENDENCIES.md`](./docs/DOMAIN_DEPENDENCIES.md) | 도메인 의존성과 변경 영향 |
| [`docs/AI_WORKFLOW.md`](./docs/AI_WORKFLOW.md) | AI 협업 전체 흐름 |
| [`docs/AI_IMPLEMENTATION_GUIDE.md`](./docs/AI_IMPLEMENTATION_GUIDE.md) | 구현 AI 실행 기준 |
| [`docs/AI_REVIEW_GUIDE.md`](./docs/AI_REVIEW_GUIDE.md) | AI PR 리뷰 기준 |
| [`docs/CODE_CONVENTION.md`](./docs/CODE_CONVENTION.md) | 코드 작성 규칙 |
| [`docs/TEST_CONVENTION.md`](./docs/TEST_CONVENTION.md) | 테스트·검증 규칙 |
| [`docs/GITHUB_RULES.md`](./docs/GITHUB_RULES.md) | Git 협업 규칙 |

## Team

| 이름 | 핵심 도메인 | 공통·도전 기술 |
|---|---|---|
| 김현승 | 예약금 결제·환불·지급 예정 예약금 | AI·채팅 |
| 김홍기 | 합석 테이블·예약 시간·검색 | AWS·CI/CD·로그·모니터링 |
| 배지현 | 예약·참여·좌석 재고·동시성 | 프론트엔드·Kafka |
| 정용태 | 회원·인증·사장님·식당·관리자 | 캐시·조회 성능·K6 |

## Development Principles

- 최신 확정 기획과 충돌하는 이전 정책은 사용하지 않습니다.
- 이해하지 못한 코드는 병합하지 않습니다.
- 실행하지 않은 테스트는 통과했다고 기록하지 않습니다.
- 성능 수치 없이 성능 개선을 주장하지 않습니다.
- v1 핵심 예약 거래 흐름을 부가 기술보다 우선합니다.
- 예약금 결제는 PortOne 실제 PG 연동으로 구현합니다. 식사대금 결제·POS 연동·계좌 송금은 구현하지 않습니다.

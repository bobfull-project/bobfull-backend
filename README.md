# BobFull Backend

제주에서 혼자 이용하기 어려운 식당을 1인 사용자 여러 명이 함께 예약할 수 있도록 지원하는 합석 예약 서비스 **밥풀(BobFull)**의 Spring Boot 백엔드 저장소입니다.

## 프로젝트 정보

- 프로젝트명: 밥풀(BobFull)
- 팀명: 밥조
- 프로젝트 기간: 2026.07.21 ~ 2026.08.24
- 핵심 타깃: 제주 평일 저녁, 혼자 여행하는 사용자
- 핵심 가치: 합석 모집, 사용자별 인원 단위 예약금, 예약·환불·노쇼·지급 예정금 통합 관리

## 핵심 문제

- 2인 이상 주문 또는 테이블 단위 운영으로 1인 고객이 이용하기 어려운 문제
- 함께 식사할 사람을 직접 구해야 하는 불편
- 합석 테이블의 빈 좌석과 낮은 좌석 활용도
- 취소와 노쇼로 인한 식당 손실
- 예약·결제·환불·노쇼·지급 예정금을 개별적으로 관리해야 하는 문제

## 핵심 정책

- API 기능 단계는 `V1·V2·V3`로 구분하지만 호출 URL에는 버전을 넣지 않고 `/api/**`를 사용합니다.
- 역할은 일반 사용자 `MEMBER`, 식당 소유자 `OWNER`, 운영 조회·재처리 권한의 `ADMIN`으로 구분합니다.
- Actuator는 `/actuator/**`, WebSocket 연결 Endpoint는 `/ws`를 사용합니다.
- 합석 테이블 정원, `partySize`, 결제 금액, 참여 인원과 잔여 정원의 계산 기준은 [프로젝트 컨텍스트](./docs/PROJECT_CONTEXT.md)와 [ERD](./docs/ERD.md)를 따릅니다.
- 예약 상태와 모집 상태를 분리합니다. 상태값과 테이블별 확정 기준은 [프로젝트 컨텍스트](./docs/PROJECT_CONTEXT.md)를 따릅니다.
- `CONFIRMED`는 식사 진행 확정이며 추가 모집 종료를 뜻하지 않습니다.
- 모집이 `OPEN`이고 잔여 정원이 있으면 추가 참여할 수 있습니다.
- 최초 예약자는 허용 상태에서 모집을 마감할 수 있으며 다시 열 수 없습니다. 마감·취소·환불의 상세 조건은 [프로젝트 컨텍스트](./docs/PROJECT_CONTEXT.md)와 [API 명세](./docs/BOBFULL_API_SPEC_COMPLETE.md)를 따릅니다.

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
→ 결제 당사자가 서버 완료 검증 호출
→ 서버가 결제 상태·금액·통화와 당사자를 검증
→ 결제 성공 시 예약·최초 참여자 생성·Payment PAID
→ 결제 실패·만료 시 좌석 해제

추가 참여자
→ 모집 상태 OPEN인 예약 조회
→ 남은 참여 가능 인원 이내에서 partySize 입력
→ 좌석 10분 임시 선점·PortOne 예약금 결제
→ 서버 검증 성공 시 참여자 등록
→ 현재 참여 인원·임시 선점 인원·예약 상태 재계산

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

## 대표 구현·검증 현황

문서에 존재하는 설계와 실제 구현·검증·실측을 같은 의미로 사용하지 않습니다. 아래 표는 최종 발표·README에서 사용하는 대표 기술 Claim의 현재 상태를 요약하며, 상세 조건과 한계는 [V3 Final Claim Matrix](./docs/evidence/v3/FINAL_CLAIM_MATRIX.md)를 기준으로 합니다.

| 영역 | 현재 상태 | 검증·판단 근거 |
|---|---|---|
| 예약·환불·AI·Outbox 동시성 전략 | ✅ 구현·검증 | 기존 비관적 락·조건부 UPDATE·낙관락·Outbox claim을 유지하고 보호장치 제거 시 실패를 재현했습니다. [Evidence](./docs/evidence/v3/60-concurrency-strategy/README.md) |
| Redis 식당 검색 Cache | 📊 구현·실측 | Warm hit에서 DB 조회를 우회하고 동시 요청의 Hikari Pool 점유 감소를 측정했습니다. [Evidence](./docs/evidence/v3/62-search-cache/README.md) |
| Transactional Outbox | ✅ 구현·복구 검증 | ChatRoom·이메일 후속 작업의 실패 상태 보존, 재처리, 중복 방어를 검증했습니다. [ChatRoom](./docs/evidence/v3/176-chatroom-outbox/README.md) · [Email](./docs/evidence/v3/183-email-outbox/README.md) |
| Kafka AI 후속 처리 | ✅ 구현·복구 검증 | Outbox → Kafka → Consumer로 AI Moderation을 분리하고 발행 실패 복구·Retry/DLT·중복 방어를 검증했습니다. [Evidence](./docs/evidence/v3/59-kafka-ai-pipeline/README.md) |
| AI Moderation | 📊 구현·실측 | Prompt 회귀 세트를 통해 품질을 재측정하고 production 기본 모델과 적용 범위를 결정했습니다. [Evidence](./docs/evidence/v3/66-ai-moderation/README.md) |
| App HA / Blue-Green 배포 | 📊 구현·실측 | ALB 뒤 Active App EC2 2대와 Blue-Green traffic switch·rollback을 검증하고 전환 구간의 실패·downtime을 측정했습니다. [Evidence](./docs/evidence/v3/169-app-ha/README.md) |
| App EC2 Auto Scaling | ⚪ 실측 후 미도입 | 실제 부하에서 App CPU보다 Hikari 대기가 먼저 나타나 현재 조건에서는 ASG/Scaling Policy를 도입하지 않았습니다. [Evidence](./docs/evidence/v3/191-auto-scaling/README.md) |
| Kafka AI Worker / MSA 분리 | ⚪ 실측 후 미도입 | AI 지연·Consumer 중단/복구를 측정한 뒤 별도 Worker로 분리하지 않고 현재 모놀리스 내부 Consumer 구조를 유지했습니다. [Evidence](./docs/evidence/v3/192-ai-worker-scaling/README.md) |

상태 표기는 다음 기준으로 사용합니다.

- `✅ 구현·검증`: 생산 코드가 반영됐고 기능·장애·정합성·복구 등 검증 근거가 있음
- `📊 구현·실측`: 생산 코드가 반영됐고 정량 측정 Evidence가 있음
- `🟡 구현·실측 미완료`: 구현은 완료됐지만 최종 실측 근거가 아직 부족함
- `⚪ 실측 후 미도입`: 후보 기술을 직접 측정·검토한 뒤 현재 조건에서는 채택하지 않음
- `🔵 Future`: 후속 확장으로만 정의됐으며 현재 구현 기능으로 주장하지 않음

## 주요 문서

| 문서 | 역할 |
|---|---|
| [`docs/PRD.md`](./docs/PRD.md) | 제품 방향·초기 타깃·MVP 범위 요약 |
| [`docs/PROJECT_CONTEXT.md`](./docs/PROJECT_CONTEXT.md) | 확정 정책·버전·역할의 단일 기준 |
| [`docs/BOBFULL_API_SPEC_COMPLETE.md`](./docs/BOBFULL_API_SPEC_COMPLETE.md) | 전체 HTTP API 계약 |
| [`docs/ERD.md`](./docs/ERD.md) | 확정 API·정책을 구현 가능한 관계형 데이터 모델로 표현 |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | 확정 계약을 연결하는 논리 아키텍처와 책임 경계 |
| [`docs/adr/README.md`](./docs/adr/README.md) | 중요한 기술·구조 결정의 ADR 운영 기준과 템플릿 |
| [`docs/ISSUE_TITLE_RULES.md`](./docs/ISSUE_TITLE_RULES.md) | Issue 버전·유형 제목 규칙 |
| [`AGENTS.md`](./AGENTS.md) | AI 작업 진입점 |
| [`docs/bobfull_full_flowchart_mermaid.md`](./docs/bobfull_full_flowchart_mermaid.md) | 전체 업무 흐름 |
| [`docs/DOMAIN_DEPENDENCIES.md`](./docs/DOMAIN_DEPENDENCIES.md) | 도메인 의존성과 변경 영향 |
| [`docs/AI_WORKFLOW.md`](./docs/AI_WORKFLOW.md) | AI 협업 전체 흐름 |
| [`docs/AI_IMPLEMENTATION_GUIDE.md`](./docs/AI_IMPLEMENTATION_GUIDE.md) | 구현 AI 실행 기준 |
| [`docs/AI_REVIEW_GUIDE.md`](./docs/AI_REVIEW_GUIDE.md) | AI PR 리뷰 기준 |
| [`docs/CODE_CONVENTION.md`](./docs/CODE_CONVENTION.md) | 코드 작성 규칙 |
| [`docs/COMMON_SKELETON_GUIDE.md`](./docs/COMMON_SKELETON_GUIDE.md) | 공통 응답·예외·인증·시간 골격 사용 가이드 |
| [`docs/TEST_CONVENTION.md`](./docs/TEST_CONVENTION.md) | 테스트·검증 규칙 |
| [`docs/GITHUB_RULES.md`](./docs/GITHUB_RULES.md) | Git 협업 규칙 |

## Team

| 담당자 | 주요 담당 |
|---|---|
| 김현승 | 예약금 결제·환불·지급 예정 예약금, AI·채팅 |
| 김홍기 | 배포·인프라·모니터링 전반, 합석 테이블·예약 시간·검색 |
| 배지현 | 프론트엔드 전반, 예약·참여·좌석 재고·동시성 |
| 정용태 | 회원·인증·사장님·식당·관리자, 캐시·조회 성능·K6 |

김홍기의 배포·인프라·모니터링 범위는 AWS, CI/CD, Blue-Green, App EC2 다중화, Redis/Kafka 운영 구성, Prometheus/Grafana, 인프라 장애·병목 검증을 포함한다.

## Development Principles

- 최신 확정 기획과 충돌하는 이전 정책은 사용하지 않습니다.
- 이해하지 못한 코드는 병합하지 않습니다.
- 실행하지 않은 테스트는 통과했다고 기록하지 않습니다.
- 성능 수치 없이 성능 개선을 주장하지 않습니다.
- v1 핵심 예약 거래 흐름을 부가 기술보다 우선합니다.
- 예약금 결제는 PortOne 실제 PG 연동으로 구현합니다. 식사대금 결제·POS 연동·계좌 송금은 구현하지 않습니다.
- V1은 예약·결제·환불 조회와 지급 예정 금액, V2는 취소·노쇼·채팅·관리자 조회, V3는 운영 재처리·모니터링·AI 후속 처리·배포 고도화를 다룹니다.
- Redis는 인증 상태, 식당 검색 Cache, 다중 App EC2 채팅 Pub/Sub에 사용합니다. Redis Pub/Sub은 실시간 fan-out 전용 best-effort 경로이며, 놓친 메시지 복구는 DB cursor 조회가 담당합니다.
- Kafka는 ChatMessage AI Moderation 후속 처리와 Restaurant Feedback Insight Event Reuse에 사용합니다. Kafka를 채팅 실시간 전파나 무조건적인 성능 개선 근거로 표현하지 않습니다.
- Transactional Outbox는 ChatRoom 생성, 이메일 발송, ChatMessage Kafka 발행 의도를 핵심 트랜잭션과 함께 보존하는 경계로 사용합니다.
- Auto Scaling은 #191에서 측정 후 현재 조건에서는 미도입으로 판단했으며, 운영 live 상태 확인은 최종 Human QA 대상입니다.

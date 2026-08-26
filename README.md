# BobFull Backend

제주에서 혼자 이용하기 어려운 식당을 여러 1인 사용자가 함께 예약할 수 있도록 지원하는 **합석형 좌석 예약 플랫폼 BobFull(밥풀)**의 Spring Boot 백엔드 저장소입니다.

[🏠 Project Home](https://github.com/bobfull-project) · [📚 Technical Docs](https://github.com/bobfull-project/bobfull-docs) · [🖥️ Frontend](https://github.com/bobfull-project/bobfull-frontend) · [🔬 Flow Lab](https://bobfull-project.github.io/bobfull-docs/flow-lab/v3/operations-flow-lab/)

---

## 1. Project Overview

| 항목 | 내용 |
|---|---|
| 프로젝트 | BobFull(밥풀) |
| 팀 | 밥조 |
| 기간 | 2026.07.21 ~ 2026.08.24 |
| 핵심 타깃 | 제주 평일 저녁, 혼자 여행하는 사용자 |
| 핵심 가치 | 합석 모집 · 인원 단위 예약금 · 예약/환불/노쇼/지급 예정금 통합 관리 |
| 사용자 역할 | `MEMBER` · `OWNER` · `ADMIN` |

### 해결하려는 문제

- 2인 이상 주문 또는 테이블 단위 운영으로 혼자 이용하기 어려운 식당 문제
- 사용자가 직접 합석 인원을 모집해야 하는 불편
- 합석 테이블의 빈 좌석과 낮은 좌석 활용도
- 취소·노쇼에 따른 식당 손실과 예약금 관리 복잡성

### 핵심 이용 흐름

1. `OWNER`가 식당, 합석 테이블, 예약 가능 회차를 등록합니다.
2. `MEMBER`가 식당·회차·인원을 선택하면 **10분간 좌석을 임시 선점**하고 `Payment READY`를 생성합니다.
3. PortOne 결제 후 서버가 결제 상태·금액·통화를 다시 검증하고 성공 시 예약/참여 상태를 확정합니다.
4. 모집 상태가 `OPEN`이고 잔여 정원이 있으면 다른 사용자가 추가 참여할 수 있습니다.
5. 모집 마감 시 확정 기준을 충족하지 못한 예약은 취소·환불 처리됩니다.

상세 상태 전이와 정책은 [`PROJECT_CONTEXT.md`](./docs/PROJECT_CONTEXT.md)와 [`BOBFULL_API_SPEC_COMPLETE.md`](./docs/BOBFULL_API_SPEC_COMPLETE.md)를 기준으로 합니다.

---

## 2. Key Features

| 영역 | 주요 기능 |
|---|---|
| 식당·회차 검색 | QueryDSL 기반 동적 검색, 날짜/시간/카테고리/키워드 조건, Redis 검색 Cache |
| 예약·좌석 | 최초 예약/추가 참여, `partySize` 단위 처리, READY Payment 기반 10분 임시 선점, 비관적 락/상태 재검증 |
| 결제·환불 | PortOne 결제 검증, Webhook, 멱등 처리, 환불·Reconciliation, 지급 예정 예약금 조회 |
| 실시간 채팅 | WebSocket/STOMP, DB 메시지 저장, Redis Pub/Sub 기반 다중 App 실시간 전달 |
| 이벤트 처리 | Transactional Outbox, Async Executor, Kafka Consumer Group, Retry/DLT |
| AI | OpenAI 기반 채팅 Moderation, Rule Fast Path, 분할 메시지 Context 보완, Restaurant Feedback Insight |
| 운영 | ADMIN 재처리·운영 조회, Actuator, Prometheus/Grafana, 구조화 로그와 장애 관측 |

---

## 3. System Architecture

<img width="1642" height="952" alt="BobFull System Architecture" src="https://github.com/user-attachments/assets/5a1371a7-7486-4fca-8a8a-43f8f1c44995" />

> 평시에는 Blue/Green 중 **Active App EC2 2대만 서비스**하며, 배포 시 Inactive 환경을 기동해 동일 이미지를 배포·검증한 뒤 ALB Weight를 전환합니다.  
> 현재 RDS는 **Single-AZ**, Kafka는 **단일 KRaft Broker**이므로 해당 계층까지 전체 HA를 보장한다고 표현하지 않습니다.

전체 Frontend 전달 경로, 이미지 업로드, CI/CD, Monitoring까지 포함한 상세 구성은 **[Technical Docs — System Architecture](https://github.com/bobfull-project/bobfull-docs/blob/main/architecture/system-architecture.md)**에서 확인할 수 있습니다.

---

## 4. Tech Stack

| 구분 | 기술 |
|---|---|
| Language / Framework | Java 17 · Spring Boot 4.1.0 |
| Web / Auth | Spring MVC · Spring Security · JWT · Validation · WebSocket/STOMP |
| Data | Spring Data JPA · QueryDSL 5.1 · MySQL 8.4 |
| Cache / Realtime | Amazon ElastiCache for Valkey · Redis Pub/Sub |
| Messaging | Apache Kafka 3.9.0 · Transactional Outbox |
| AI / External | Spring AI 2.0 · OpenAI API · PortOne · SMTP |
| AWS | EC2 · ALB · RDS · ElastiCache · ECR · S3 · Lambda · SSM / Parameter Store |
| CI/CD | GitHub Actions · Docker |
| Observability | Spring Boot Actuator · Prometheus · Grafana · CloudWatch |
| Test / Load | JUnit 5 · Testcontainers · k6 |

의존성 버전의 Source of Truth는 [`build.gradle`](./build.gradle), 로컬 인프라는 [`docker-compose.yml`](./docker-compose.yml)을 기준으로 합니다.

---

## 5. Backend Structure

최상위 패키지는 **도메인 기준으로 먼저 분리**하고, 각 도메인 내부에서 필요한 범위만 `controller`, `service`, `entity`, `repository`, `port`, `adapter` 등으로 나눕니다.

```text
src/main/java/com/bobfull
├── member / auth
├── restaurant / sharedtable / timeslot
├── reservation / payment
├── chat / restaurantinsight
├── outbox / kafka / notification
├── admin
└── common
```

모든 도메인에 동일한 하위 패키지 템플릿을 강제하지 않고, 외부 시스템이나 다른 도메인과의 경계가 필요한 곳에서 Port/Adapter를 사용합니다. 예를 들어 결제 도메인은 PortOne 연동과 예약 도메인 통지를 `port` / `adapter` 경계로 분리합니다.

상세 의존 관계는 [`DOMAIN_DEPENDENCIES.md`](./docs/DOMAIN_DEPENDENCIES.md)와 [`ARCHITECTURE.md`](./docs/ARCHITECTURE.md)를 확인할 수 있습니다.

---

## 6. Representative Engineering Results

README에서는 대표 결과만 요약하고, 측정 조건·Raw Log·한계는 Backend Evidence와 Technical Docs를 기준으로 합니다.

| 영역 | 대표 결과 | 상세 문서 |
|---|---|---|
| 검색 Cache | 동일 검색 150건 동시 요청에서 p95 **43ms → 14ms**, Hikari 최대 active `10/10 → 0/10`, awaiting `20 → 0` | [Evidence #62](./docs/evidence/v3/62-search-cache/README.md) · [CS-02](https://github.com/bobfull-project/bobfull-docs/blob/main/case-studies/cs-02-query-index-to-settlement-optimization.md) |
| 정산 조회 | 결합 p95 **6.5s → 30.32ms**, Hikari pending **92 → 0**. Batch/Snapshot 대신 누락 인덱스 보완을 채택 | [Evidence #65](./docs/evidence/v3/65-settlement/README.md) · [CS-02](https://github.com/bobfull-project/bobfull-docs/blob/main/case-studies/cs-02-query-index-to-settlement-optimization.md) |
| App HA / Blue-Green | 정상 배포 중 public readiness **2,787건 전부 HTTP 200**, 실패 0, 관측 다운타임 0초 | [Evidence #169](./docs/evidence/v3/169-app-ha/README.md) · [CS-01](https://github.com/bobfull-project/bobfull-docs/blob/main/case-studies/cs-01-spof-to-multi-az-blue-green.md) |
| 거래/후속 작업 | 핵심 거래와 ChatRoom·Email·AI 후속 작업의 실패 범위를 `AFTER_COMMIT → Outbox → Async/Kafka`로 분리 | [CS-03](https://github.com/bobfull-project/bobfull-docs/blob/main/case-studies/cs-03-transaction-and-followup-failure-boundary.md) |
| Kafka 선택 | 동일 조건 비교에서 Async가 더 빨랐지만, 독립 Consumer의 적체·실패 격리와 확장 경계를 이유로 Kafka를 유지 | [CS-05](https://github.com/bobfull-project/bobfull-docs/blob/main/case-studies/cs-05-outbox-async-vs-kafka.md) |
| AI Moderation | Rule Fast Path, DB Context, Prompt Injection 경계 검증으로 단건 LLM 검수의 호출 비용과 분할 메시지 한계를 보완 | [CS-04](https://github.com/bobfull-project/bobfull-docs/blob/main/case-studies/cs-04-ai-moderation-optimization.md) |

> Blue-Green 측정은 **App 계층 배포 전환 범위**의 결과입니다. RDS Single-AZ와 단일 Kafka Broker까지 포함한 전체 시스템 무중단 결과로 확대하지 않습니다.

전체 검증 주장과 근거 상태는 [`FINAL_CLAIM_MATRIX.md`](./docs/evidence/v3/FINAL_CLAIM_MATRIX.md)를 기준으로 합니다.

---

## 7. Technical Documentation

상세 기술 문서는 별도 **[bobfull-docs](https://github.com/bobfull-project/bobfull-docs)** 저장소에서 포트폴리오 관점으로 정리합니다.

| 문서 | 역할 |
|---|---|
| [System Architecture](https://github.com/bobfull-project/bobfull-docs/blob/main/architecture/system-architecture.md) | 최종 운영 구조와 책임 경계 |
| [Case Studies](https://github.com/bobfull-project/bobfull-docs/tree/main/case-studies) | `[발표]` 기준으로 정제한 대표 문제 해결 사례 |
| [ADR](https://github.com/bobfull-project/bobfull-docs/tree/main/adr) | 주요 Architecture Decision Record |
| [Technical Decisions](https://github.com/bobfull-project/bobfull-docs/tree/main/decisions) | 도메인·정책·구현 단위 의사결정 |
| [Troubleshooting](https://github.com/bobfull-project/bobfull-docs/tree/main/troubleshooting) | 개별 장애·버그·정합성 문제 해결 기록 |
| [Performance](https://github.com/bobfull-project/bobfull-docs/tree/main/performance) | 실제 측정 기반 성능 결과 |
| [Engineering Records](https://github.com/bobfull-project/bobfull-docs/tree/main/engineering-records) | 인프라·배포·모니터링의 발전 과정 |
| [Flow Lab](https://bobfull-project.github.io/bobfull-docs/flow-lab/v3/operations-flow-lab/) | 실제 코드/Evidence 기반 주요 Backend 흐름 시뮬레이션 |

### Backend Source of Truth

포트폴리오 문서와 구현 세부가 충돌할 경우 이 저장소의 최신 코드와 다음 문서를 우선합니다.

| 문서 | 기준 |
|---|---|
| [`PROJECT_CONTEXT.md`](./docs/PROJECT_CONTEXT.md) | 서비스 정책·역할·상태 기준 |
| [`BOBFULL_API_SPEC_COMPLETE.md`](./docs/BOBFULL_API_SPEC_COMPLETE.md) | HTTP API 계약 |
| [`ERD.md`](./docs/ERD.md) | 데이터 모델 |
| [`ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | 논리 아키텍처·책임 경계 |
| [`docs/adr`](./docs/adr/README.md) | 공식 번호형 ADR |
| [`FINAL_CLAIM_MATRIX.md`](./docs/evidence/v3/FINAL_CLAIM_MATRIX.md) | 구현·검증·실측 주장과 Evidence |

협업 규칙, AI 작업 가이드, 코드/테스트 Convention 등 팀 내부 개발 문서는 `docs/`에 유지하되 README의 핵심 탐색 경로와 분리합니다.

---

## 8. Local Development

### Requirements

- Java 17
- Docker / Docker Compose

### 1) 로컬 설정 준비

```bash
cp .env.example .env
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`.env`의 DB/JWT 및 필요한 외부 연동 값을 로컬 환경에 맞게 설정합니다. 실제 비밀값은 커밋하지 않습니다.

### 2) 로컬 인프라 실행

```bash
docker compose up -d mysql redis kafka
```

기본 로컬 구성은 MySQL, Redis, Kafka를 Docker로 실행하고 Spring Boot 애플리케이션은 IDE 또는 Gradle Wrapper로 직접 실행하는 방식입니다.

### 3) 애플리케이션 실행

macOS / Linux:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

전체 애플리케이션까지 Docker로 검증하려면:

```bash
docker compose --profile app up --build -d
```

---

## 9. Team

| 담당자 | 주요 담당 |
|---|---|
| 김현승 | 예약금 결제·환불·지급 예정 예약금 · AI · 채팅 |
| 김홍기 | 배포·인프라·모니터링 · 합석 테이블·예약 시간 · 검색 |
| 배지현 | 프론트엔드 전반 · 예약·참여·좌석 재고·동시성 |
| 정용태 | 회원·인증·사장님·식당·관리자 · 캐시·조회 성능·k6 |

---

## Repository Role

- **`bobfull-backend`**: 최신 코드, API/정책 계약, 공식 ADR, Raw Evidence의 Source of Truth
- **[`bobfull-docs`](https://github.com/bobfull-project/bobfull-docs)**: Architecture, Case Study, ADR, Troubleshooting, Performance를 읽기 좋게 연결한 Technical Docs
- **[`bobfull-frontend`](https://github.com/bobfull-project/bobfull-frontend)**: 사용자 Web Client

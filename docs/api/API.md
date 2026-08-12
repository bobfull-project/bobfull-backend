# BobFull API 개요

> API 계약 검증 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> Source of Truth: 실제 `Controller / DTO / Validation / SecurityConfig / ErrorCode`

전체 통합 명세는 [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)에 유지하고, 아래 문서는 같은 계약을 도메인별로 나눈 탐색용 상세 문서다.

## API 문서

| 도메인 | 상세 API 문서 |
|---|---|
| 인증 (Auth) | [auth-api.md](auth-api.md) |
| 회원 (Member) | [member-api.md](member-api.md) |
| 식당 (Restaurant) | [restaurant-api.md](restaurant-api.md) |
| 합석 테이블 / 회차 (Table / Dining Session) | [table-session-api.md](table-session-api.md) |
| 예약 (Reservation) | [reservation-api.md](reservation-api.md) |
| 결제 / 환불 / 정산 (Payment / Refund / Settlement) | [payment-api.md](payment-api.md) |
| 노쇼 (No-show) | [no-show-api.md](no-show-api.md) |
| 채팅 / 신고 (Chat / Report) | [chat-api.md](chat-api.md) |
| 관리자 / Moderation (Admin) | [admin-api.md](admin-api.md) |
| 운영 Endpoint / Webhook | [operations-api.md](operations-api.md) |

## 공통 계약

- 일반 REST 응답은 `ApiResponse<T>`를 사용한다.
- 페이징 응답은 `PageResponse<T>`의 `content/page/size/totalElements/totalPages` 구조를 사용한다.
- 권한 표기는 `PUBLIC / AUTHENTICATED / OWNER / ADMIN`으로 통일한다.
- 공통 Validation, Security, ErrorCode 전체 카탈로그는 통합 명세를 기준으로 한다.
- WebSocket 메시지 계약과 내부 Kafka/Outbox/Redis 흐름은 HTTP API 문서가 아니라 Architecture/ADR/Evidence에서 관리한다.

## 정합성 유지 규칙

API 변경 시 **실제 코드가 최종 Source of Truth**다.

1. Controller의 `Method + Path`를 변경하면 해당 도메인 문서와 통합 명세를 같은 PR에서 함께 수정한다.
2. Request/Response DTO 또는 Validation이 바뀌면 해당 도메인 문서의 DTO 계약도 같이 수정한다.
3. Security 또는 ErrorCode가 바뀌면 영향받는 도메인 문서와 통합 명세를 같이 수정한다.
4. 최종 QA에서 Controller 전체와 문서 전체를 `Method + Path` 기준으로 다시 비교한다.

현재 Application Controller HTTP API는 70개이며, 운영 Actuator Endpoint 2개를 별도로 문서화한다.
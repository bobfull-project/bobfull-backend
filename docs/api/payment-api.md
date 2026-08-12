# 결제 / 환불 / 정산 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| 구분 | Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|---:|
| 결제 | `POST` | `/api/payments/{paymentId}/complete` | `AUTHENTICATED` | Path `paymentId` | `ApiResponse<PaymentCompletionResponse>` | 200 |
| 결제 | `GET` | `/api/payments/{paymentId}` | `AUTHENTICATED` | Path `paymentId` | `ApiResponse<PaymentDetailResponse>` | 200 |
| 결제 이력 | `GET` | `/api/members/me/payments` | `AUTHENTICATED` | Query `paymentStatus?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<PaymentListResponse>>` | 200 |
| 환불 | `GET` | `/api/refunds/{refundId}` | `AUTHENTICATED` | Path `refundId` | `ApiResponse<RefundResponse>` | 200 |
| 환불 이력 | `GET` | `/api/members/me/refunds` | `AUTHENTICATED` | Query `refundStatus?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<RefundResponse>>` | 200 |
| 정산 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/expected` | `OWNER` | Path `restaurantId`; Query `startDate?`, `endDate?` | `ApiResponse<ExpectedSettlementResponse>` | 200 |
| 정산 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/reservations` | `OWNER` | Path `restaurantId`; Query `startDate?`, `endDate?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<SettlementReservationResponse>>` | 200 |
| 정산 | `GET` | `/api/owner/settlements/reservations/{reservationId}` | `OWNER` | Path `reservationId` | `ApiResponse<SettlementReservationDetailResponse>` | 200 |

PortOne Webhook은 운영 연동 문서인 [`operations-api.md`](operations-api.md)에 분리한다.

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `PaymentCompletionResponse` | `paymentId: String`, `paymentStatus: PaymentStatus`, `reservationId: Long`, `participationId: Long` | factory가 예약/참여 식별자 null을 거부 |
| `PaymentDetailResponse` | `paymentId`, `reservationId`, `participationId`, `paymentPurpose`, `partySize`, `paymentStatus`, `amount`, `currency`, `expiresAt`, `paidAt` | 미결제면 `paidAt=null` 가능 |
| `PaymentListResponse` | `paymentId`, `reservationId`, `participationId`, `paymentPurpose`, `partySize`, `amount`, `currency`, `paymentStatus`, `paidAt` | 미결제면 `paidAt=null` 가능 |
| `RefundResponse` | `refundId`, `paymentId`, `reservationId`, `amount`, `refundStatus`, `requestedAt`, `completedAt` | 상태에 따라 시간 필드 null 가능 |
| `ExpectedSettlementResponse` | `totalPaidAmount`, `totalRefundedAmount`, `expectedSettlementAmount` | `BigDecimal` |
| `SettlementReservationResponse` | `reservationId`, `diningSessionAt`, `totalPaidAmount`, `totalRefundedAmount`, `expectedSettlementAmount` | - |
| `SettlementReservationDetailResponse` | `reservationId`, `expectedSettlementAmount`, `payments: List<PaymentItem>`, `refunds: List<RefundItem>` | 중첩 목록 |
| `PaymentItem` | `paymentId: String`, `paymentStatus: String`, `amount: BigDecimal` | 정산 상세 내부 DTO |
| `RefundItem` | `refundId: Long`, `refundStatus: String`, `amount: BigDecimal` | 정산 상세 내부 DTO |

## 주요 오류

- `DUPLICATE_PAYMENT_ID` — 409
- `PAYMENT_NOT_FOUND` — 404
- `PAYMENT_ACCESS_DENIED` — 403
- `PAYMENT_VERIFICATION_FAILED`, `PAYMENT_EXPIRED` — 409
- `REFUND_ID_NOT_FOUND` — 404
- `PAYMENT_NOT_REFUNDABLE`, `REFUND_ALREADY_REQUESTED`, `REFUND_PROCESSING`, `REFUND_FAILED` — 409
- `PORTONE_REFUND_FAILED` — 502
- `REFUND_RECONCILIATION_REQUIRED` — 500

내부 결제 확정 Transaction, Outbox, Kafka, 환불 재처리 정책은 API 계약이 아니라 Architecture/ADR/Evidence 문서에서 관리한다.
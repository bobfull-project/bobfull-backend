# 예약 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `GET` | `/api/reservations/search` | `PUBLIC` | Query `keyword?`, `date?`, `time?`, `capacity?`, `minimumRemainingSeats?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<ReservationSearchResponse>>` | 200 |
| `GET` | `/api/reservations/availability` | `AUTHENTICATED` | Query `type`, `targetId`, `partySize` | `ApiResponse<ReservationAvailabilityResponse>` | 200 |
| `POST` | `/api/reservations/prepare` | `AUTHENTICATED` | `ReservationPrepareRequest` | `ApiResponse<ReservationPrepareResponse>` | 200 |
| `POST` | `/api/reservations/{reservationId}/participations/me/cancel` | `AUTHENTICATED` | Path `reservationId`; `ReservationCancellationRequest` | `ApiResponse<ReservationCancellationResponse>` | 200 |
| `GET` | `/api/members/me/reservations` | `AUTHENTICATED` | Query `reservationStatus?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<MyReservationListItemResponse>>` | 200 |
| `GET` | `/api/members/me/reservations/{reservationId}` | `AUTHENTICATED` | Path `reservationId` | `ApiResponse<MyReservationDetailResponse>` | 200 |
| `GET` | `/api/owner/restaurants/{restaurantId}/reservations` | `OWNER` | Path `restaurantId`; Query `date?`, `reservationStatus?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<OwnerReservationListItemResponse>>` | 200 |
| `GET` | `/api/owner/reservations/{reservationId}` | `OWNER` | Path `reservationId` | `ApiResponse<OwnerReservationDetailResponse>` | 200 |
| `GET` | `/api/owner/reservations/{reservationId}/participations` | `OWNER` | Path `reservationId`; Query `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<OwnerReservationParticipantResponse>>` | 200 |
| `POST` | `/api/owner/reservations/{reservationId}/cancel` | `OWNER` | Path `reservationId`; `ReservationCancellationRequest` | `ApiResponse<OwnerReservationCancellationResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `ReservationPrepareRequest` | `type` | `PaymentPurpose` | `@NotNull`; `CREATE`, `JOIN` |
|  | `targetId` | `Long` | `@NotNull` |
|  | `partySize` | `Integer` | `@NotNull`, `@Min(1)` |
| `ReservationCancellationRequest` | `reason` | `String` | `@NotBlank`, `@Size(max=255)` |

`GET /api/reservations/availability`의 `type`, `targetId`, `partySize`는 모두 필수 Query Parameter다.

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `ReservationSearchResponse` | `reservationId`, `restaurantId`, `restaurantName`, `sessionId`, `tableId`, `capacity`, `startAt`, `endAt`, `reservationStatus`, `recruitmentStatus`, `currentParticipantCount`, `availableCapacity`, `confirmationThreshold` | - |
| `ReservationAvailabilityResponse` | `available: boolean`, `availableCapacity: Integer`, `reason: String` | available factory에서는 `reason=null` |
| `ReservationPrepareResponse` | `paymentId: String`, `paymentStatus: PaymentStatus`, `amount: BigDecimal`, `expiresAt: OffsetDateTime` | - |
| `ReservationCancellationResponse` | `reservationId`, `participationId`, `participationStatus`, `cancellationScope`, `refundStatus` | - |
| `MyReservationListItemResponse` | `reservationId`, `restaurantId`, `restaurantName`, `sessionId`, `startAt`, `endAt`, `reservationStatus`, `recruitmentStatus`, `participationId`, `partySize`, `participationStatus`, `paymentStatus` | - |
| `MyReservationDetailResponse` | 위 목록 필드 + `paymentId`, `paymentStatus` | - |
| `OwnerReservationListItemResponse` | `reservationId`, `sessionId`, `tableId`, `capacity`, `startAt`, `endAt`, `reservationStatus`, `recruitmentStatus`, `currentParticipantCount`, `availableCapacity`, `confirmationThreshold` | - |
| `OwnerReservationDetailResponse` | `reservationId`, `restaurantId`, `sessionId`, `tableId`, `capacity`, `startAt`, `endAt`, `reservationStatus`, `recruitmentStatus`, `currentParticipantCount`, `availableCapacity`, `confirmationThreshold` | - |
| `OwnerReservationParticipantResponse` | `participationId`, `memberId`, `name`, `partySize`, `participationStatus` | 운영 조회용 이름 원문 |
| `OwnerReservationCancellationResponse` | `reservationId: Long` | - |

## 주요 오류

- `RESOURCE_NOT_FOUND`, `RESERVATION_ID_NOT_FOUND`, `PARTICIPATION_ID_NOT_FOUND` — 404
- `ACTIVE_RESERVATION_ALREADY_EXISTS`, `INSUFFICIENT_REMAINING_CAPACITY`, `INVALID_STATE` — 409
- `INVALID_PARTY_SIZE` — 400
- `CANCELLATION_NOT_ALLOWED` — 403
- `CANCELLATION_DEADLINE_PASSED`, `PARTICIPATION_ALREADY_CANCELLED`, `RESERVATION_ALREADY_CANCELLED` — 409

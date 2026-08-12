# 합석 테이블 / 회차 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## 합석 테이블 Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `POST` | `/api/owner/restaurants/{restaurantId}/tables` | `OWNER` | Path `restaurantId`; `SharedTableRequest` | `ApiResponse<SharedTableIdResponse>` | 201 |
| `POST` | `/api/owner/restaurants/{restaurantId}/tables/bulk` | `OWNER` | Path `restaurantId`; `SharedTableBulkRequest` | `ApiResponse<SharedTableBulkResponse>` | 201 |
| `GET` | `/api/owner/restaurants/{restaurantId}/tables` | `OWNER` | Path `restaurantId`; Query `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<SharedTableResponse>>` | 200 |
| `GET` | `/api/owner/tables/{tableId}` | `OWNER` | Path `tableId` | `ApiResponse<SharedTableResponse>` | 200 |
| `PATCH` | `/api/owner/tables/{tableId}` | `OWNER` | Path `tableId`; `SharedTableRequest` | `ApiResponse<SharedTableIdResponse>` | 200 |
| `DELETE` | `/api/owner/tables/{tableId}` | `OWNER` | Path `tableId` | `ApiResponse<SharedTableIdResponse>` | 200 |

## 회차 Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `POST` | `/api/owner/tables/{tableId}/dining-sessions` | `OWNER` | Path `tableId`; `DiningSessionRequest` | `ApiResponse<DiningSessionIdResponse>` | 201 |
| `POST` | `/api/owner/tables/{tableId}/dining-sessions/bulk` | `OWNER` | Path `tableId`; `DiningSessionBulkRequest` | `ApiResponse<DiningSessionBulkResponse>` | 201 |
| `GET` | `/api/owner/restaurants/{restaurantId}/dining-sessions` | `OWNER` | Path `restaurantId`; Query `date?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<DiningSessionResponse>>` | 200 |
| `GET` | `/api/restaurants/{restaurantId}/dining-sessions` | `PUBLIC` | Path `restaurantId`; Query `date` required, `partySize?` | `ApiResponse<AvailableDiningSessionListResponse>` | 200 |
| `PATCH` | `/api/owner/dining-sessions/{sessionId}` | `OWNER` | Path `sessionId`; `DiningSessionRequest` | `ApiResponse<DiningSessionIdResponse>` | 200 |
| `DELETE` | `/api/owner/dining-sessions/{sessionId}` | `OWNER` | Path `sessionId` | `ApiResponse<DiningSessionIdResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `SharedTableRequest` | `capacity` | `Integer` | `@NotNull`; 허용값 2/4/6/8은 서비스/도메인 검증 |
| `SharedTableBulkRequest` | `capacity` | `Integer` | `@NotNull`; 허용값 2/4/6/8은 서비스/도메인 검증 |
|  | `count` | `Integer` | `@NotNull`, `@Min(1)`, `@Max(10)` |
| `DiningSessionRequest` | `startAt` | `LocalDateTime` | `@NotNull` |
|  | `endAt` | `LocalDateTime` | `@NotNull` |
| `DiningSessionBulkRequest` | `dates` | `List<LocalDate>` | `@NotEmpty`; 각 원소 `@NotNull` |
|  | `startTime` | `LocalTime` | `@NotNull` |
|  | `endTime` | `LocalTime` | `@NotNull` |
|  | `intervalMinutes` | `Integer` | `@NotNull`, `@Positive` |

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `SharedTableIdResponse` | `tableId: Long`, `displayNumber: Integer` | - |
| `SharedTableBulkResponse` | `createdTableCount: int`, `tables: List<SharedTableResponse>` | 중첩 테이블 목록 |
| `SharedTableResponse` | `tableId`, `restaurantId`, `displayNumber`, `capacity`, `status: SharedTableStatus` | - |
| `DiningSessionIdResponse` | `sessionId: Long` | - |
| `DiningSessionBulkResponse` | `tableId: Long`, `createdSessionCount: Integer` | - |
| `DiningSessionResponse` | `sessionId`, `tableId`, `capacity`, `startAt`, `endAt` | 시간은 `OffsetDateTime` 응답 |
| `AvailableDiningSessionListResponse` | `restaurantId: Long`, `content: List<AvailableDiningSessionResponse>` | - |
| `AvailableDiningSessionResponse` | `sessionId`, `tableId`, `capacity`, `startAt`, `endAt`, `availableCapacity`, `reservationId`, `currentParticipantCount` | 활성 Reservation이 없으면 `reservationId=null` |

## 주요 오류

- `INVALID_TABLE_CAPACITY` — 400
- `TABLE_ID_NOT_FOUND` — 404
- `TABLE_HAS_DINING_SESSION` — 409
- `TABLE_HAS_RESERVATION` — 409
- `SESSION_ID_NOT_FOUND` — 404
- `DUPLICATE_DINING_SESSION` — 409
- `SESSION_HAS_RESERVATION` — 409

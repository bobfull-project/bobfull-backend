# BobFull API Specification — develop 동기화본

> 기준 브랜치: `develop`  
> 기준 Commit: `2172941eb49e08518a88b1afac12c3e732ceef2f`  
> 동기화 Issue: `#238`  
> Source of Truth: 실제 `Controller / DTO / Validation / SecurityConfig / ErrorCode`

이 문서는 현재 `develop`에 실제 존재하는 HTTP 계약만 정리한다. 구현되지 않은 계획·과거 API·추정 계약은 포함하지 않는다.

## 1. 동기화 결과

- Spring HTTP Controller API: **70개**
- 운영 Actuator Endpoint: **2개** (`/actuator/health`, `/actuator/prometheus`)
- 최종 문서화 HTTP Route: **72개**
- `Method + Path` 기준 코드 전용 API: **0개**
- 기존 문서에서 제거한 문서 전용 오래된 API: **10개**

### 제거된 오래된 API

- `DELETE /api/members/me`
- `GET /api/admin/refunds/failed`
- `GET /api/owner/restaurants/{restaurantId}/settlements/summary`
- `GET /api/reservations/{reservationId}`
- `GET /api/reservations/{reservationId}/participations`
- `GET /api/reservations/{reservationId}/participations/me`
- `PATCH /api/reservations/{reservationId}/recruitment`
- `POST /api/admin/payments/{paymentId}/retry`
- `POST /api/admin/refunds/{refundId}/retry`
- `POST /api/admin/settlements/recalculate`

## 2. 공통 계약

### 2.1 응답 Envelope

일반 REST API는 `ApiResponse<T>`를 사용한다.

```json
{"success":true,"message":"요청이 성공했습니다.","data":{}}
```

실패 응답은 다음 형태다.

```json
{"success":false,"message":"에러 메시지","code":"ERROR_CODE"}
```

`null` 필드는 `@JsonInclude(NON_NULL)` 적용 범위에서 응답에서 제외된다.

### 2.2 페이징

`PageResponse<T>`:

```json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

Controller의 `@PageableDefault(size = 20)`가 적용되는 API는 기본 `size=20`이며 `page`, `size`, `sort`를 Spring `Pageable` 규칙으로 받는다.

### 2.3 인증·인가

| 구분 | 실제 Security 계약 |
|---|---|
| `PUBLIC` | 인증 없이 허용 |
| `AUTHENTICATED` | 유효한 JWT Access Token 필요 |
| `OWNER` | `ROLE_OWNER` 필요 (`/api/owner/**`) |
| `ADMIN` | `ROLE_ADMIN` 필요 (`/api/admin/**`) |

Security 예외:

- `POST /api/auth/logout`은 `/api/auth/**`보다 먼저 `authenticated()`로 선언돼 인증이 필요하다.
- `POST /api/auth/signup/**`, `POST /api/auth/login`, `POST /api/auth/reissue`는 공개다.
- `GET /api/restaurants`, `GET /api/restaurants/{restaurantId}`, `GET /api/restaurants/{restaurantId}/dining-sessions`, `GET /api/reservations/search`는 공개다.
- `POST /api/webhooks/portone`, `GET /actuator/health`, `GET /actuator/prometheus`, `/ws`는 공개 경로다.
- 나머지는 인증이 필요하다.

### 2.4 공통 Validation / Error 처리

- `@Valid @RequestBody` 검증 실패 → `400 INVALID_INPUT_VALUE`.
- 필수 Query Parameter 누락 또는 타입 변환 실패 → `400 INVALID_INPUT_VALUE`.
- 미인증 보호 API → `401 UNAUTHORIZED`.
- 역할 불일치 → `403 ACCESS_DENIED`.
- 처리되지 않은 예외 → `500 INTERNAL_SERVER_ERROR`.
- 관리자 신고 동시 검토에서 `ObjectOptimisticLockingFailureException` → `409 CHAT_ROOM_REPORT_ALREADY_REVIEWED`.

## 3. 전체 HTTP API

아래 `Response` 열은 Controller의 **실제 제네릭 반환 타입**이다. `ApiResponse<T>.data`의 구체 필드·중첩 구조는 **3.2 Response DTO Catalog**에 같은 기준 Commit의 실제 DTO 소스 기준으로 명시한다.

| 도메인 | Method | Path | Auth | Request | Response | Status | Controller |
|---|---|---|---|---|---|---:|---|
| 인증 | `POST` | `/api/auth/signup/users` | `PUBLIC` | Body: SignupUserRequest | `ApiResponse<SignupResponse>` | 201 | `AuthController` |
| 인증 | `POST` | `/api/auth/signup/owners` | `PUBLIC` | Body: SignupOwnerRequest | `ApiResponse<SignupResponse>` | 201 | `AuthController` |
| 인증 | `POST` | `/api/auth/login` | `PUBLIC` | Body: LoginRequest | `ApiResponse<LoginResponse>` | 200 | `AuthController` |
| 인증 | `POST` | `/api/auth/reissue` | `PUBLIC` | Body: ReissueRequest | `ApiResponse<ReissueResponse>` | 200 | `AuthController` |
| 인증 | `POST` | `/api/auth/logout` | `AUTHENTICATED` | Header: Authorization: Bearer ... | `ApiResponse<LogoutResponse>` | 200 | `AuthController` |
| 회원 | `GET` | `/api/members/me` | `AUTHENTICATED` | - | `ApiResponse<MemberResponse>` | 200 | `MemberController` |
| 회원 | `PATCH` | `/api/members/me` | `AUTHENTICATED` | Body: MemberUpdateRequest | `ApiResponse<MemberUpdateResponse>` | 200 | `MemberController` |
| 식당 | `GET` | `/api/restaurants` | `PUBLIC` | Query: keyword?, category?, date?, time?, page?, size?(20), sort? | `ApiResponse<PageResponse<RestaurantSearchResponse>>` | 200 | `RestaurantController` |
| 식당 | `GET` | `/api/restaurants/{restaurantId}` | `PUBLIC` | Path: restaurantId(Long) | `ApiResponse<RestaurantDetailResponse>` | 200 | `RestaurantController` |
| 식당 | `POST` | `/api/owner/restaurants` | `OWNER` | Body: RestaurantCreateRequest | `ApiResponse<RestaurantIdResponse>` | 201 | `OwnerRestaurantController` |
| 식당 | `GET` | `/api/owner/restaurants` | `OWNER` | Query: page?, size?(20), sort? | `ApiResponse<PageResponse<OwnerRestaurantListResponse>>` | 200 | `OwnerRestaurantController` |
| 식당 | `GET` | `/api/owner/restaurants/{restaurantId}` | `OWNER` | Path: restaurantId(Long) | `ApiResponse<OwnerRestaurantDetailResponse>` | 200 | `OwnerRestaurantController` |
| 식당 | `PATCH` | `/api/owner/restaurants/{restaurantId}` | `OWNER` | Path: restaurantId(Long); Body: RestaurantUpdateRequest | `ApiResponse<RestaurantIdResponse>` | 200 | `OwnerRestaurantController` |
| 식당 | `DELETE` | `/api/owner/restaurants/{restaurantId}` | `OWNER` | Path: restaurantId(Long) | `ApiResponse<RestaurantIdResponse>` | 200 | `OwnerRestaurantController` |
| 식당 이미지 | `POST` | `/api/owner/restaurants/images/upload-url` | `OWNER` | Body: RestaurantImageUploadUrlRequest | `ApiResponse<RestaurantImageUploadUrlResponse>` | 200 | `RestaurantImageController` |
| 합석 테이블 | `POST` | `/api/owner/restaurants/{restaurantId}/tables` | `OWNER` | Path: restaurantId(Long); Body: SharedTableRequest | `ApiResponse<SharedTableIdResponse>` | 201 | `SharedTableController` |
| 합석 테이블 | `POST` | `/api/owner/restaurants/{restaurantId}/tables/bulk` | `OWNER` | Path: restaurantId(Long); Body: SharedTableBulkRequest | `ApiResponse<SharedTableBulkResponse>` | 201 | `SharedTableController` |
| 합석 테이블 | `GET` | `/api/owner/restaurants/{restaurantId}/tables` | `OWNER` | Path: restaurantId(Long); Query: page?, size?(20), sort? | `ApiResponse<PageResponse<SharedTableResponse>>` | 200 | `SharedTableController` |
| 합석 테이블 | `GET` | `/api/owner/tables/{tableId}` | `OWNER` | Path: tableId(Long) | `ApiResponse<SharedTableResponse>` | 200 | `SharedTableController` |
| 합석 테이블 | `PATCH` | `/api/owner/tables/{tableId}` | `OWNER` | Path: tableId(Long); Body: SharedTableRequest | `ApiResponse<SharedTableIdResponse>` | 200 | `SharedTableController` |
| 합석 테이블 | `DELETE` | `/api/owner/tables/{tableId}` | `OWNER` | Path: tableId(Long) | `ApiResponse<SharedTableIdResponse>` | 200 | `SharedTableController` |
| 회차 | `POST` | `/api/owner/tables/{tableId}/dining-sessions` | `OWNER` | Path: tableId(Long); Body: DiningSessionRequest | `ApiResponse<DiningSessionIdResponse>` | 201 | `DiningSessionController` |
| 회차 | `POST` | `/api/owner/tables/{tableId}/dining-sessions/bulk` | `OWNER` | Path: tableId(Long); Body: DiningSessionBulkRequest | `ApiResponse<DiningSessionBulkResponse>` | 201 | `DiningSessionController` |
| 회차 | `GET` | `/api/owner/restaurants/{restaurantId}/dining-sessions` | `OWNER` | Path: restaurantId(Long); Query: date?, page?, size?(20), sort? | `ApiResponse<PageResponse<DiningSessionResponse>>` | 200 | `DiningSessionController` |
| 회차 | `GET` | `/api/restaurants/{restaurantId}/dining-sessions` | `PUBLIC` | Path: restaurantId(Long); Query: date(LocalDate, required), partySize? | `ApiResponse<AvailableDiningSessionListResponse>` | 200 | `DiningSessionController` |
| 회차 | `PATCH` | `/api/owner/dining-sessions/{sessionId}` | `OWNER` | Path: sessionId(Long); Body: DiningSessionRequest | `ApiResponse<DiningSessionIdResponse>` | 200 | `DiningSessionController` |
| 회차 | `DELETE` | `/api/owner/dining-sessions/{sessionId}` | `OWNER` | Path: sessionId(Long) | `ApiResponse<DiningSessionIdResponse>` | 200 | `DiningSessionController` |
| 예약 | `GET` | `/api/reservations/search` | `PUBLIC` | Query: keyword?, date?, time?, capacity?, minimumRemainingSeats?, page?, size?(20), sort? | `ApiResponse<PageResponse<ReservationSearchResponse>>` | 200 | `ReservationController` |
| 예약 | `GET` | `/api/reservations/availability` | `AUTHENTICATED` | Query: type(PaymentPurpose), targetId(Long), partySize(Integer) | `ApiResponse<ReservationAvailabilityResponse>` | 200 | `ReservationController` |
| 예약 | `POST` | `/api/reservations/prepare` | `AUTHENTICATED` | Body: ReservationPrepareRequest | `ApiResponse<ReservationPrepareResponse>` | 200 | `ReservationController` |
| 예약 | `POST` | `/api/reservations/{reservationId}/participations/me/cancel` | `AUTHENTICATED` | Path: reservationId(Long); Body: ReservationCancellationRequest | `ApiResponse<ReservationCancellationResponse>` | 200 | `ReservationController` |
| 예약 | `GET` | `/api/members/me/reservations` | `AUTHENTICATED` | Query: reservationStatus?, page?, size?(20), sort? | `ApiResponse<PageResponse<MyReservationListItemResponse>>` | 200 | `MyReservationController` |
| 예약 | `GET` | `/api/members/me/reservations/{reservationId}` | `AUTHENTICATED` | Path: reservationId(Long) | `ApiResponse<MyReservationDetailResponse>` | 200 | `MyReservationController` |
| 예약 | `GET` | `/api/owner/restaurants/{restaurantId}/reservations` | `OWNER` | Path: restaurantId(Long); Query: date?, reservationStatus?, page?, size?(20), sort? | `ApiResponse<PageResponse<OwnerReservationListItemResponse>>` | 200 | `RestaurantReservationController` |
| 예약 | `GET` | `/api/owner/reservations/{reservationId}` | `OWNER` | Path: reservationId(Long) | `ApiResponse<OwnerReservationDetailResponse>` | 200 | `OwnerReservationController` |
| 예약 | `GET` | `/api/owner/reservations/{reservationId}/participations` | `OWNER` | Path: reservationId(Long); Query: page?, size?(20), sort? | `ApiResponse<PageResponse<OwnerReservationParticipantResponse>>` | 200 | `OwnerReservationController` |
| 예약 | `POST` | `/api/owner/reservations/{reservationId}/cancel` | `OWNER` | Path: reservationId(Long); Body: ReservationCancellationRequest | `ApiResponse<OwnerReservationCancellationResponse>` | 200 | `OwnerReservationController` |
| 결제 | `POST` | `/api/payments/{paymentId}/complete` | `AUTHENTICATED` | Path: paymentId(String) | `ApiResponse<PaymentCompletionResponse>` | 200 | `PaymentController` |
| 결제 | `GET` | `/api/payments/{paymentId}` | `AUTHENTICATED` | Path: paymentId(String) | `ApiResponse<PaymentDetailResponse>` | 200 | `PaymentController` |
| 환불 | `GET` | `/api/refunds/{refundId}` | `AUTHENTICATED` | Path: refundId(Long) | `ApiResponse<RefundResponse>` | 200 | `RefundController` |
| 결제 | `GET` | `/api/members/me/payments` | `AUTHENTICATED` | Query: paymentStatus?, page?, size?(20), sort? | `ApiResponse<PageResponse<PaymentListResponse>>` | 200 | `MemberPaymentHistoryController` |
| 환불 | `GET` | `/api/members/me/refunds` | `AUTHENTICATED` | Query: refundStatus?, page?, size?(20), sort? | `ApiResponse<PageResponse<RefundResponse>>` | 200 | `MemberPaymentHistoryController` |
| 정산 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/expected` | `OWNER` | Path: restaurantId(Long); Query: startDate?, endDate? | `ApiResponse<ExpectedSettlementResponse>` | 200 | `SettlementController` |
| 정산 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/reservations` | `OWNER` | Path: restaurantId(Long); Query: startDate?, endDate?, page?, size?(20), sort? | `ApiResponse<PageResponse<SettlementReservationResponse>>` | 200 | `SettlementController` |
| 정산 | `GET` | `/api/owner/settlements/reservations/{reservationId}` | `OWNER` | Path: reservationId(Long) | `ApiResponse<SettlementReservationDetailResponse>` | 200 | `SettlementController` |
| PortOne Webhook | `POST` | `/api/webhooks/portone` | `PUBLIC` | Raw Body(String); Headers: webhook-id?, webhook-signature?, webhook-timestamp? | `ResponseEntity<Void>` | 200 / 400 | `PortOneWebhookController` |
| 노쇼 | `GET` | `/api/owner/reservations/{reservationId}/participations/no-show-candidates` | `OWNER` | Path: reservationId(Long); Query: page?, size?(20), sort? | `ApiResponse<PageResponse<NoShowCandidateResponse>>` | 200 | `NoShowController` |
| 노쇼 | `POST` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | `OWNER` | Path: reservationId(Long), participationId(Long) | `ApiResponse<NoShowProcessResponse>` | 200 | `NoShowController` |
| 노쇼 | `DELETE` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | `OWNER` | Path: reservationId(Long), participationId(Long) | `ApiResponse<NoShowProcessResponse>` | 200 | `NoShowController` |
| 노쇼 | `GET` | `/api/owner/reservations/{reservationId}/no-show-histories` | `OWNER` | Path: reservationId(Long); Query: page?, size?(20), sort? | `ApiResponse<PageResponse<NoShowHistoryResponse>>` | 200 | `NoShowController` |
| 노쇼 | `GET` | `/api/owner/restaurants/{restaurantId}/no-shows` | `OWNER` | Path: restaurantId(Long); Query: startDate?, endDate?, page?, size?(20), sort? | `ApiResponse<PageResponse<NoShowCustomerResponse>>` | 200 | `RestaurantNoShowController` |
| 채팅 | `GET` | `/api/reservations/{reservationId}/chat-room` | `AUTHENTICATED` | Path: reservationId(Long) | `ApiResponse<ChatRoomResponse>` | 200 | `ReservationChatRoomController` |
| 채팅 | `GET` | `/api/chat/rooms/{chatRoomId}/messages` | `AUTHENTICATED` | Path: chatRoomId(Long); Query: cursor?, size(default=50, 1..100) | `ApiResponse<ChatMessageSliceResponse>` | 200 | `ChatMessageQueryController` |
| 채팅 신고 | `POST` | `/api/chat-rooms/{chatRoomId}/members/{reportedMemberId}/reports` | `AUTHENTICATED` | Path: chatRoomId(Long), reportedMemberId(Long); Body: ChatRoomMemberReportCreateRequest | `ApiResponse<ChatRoomMemberReportResponse>` | 200 | `ChatRoomMemberReportController` |
| 관리자 회원 | `GET` | `/api/admin/members` | `ADMIN` | Query: keyword?, role?, deleted?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminMemberListItemResponse>>` | 200 | `AdminMemberController` |
| 관리자 회원 | `GET` | `/api/admin/members/{memberId}` | `ADMIN` | Path: memberId(Long) | `ApiResponse<AdminMemberDetailResponse>` | 200 | `AdminMemberController` |
| 관리자 식당 | `GET` | `/api/admin/restaurants` | `ADMIN` | Query: keyword?, restaurantStatus?, deleted?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminRestaurantListItemResponse>>` | 200 | `AdminRestaurantController` |
| 관리자 식당 | `GET` | `/api/admin/restaurants/{restaurantId}` | `ADMIN` | Path: restaurantId(Long) | `ApiResponse<AdminRestaurantDetailResponse>` | 200 | `AdminRestaurantController` |
| 관리자 예약 | `GET` | `/api/admin/reservations` | `ADMIN` | Query: reservationStatus?, startDate?, endDate?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminReservationListItemResponse>>` | 200 | `AdminReservationController` |
| 관리자 결제 | `GET` | `/api/admin/payments` | `ADMIN` | Query: paymentStatus?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminPaymentListItemResponse>>` | 200 | `AdminPaymentController` |
| 관리자 환불 | `GET` | `/api/admin/refunds` | `ADMIN` | Query: refundStatus?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminRefundListItemResponse>>` | 200 | `AdminRefundController` |
| 관리자 노쇼 | `GET` | `/api/admin/no-shows` | `ADMIN` | Query: memberId?, restaurantId?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminNoShowListItemResponse>>` | 200 | `AdminNoShowController` |
| 관리자 통계 | `GET` | `/api/admin/statistics/overview` | `ADMIN` | - | `ApiResponse<AdminOverviewStatisticsResponse>` | 200 | `AdminStatisticsController` |
| 관리자 통계 | `GET` | `/api/admin/statistics/restaurants` | `ADMIN` | Query: startDate?, endDate?, page?, size?(20), sort? | `ApiResponse<PageResponse<AdminRestaurantStatisticsResponse>>` | 200 | `AdminStatisticsController` |
| 관리자 통계 | `GET` | `/api/admin/statistics/members/no-show-rates` | `ADMIN` | Query: page?, size?(20), sort? | `ApiResponse<PageResponse<AdminMemberNoShowRateResponse>>` | 200 | `AdminStatisticsController` |
| 관리자 Moderation | `GET` | `/api/admin/moderation/members` | `ADMIN` | Query: status?(NORMAL\|REVIEW_REQUIRED), page?, size?(20), sort? | `ApiResponse<PageResponse<AdminMemberModerationListItemResponse>>` | 200 | `AdminModerationController` |
| 관리자 Moderation | `GET` | `/api/admin/moderation/members/{memberId}` | `ADMIN` | Path: memberId(Long) | `ApiResponse<AdminMemberModerationDetailResponse>` | 200 | `AdminModerationController` |
| 관리자 신고 | `GET` | `/api/admin/moderation/reports` | `ADMIN` | Query: status?(PENDING\|REVIEWED), page?, size?(20), sort? | `ApiResponse<PageResponse<AdminModerationReportResponse>>` | 200 | `AdminModerationReportController` |
| 관리자 신고 | `GET` | `/api/admin/moderation/reports/{reportId}` | `ADMIN` | Path: reportId(Long) | `ApiResponse<AdminModerationReportDetailResponse>` | 200 | `AdminModerationReportController` |
| 관리자 신고 | `PATCH` | `/api/admin/moderation/reports/{reportId}/review` | `ADMIN` | Path: reportId(Long); Body: AdminReportReviewRequest | `ApiResponse<AdminModerationReportResponse>` | 200 | `AdminModerationReportController` |

### 3.1 Actuator 운영 Endpoint

| Method | Path | Auth | Response | 비고 |
|---|---|---|---|---|
| `GET` | `/actuator/health` | PUBLIC | Spring Boot Actuator Health JSON | `ApiResponse` 미사용 |
| `GET` | `/actuator/prometheus` | PUBLIC | Prometheus text exposition | `ApiResponse` 미사용 |

### 3.2 Response DTO Catalog

70개 Application API의 `ApiResponse<T>.data`에 사용되는 Response DTO를 실제 `develop` DTO 선언 기준으로 정리한다. `PageResponse<T>` 자체의 `content/page/size/totalElements/totalPages` 계약은 2.2를 따른다. Java 소스에서 null 가능성이 직접 드러나는 경우만 비고에 표시하며, 그 외는 임의로 nullable 여부를 추정하지 않는다.

| DTO | 실제 필드 계약 | Nullable / 중첩 / 비고 |
|---|---|---|
| `SignupResponse` | `memberId: Long`, `email: String`, `name: String`, `role: MemberRole` | 소스에서 nullable 명시 없음 |
| `LoginResponse` | `accessToken: String`, `tokenType: String`, `refreshToken: String` | `tokenType`은 factory 기준 `Bearer` |
| `ReissueResponse` | `accessToken: String`, `refreshToken: String` | 소스에서 nullable 명시 없음 |
| `LogoutResponse` | `result: boolean` | 성공 factory는 `true` |
| `MemberResponse` | `memberId: Long`, `email: String`, `name: String`, `phoneNumber: String`, `role: MemberRole`, `businessNumber: String` | `businessNumber`는 MEMBER일 때 null이며 `NON_NULL`로 생략 |
| `MemberUpdateResponse` | `result: boolean` | 성공 factory는 `true` |
| `RestaurantSearchResponse` | `restaurantId: Long`, `name: String`, `address: String`, `category: String`, `keyword: String`, `depositPerPerson: Integer`, `imageUrl: String` | factory가 `imageUrl=null`을 허용 |
| `RestaurantDetailResponse` | `restaurantId: Long`, `name: String`, `address: String`, `category: String`, `description: String`, `keyword: String`, `depositPerPerson: Integer`, `imageUrl: String` | factory가 `imageUrl=null`을 허용 |
| `RestaurantIdResponse` | `restaurantId: Long` | 소스에서 nullable 명시 없음 |
| `OwnerRestaurantListResponse` | `restaurantId: Long`, `name: String`, `address: String`, `category: String`, `depositPerPerson: Integer`, `status: RestaurantStatus`, `imageUrl: String` | factory가 `imageUrl=null`을 허용 |
| `OwnerRestaurantDetailResponse` | `restaurantId: Long`, `name: String`, `address: String`, `category: String`, `description: String`, `keyword: String`, `depositPerPerson: Integer`, `status: RestaurantStatus`, `imageUrl: String` | factory가 `imageUrl=null`을 허용 |
| `RestaurantImageUploadUrlResponse` | `uploadUrl: String`, `tempImageKey: String`, `finalImageKey: String` | 소스에서 nullable 명시 없음 |
| `SharedTableIdResponse` | `tableId: Long`, `displayNumber: Integer` | 소스에서 nullable 명시 없음 |
| `SharedTableBulkResponse` | `createdTableCount: int`, `tables: List<SharedTableResponse>` | `tables` 중첩 DTO는 아래 행 참조 |
| `SharedTableResponse` | `tableId: Long`, `restaurantId: Long`, `displayNumber: Integer`, `capacity: Integer`, `status: SharedTableStatus` | 소스에서 nullable 명시 없음 |
| `DiningSessionIdResponse` | `sessionId: Long` | 소스에서 nullable 명시 없음 |
| `DiningSessionBulkResponse` | `tableId: Long`, `createdSessionCount: Integer` | 소스에서 nullable 명시 없음 |
| `DiningSessionResponse` | `sessionId: Long`, `tableId: Long`, `capacity: Integer`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime` | 소스에서 nullable 명시 없음 |
| `AvailableDiningSessionListResponse` | `restaurantId: Long`, `content: List<AvailableDiningSessionResponse>` | `content` 중첩 DTO는 아래 행 참조 |
| `AvailableDiningSessionResponse` | `sessionId: Long`, `tableId: Long`, `capacity: Integer`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime`, `availableCapacity: Integer`, `reservationId: Long`, `currentParticipantCount: Integer` | 활성 Reservation이 없으면 `reservationId=null` |
| `ReservationSearchResponse` | `reservationId: Long`, `restaurantId: Long`, `restaurantName: String`, `sessionId: Long`, `tableId: Long`, `capacity: Integer`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime`, `reservationStatus: ReservationStatus`, `recruitmentStatus: RecruitmentStatus`, `currentParticipantCount: Integer`, `availableCapacity: Integer`, `confirmationThreshold: Integer` | 소스에서 nullable 명시 없음 |
| `ReservationAvailabilityResponse` | `available: boolean`, `availableCapacity: Integer`, `reason: String` | available factory에서는 `reason=null` |
| `ReservationPrepareResponse` | `paymentId: String`, `paymentStatus: PaymentStatus`, `amount: BigDecimal`, `expiresAt: OffsetDateTime` | 소스에서 nullable 명시 없음 |
| `ReservationCancellationResponse` | `reservationId: Long`, `participationId: Long`, `participationStatus: ParticipationStatus`, `cancellationScope: CancellationScope`, `refundStatus: String` | 소스에서 nullable 명시 없음 |
| `MyReservationListItemResponse` | `reservationId: Long`, `restaurantId: Long`, `restaurantName: String`, `sessionId: Long`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime`, `reservationStatus: ReservationStatus`, `recruitmentStatus: RecruitmentStatus`, `participationId: Long`, `partySize: Integer`, `participationStatus: ParticipationStatus`, `paymentStatus: PaymentStatus` | 소스에서 nullable 명시 없음 |
| `MyReservationDetailResponse` | `reservationId: Long`, `restaurantId: Long`, `restaurantName: String`, `sessionId: Long`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime`, `reservationStatus: ReservationStatus`, `recruitmentStatus: RecruitmentStatus`, `participationId: Long`, `partySize: Integer`, `participationStatus: ParticipationStatus`, `paymentId: String`, `paymentStatus: PaymentStatus` | 소스에서 nullable 명시 없음 |
| `OwnerReservationListItemResponse` | `reservationId: Long`, `sessionId: Long`, `tableId: Long`, `capacity: Integer`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime`, `reservationStatus: ReservationStatus`, `recruitmentStatus: RecruitmentStatus`, `currentParticipantCount: Integer`, `availableCapacity: Integer`, `confirmationThreshold: Integer` | 소스에서 nullable 명시 없음 |
| `OwnerReservationDetailResponse` | `reservationId: Long`, `restaurantId: Long`, `sessionId: Long`, `tableId: Long`, `capacity: Integer`, `startAt: OffsetDateTime`, `endAt: OffsetDateTime`, `reservationStatus: ReservationStatus`, `recruitmentStatus: RecruitmentStatus`, `currentParticipantCount: Integer`, `availableCapacity: Integer`, `confirmationThreshold: Integer` | 소스에서 nullable 명시 없음 |
| `OwnerReservationParticipantResponse` | `participationId: Long`, `memberId: Long`, `name: String`, `partySize: Integer`, `participationStatus: ParticipationStatus` | 이름은 운영 조회용 원문 |
| `OwnerReservationCancellationResponse` | `reservationId: Long` | 소스에서 nullable 명시 없음 |
| `PaymentCompletionResponse` | `paymentId: String`, `paymentStatus: PaymentStatus`, `reservationId: Long`, `participationId: Long` | factory가 `reservationId`/`participationId` null을 거부 |
| `PaymentDetailResponse` | `paymentId: String`, `reservationId: Long`, `participationId: Long`, `paymentPurpose: PaymentPurpose`, `partySize: Integer`, `paymentStatus: PaymentStatus`, `amount: BigDecimal`, `currency: String`, `expiresAt: OffsetDateTime`, `paidAt: OffsetDateTime` | 미결제면 `paidAt=null` 가능 |
| `PaymentListResponse` | `paymentId: String`, `reservationId: Long`, `participationId: Long`, `paymentPurpose: PaymentPurpose`, `partySize: Integer`, `amount: BigDecimal`, `currency: String`, `paymentStatus: PaymentStatus`, `paidAt: OffsetDateTime` | 미결제면 `paidAt=null` 가능 |
| `RefundResponse` | `refundId: Long`, `paymentId: String`, `reservationId: Long`, `amount: BigDecimal`, `refundStatus: RefundStatus`, `requestedAt: OffsetDateTime`, `completedAt: OffsetDateTime` | `requestedAt`/`completedAt`은 상태에 따라 null 가능 |
| `ExpectedSettlementResponse` | `totalPaidAmount: BigDecimal`, `totalRefundedAmount: BigDecimal`, `expectedSettlementAmount: BigDecimal` | 소스에서 nullable 명시 없음 |
| `SettlementReservationResponse` | `reservationId: Long`, `diningSessionAt: OffsetDateTime`, `totalPaidAmount: BigDecimal`, `totalRefundedAmount: BigDecimal`, `expectedSettlementAmount: BigDecimal` | 소스에서 nullable 명시 없음 |
| `SettlementReservationDetailResponse` | `reservationId: Long`, `expectedSettlementAmount: BigDecimal`, `payments: List<PaymentItem>`, `refunds: List<RefundItem>` | 중첩 item은 아래 두 행 참조 |
| `SettlementReservationDetailResponse.PaymentItem` | `paymentId: String`, `paymentStatus: String`, `amount: BigDecimal` | 소스에서 nullable 명시 없음 |
| `SettlementReservationDetailResponse.RefundItem` | `refundId: Long`, `refundStatus: String`, `amount: BigDecimal` | 소스에서 nullable 명시 없음 |
| `NoShowCandidateResponse` | `participationId: Long`, `memberId: Long`, `name: String`, `partySize: Integer`, `participationStatus: ParticipationStatus` | `name`은 마스킹됨 |
| `NoShowProcessResponse` | `reservationId: Long`, `participationId: Long` | 소스에서 nullable 명시 없음 |
| `NoShowHistoryResponse` | `noShowHistoryId: Long`, `participationId: Long`, `memberId: Long`, `name: String`, `partySize: Integer`, `isMarked: boolean`, `processedByMemberId: Long`, `processedAt: OffsetDateTime` | `name`은 마스킹됨 |
| `NoShowCustomerResponse` | `memberId: Long`, `name: String`, `noShowCount: long`, `latestNoShowAt: OffsetDateTime`, `reservationId: Long`, `participationId: Long`, `partySize: Integer` | `name`은 마스킹됨 |
| `ChatRoomResponse` | `chatRoomId: Long`, `reservationId: Long` | 소스에서 nullable 명시 없음 |
| `ChatMessageSliceResponse` | `content: List<ChatMessageResponse>`, `nextCursor: Long` | `content` 중첩 DTO는 아래 행 참조; `nextCursor` nullable 여부는 소스 선언만으로 확정하지 않음 |
| `ChatMessageResponse` | `messageId: Long`, `senderMemberId: Long`, `senderName: String`, `content: String`, `sentAt: OffsetDateTime` | 소스에서 nullable 명시 없음 |
| `ChatRoomMemberReportResponse` | `reportId: Long`, `chatRoomId: Long`, `reporterMemberId: Long`, `reportedMemberId: Long`, `anchorMessageId: Long`, `reason: ReportReason`, `detail: String`, `status: ReportStatus`, `createdAt: Instant` | `anchorMessageId`/`detail`은 요청에서 optional; 응답 DTO는 엔티티 값을 그대로 전달 |
| `AdminMemberListItemResponse` | `memberId: Long`, `email: String`, `name: String`, `role: MemberRole`, `noShowCount: long`, `createdAt: OffsetDateTime`, `deletedAt: OffsetDateTime` | `deletedAt` nullable 여부는 소스 선언만으로 확정하지 않음 |
| `AdminMemberDetailResponse` | `memberId: Long`, `email: String`, `name: String`, `phoneNumber: String`, `role: MemberRole`, `noShowCount: long`, `createdAt: OffsetDateTime`, `deletedAt: OffsetDateTime` | `deletedAt` nullable 여부는 소스 선언만으로 확정하지 않음 |
| `AdminRestaurantListItemResponse` | `restaurantId: Long`, `ownerMemberId: Long`, `ownerName: String`, `name: String`, `category: String`, `status: RestaurantStatus`, `createdAt: OffsetDateTime` | 소스에서 nullable 명시 없음 |
| `AdminRestaurantDetailResponse` | `restaurantId: Long`, `ownerMemberId: Long`, `ownerName: String`, `name: String`, `address: String`, `category: String`, `description: String`, `keyword: String`, `depositPerPerson: Integer`, `status: RestaurantStatus`, `createdAt: OffsetDateTime`, `deletedAt: OffsetDateTime` | `deletedAt` nullable 여부는 소스 선언만으로 확정하지 않음 |
| `AdminReservationListItemResponse` | `reservationId: Long`, `restaurantId: Long`, `restaurantName: String`, `creatorMemberId: Long`, `startAt: OffsetDateTime`, `reservationStatus: ReservationStatus`, `recruitmentStatus: RecruitmentStatus`, `currentParticipantCount: long`, `capacity: Integer` | 소스에서 nullable 명시 없음 |
| `AdminPaymentListItemResponse` | `paymentId: String`, `memberId: Long`, `reservationId: Long`, `amount: BigDecimal`, `currency: String`, `paymentStatus: PaymentStatus`, `paidAt: OffsetDateTime` | 미결제면 `paidAt=null` 가능 |
| `AdminRefundListItemResponse` | `refundId: Long`, `paymentId: String`, `memberId: Long`, `reservationId: Long`, `amount: BigDecimal`, `refundStatus: RefundStatus`, `requestedAt: OffsetDateTime`, `completedAt: OffsetDateTime` | `requestedAt`/`completedAt`은 상태에 따라 null 가능 |
| `AdminNoShowListItemResponse` | `noShowHistoryId: Long`, `memberId: Long`, `memberName: String`, `restaurantId: Long`, `restaurantName: String`, `reservationId: Long`, `participationId: Long`, `partySize: Integer`, `processedAt: OffsetDateTime` | `memberName`은 마스킹됨 |
| `AdminOverviewStatisticsResponse` | `totalReservationCount: long`, `reservationConfirmationRate: double`, `noShowRate: double` | primitive 필드 |
| `AdminRestaurantStatisticsResponse` | `restaurantId: Long`, `restaurantName: String`, `totalReservationCount: long`, `confirmedReservationCount: long`, `confirmationRate: double` | 소스에서 nullable 명시 없음 |
| `AdminMemberNoShowRateResponse` | `memberId: Long`, `name: String`, `totalReservationCount: long`, `noShowCount: long`, `noShowRate: double` | `name`은 마스킹됨 |
| `AdminMemberModerationListItemResponse` | `memberId: Long`, `profanityCount: long`, `personalInformationCount: long`, `spamCount: long`, `totalFlaggedCount: long`, `reviewTargetCount: long`, `reviewStatus: MemberModerationReviewStatus`, `lastFlaggedAt: Instant` | 소스에서 nullable 명시 없음 |
| `AdminMemberModerationDetailResponse` | `memberId: Long`, `reviewStatus: MemberModerationReviewStatus`, `totalFlaggedCount: long`, `reviewTargetCount: long`, `riskCounts: Map<String, Long>`, `evidences: List<AdminMemberModerationEvidenceResponse>` | evidence 중첩 DTO는 아래 행 참조 |
| `AdminMemberModerationEvidenceResponse` | `messageId: Long`, `content: String`, `categories: Set<ModerationCategory>`, `riskLevel: RiskLevel`, `countedForReview: boolean`, `sentAt: Instant`, `analyzedAt: Instant` | 소스에서 nullable 명시 없음 |
| `AdminModerationReportResponse` | `reportId: Long`, `chatRoomId: Long`, `reporterMemberId: Long`, `reportedMemberId: Long`, `reason: ReportReason`, `status: ReportStatus`, `anchorMessageId: Long`, `createdAt: Instant`, `decision: ReviewDecision`, `reviewedByMemberId: Long`, `reviewedAt: Instant` | nullable 여부는 엔티티 상태에 따라 달라질 수 있으나 DTO 선언만으로 단정하지 않음 |
| `AdminModerationReportDetailResponse` | `reportId: Long`, `chatRoomId: Long`, `reason: ReportReason`, `detail: String`, `reporterMemberId: Long`, `reportedMemberId: Long`, `anchorMessageId: Long`, `createdAt: Instant`, `status: ReportStatus`, `context: List<ContextMessage>`, `moderationSignals: ModerationSignals`, `reportSignals: ReportSignals` | 중첩 DTO는 아래 행 참조 |
| `AdminModerationReportDetailResponse.ContextMessage` | `messageId: Long`, `senderMemberId: Long`, `content: String`, `sentAt: Instant`, `moderation: Moderation` | 소스에서 nullable 명시 없음 |
| `AdminModerationReportDetailResponse.Moderation` | `status: ModerationProcessingStatus`, `categories: Set<ModerationCategory>`, `riskLevel: RiskLevel`, `promptVersion: String`, `policyVersion: String`, `analyzedAt: Instant` | 소스에서 nullable 명시 없음 |
| `AdminModerationReportDetailResponse.ModerationSignals` | `totalFlaggedCount: long`, `reviewTargetCount: long`, `profanityCount: long`, `personalInformationCount: long`, `spamCount: long` | primitive 집계 필드 |
| `AdminModerationReportDetailResponse.ReportSignals` | `pendingReportCount: long`, `reviewedReportCount: long`, `confirmedViolationCount: long` | primitive 집계 필드 |

PortOne Webhook의 `ResponseEntity<Void>`는 `ApiResponse` 및 Response DTO를 사용하지 않으므로 Catalog 대상에서 제외한다.

## 4. Request Body DTO / Validation

Controller에서 `@Valid`로 검증되는 Body DTO의 현재 필드 계약이다.

| DTO | Field | Type | Validation / 값 계약 |
|---|---|---|---|
| `SignupUserRequest` | `email` | `String` | @NotBlank, @Email |
| `SignupUserRequest` | `password` | `String` | @NotBlank |
| `SignupUserRequest` | `name` | `String` | @NotBlank |
| `SignupUserRequest` | `phoneNumber` | `String` | @NotBlank |
| `SignupOwnerRequest` | `email` | `String` | @NotBlank, @Email |
| `SignupOwnerRequest` | `password` | `String` | @NotBlank |
| `SignupOwnerRequest` | `name` | `String` | @NotBlank |
| `SignupOwnerRequest` | `phoneNumber` | `String` | @NotBlank |
| `SignupOwnerRequest` | `businessNumber` | `String` | @NotBlank |
| `LoginRequest` | `email` | `String` | @NotBlank |
| `LoginRequest` | `password` | `String` | @NotBlank |
| `ReissueRequest` | `refreshToken` | `String` | @NotBlank |
| `MemberUpdateRequest` | `name` | `String` | @NotBlank |
| `MemberUpdateRequest` | `phoneNumber` | `String` | @NotBlank |
| `RestaurantImageUploadUrlRequest` | `extension` | `String` | @NotBlank |
| `RestaurantImageUploadUrlRequest` | `contentType` | `String` | @NotBlank |
| `RestaurantImageUploadUrlRequest` | `fileSize` | `Long` | @NotNull, @Positive |
| `RestaurantCreateRequest` | `name` | `String` | @NotBlank |
| `RestaurantCreateRequest` | `address` | `String` | @NotBlank |
| `RestaurantCreateRequest` | `category` | `String` | @NotBlank |
| `RestaurantCreateRequest` | `description` | `String` | @NotBlank |
| `RestaurantCreateRequest` | `keyword` | `String` | @NotBlank |
| `RestaurantCreateRequest` | `depositPerPerson` | `Integer` | @NotNull |
| `RestaurantCreateRequest` | `imageKey` | `String` | optional |
| `RestaurantUpdateRequest` | `name` | `String` | @NotBlank |
| `RestaurantUpdateRequest` | `description` | `String` | @NotBlank |
| `RestaurantUpdateRequest` | `keyword` | `String` | @NotBlank |
| `RestaurantUpdateRequest` | `depositPerPerson` | `Integer` | @NotNull |
| `RestaurantUpdateRequest` | `imageKey` | `String` | optional |
| `SharedTableRequest` | `capacity` | `Integer` | @NotNull; 허용값 2/4/6/8은 서비스/도메인 검증 |
| `SharedTableBulkRequest` | `capacity` | `Integer` | @NotNull; 허용값 2/4/6/8은 서비스/도메인 검증 |
| `SharedTableBulkRequest` | `count` | `Integer` | @NotNull, @Min(1), @Max(10) |
| `DiningSessionRequest` | `startAt` | `LocalDateTime` | @NotNull |
| `DiningSessionRequest` | `endAt` | `LocalDateTime` | @NotNull |
| `DiningSessionBulkRequest` | `dates` | `List<LocalDate>` | @NotEmpty; 각 원소 @NotNull |
| `DiningSessionBulkRequest` | `startTime` | `LocalTime` | @NotNull |
| `DiningSessionBulkRequest` | `endTime` | `LocalTime` | @NotNull |
| `DiningSessionBulkRequest` | `intervalMinutes` | `Integer` | @NotNull, @Positive |
| `ReservationPrepareRequest` | `type` | `PaymentPurpose` | @NotNull; `CREATE` / `JOIN` |
| `ReservationPrepareRequest` | `targetId` | `Long` | @NotNull |
| `ReservationPrepareRequest` | `partySize` | `Integer` | @NotNull, @Min(1) |
| `ReservationCancellationRequest` | `reason` | `String` | @NotBlank, @Size(max=255) |
| `ChatRoomMemberReportCreateRequest` | `reason` | `ReportReason` | @NotNull; `ABUSE` / `SPAM` / `PERSONAL_INFORMATION` / `OTHER` |
| `ChatRoomMemberReportCreateRequest` | `anchorMessageId` | `Long` | optional |
| `ChatRoomMemberReportCreateRequest` | `detail` | `String` | optional, @Size(max=500) |
| `AdminReportReviewRequest` | `decision` | `ReviewDecision` | @NotNull; `NO_VIOLATION` / `VIOLATION_CONFIRMED` |

### 4.1 Body가 없는 주요 특수 계약

- `POST /api/auth/logout`: Request Body 없음. `Authorization` Header에서 Bearer Access Token을 추출한다.
- `POST /api/webhooks/portone`: JSON DTO로 역직렬화하지 않고 **raw String body**와 PortOne webhook headers를 검증한다. 헤더가 누락되거나 서명 검증 실패면 `400`.
- `GET /api/chat/rooms/{chatRoomId}/messages`: `size` 기본값 `50`, 허용 범위 `1..100`; 범위 밖이면 Controller가 직접 `400 INVALID_INPUT_VALUE`를 발생시킨다.
- `GET /api/reservations/availability`: `type`, `targetId`, `partySize`는 필수 Query Parameter. `type`은 `CREATE | JOIN`.

## 5. Enum / Query 값

| 대상 | 값 |
|---|---|
| `PaymentPurpose` | `CREATE`, `JOIN` |
| `ReportReason` | `ABUSE`, `SPAM`, `PERSONAL_INFORMATION`, `OTHER` |
| `ReportStatus` | `PENDING`, `REVIEWED` |
| `ReviewDecision` | `NO_VIOLATION`, `VIOLATION_CONFIRMED` |
| `MemberModerationReviewStatus` | `NORMAL`, `REVIEW_REQUIRED` |

## 6. Error Code Catalog

> 아래 표는 현재 코드에 정의된 ErrorCode 목록이다. 각 Endpoint는 실행 경로에 따라 이 중 일부를 반환한다. 문서가 서비스 호출 그래프를 추정해 Endpoint별 오류를 과장하지 않도록, 실제 ErrorCode 정의를 공통 카탈로그로 관리한다.

| Domain | Code | HTTP | Message |
|---|---|---:|---|
| Common | `INVALID_INPUT_VALUE` | 400 | 요청 값이 올바르지 않습니다. |
| Common | `UNAUTHORIZED` | 401 | 인증이 필요합니다. |
| Common | `ACCESS_DENIED` | 403 | 접근 권한이 없습니다. |
| Common | `INTERNAL_SERVER_ERROR` | 500 | 서버 오류가 발생했습니다. |
| Member | `MEMBER_NOT_FOUND` | 404 | 회원을 찾을 수 없습니다. |
| Member | `MEMBER_ID_NOT_FOUND` | 404 | memberId에 해당하는 대상을 찾을 수 없습니다. |
| Member | `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 email입니다. |
| Member | `DUPLICATE_PHONE_NUMBER` | 409 | 이미 사용 중인 phoneNumber입니다. |
| Member | `DUPLICATE_BUSINESS_NUMBER` | 409 | 이미 사용 중인 businessNumber입니다. |
| Member | `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 일치하지 않습니다. |
| Restaurant | `RESTAURANT_ID_NOT_FOUND` | 404 | restaurantId에 해당하는 대상을 찾을 수 없습니다. |
| Restaurant | `RESTAURANT_DELETE_NOT_ALLOWED` | 409 | 연결된 테이블·회차·예약이 있어 삭제할 수 없습니다. |
| SharedTable | `INVALID_TABLE_CAPACITY` | 400 | capacity는 2, 4, 6, 8 중 하나여야 합니다. |
| SharedTable | `TABLE_ID_NOT_FOUND` | 404 | tableId에 해당하는 대상을 찾을 수 없습니다. |
| SharedTable | `TABLE_HAS_DINING_SESSION` | 409 | 연결된 회차가 있어 삭제할 수 없습니다. |
| SharedTable | `TABLE_HAS_RESERVATION` | 409 | 연결된 활성 예약이 있어 정원을 변경할 수 없습니다. |
| TimeSlot | `SESSION_ID_NOT_FOUND` | 404 | sessionId에 해당하는 대상을 찾을 수 없습니다. |
| TimeSlot | `DUPLICATE_DINING_SESSION` | 409 | 동일 테이블의 동일 시작 시각 회차가 이미 존재합니다. |
| TimeSlot | `SESSION_HAS_RESERVATION` | 409 | 연결된 예약이 있어 회차를 변경할 수 없습니다. |
| Reservation | `RESOURCE_NOT_FOUND` | 404 | 대상 회차 또는 예약을 찾을 수 없습니다. |
| Reservation | `RESERVATION_ID_NOT_FOUND` | 404 | reservationId에 해당하는 대상을 찾을 수 없습니다. |
| Reservation | `PARTICIPATION_ID_NOT_FOUND` | 404 | participationId에 해당하는 대상을 찾을 수 없습니다. |
| Reservation | `ACTIVE_RESERVATION_ALREADY_EXISTS` | 409 | 이미 활성 예약 또는 결제 준비가 존재합니다. |
| Reservation | `INSUFFICIENT_REMAINING_CAPACITY` | 409 | 남은 참여 가능 인원을 초과했습니다. |
| Reservation | `INVALID_PARTY_SIZE` | 400 | partySize가 올바르지 않습니다. |
| Reservation | `INVALID_STATE` | 409 | 현재 상태에서 요청을 처리할 수 없습니다. |
| Reservation | `PARTICIPATION_NOT_FOUND` | 404 | 본인 참여를 찾을 수 없습니다. |
| Reservation | `CANCELLATION_NOT_ALLOWED` | 403 | 취소할 수 없는 참여 상태입니다. |
| Reservation | `CANCELLATION_DEADLINE_PASSED` | 409 | 서버 시간 기준 식사 시작 2시간 이내에는 취소할 수 없습니다. |
| Reservation | `PARTICIPATION_ALREADY_CANCELLED` | 409 | 이미 취소된 참여입니다. |
| Reservation | `RESERVATION_ALREADY_CANCELLED` | 409 | 이미 취소된 예약입니다. |
| Payment | `DUPLICATE_PAYMENT_ID` | 409 | 이미 존재하는 paymentId입니다. |
| Payment | `PAYMENT_NOT_FOUND` | 404 | 결제를 찾을 수 없습니다. |
| Payment | `PAYMENT_ACCESS_DENIED` | 403 | 결제 접근 권한이 없습니다. |
| Payment | `PAYMENT_VERIFICATION_FAILED` | 409 | 결제 검증에 실패했습니다. |
| Payment | `PAYMENT_EXPIRED` | 409 | 결제 가능 시간이 만료되었습니다. |
| Payment | `RESERVATION_CONFIRMATION_NOT_READY` | 409 | 예약 확정 기능이 아직 준비되지 않았습니다. |
| Payment | `REFUND_ID_NOT_FOUND` | 404 | 환불을 찾을 수 없습니다. |
| Payment | `PAYMENT_NOT_REFUNDABLE` | 409 | 환불 가능한 결제 상태가 아닙니다. |
| Payment | `REFUND_ALREADY_REQUESTED` | 409 | 환불이 이미 요청되었습니다. |
| Payment | `REFUND_PROCESSING` | 409 | 환불 처리 중입니다. |
| Payment | `REFUND_FAILED` | 409 | 환불에 실패했습니다. |
| Payment | `PORTONE_REFUND_FAILED` | 502 | PortOne 환불 요청에 실패했습니다. |
| Payment | `REFUND_RECONCILIATION_REQUIRED` | 500 | 환불 처리 결과 재확인이 필요합니다. |
| Chat | `CHAT_ROOM_ID_NOT_FOUND` | 404 | chatRoomId에 해당하는 대상을 찾을 수 없습니다. |
| Chat | `CHAT_MESSAGE_ID_NOT_FOUND` | 404 | messageId에 해당하는 대상을 찾을 수 없습니다. |
| Chat | `CHAT_MESSAGE_SEND_NOT_ALLOWED` | 409 | 현재 예약 상태에서는 메시지를 전송할 수 없습니다. |
| Chat | `CHAT_ROOM_NOT_READY` | 503 | 채팅방을 아직 사용할 수 없습니다. |
| Chat | `CHAT_ROOM_REPORT_DUPLICATE` | 409 | 같은 채팅방의 같은 회원은 한 번만 신고할 수 있습니다. |
| Chat | `CHAT_ROOM_REPORT_SELF_FORBIDDEN` | 400 | 자기 자신은 신고할 수 없습니다. |
| Chat | `CHAT_ROOM_REPORT_NOT_FOUND` | 404 | 신고 대상을 찾을 수 없습니다. |
| Chat | `CHAT_ROOM_REPORT_ALREADY_REVIEWED` | 409 | 이미 검토된 신고입니다. |
| Image | `INVALID_IMAGE_EXTENSION` | 400 | 허용하지 않는 이미지 확장자입니다. |
| Image | `UNSUPPORTED_IMAGE_CONTENT_TYPE` | 400 | 허용하지 않는 이미지 Content-Type입니다. |
| Image | `IMAGE_EXTENSION_CONTENT_TYPE_MISMATCH` | 400 | 이미지 확장자와 Content-Type이 일치하지 않습니다. |
| Image | `IMAGE_FILE_SIZE_EXCEEDED` | 400 | 이미지 파일 크기가 허용 범위를 초과했습니다. |
| Image | `INVALID_RESTAURANT_IMAGE_KEY` | 400 | 식당 이미지 Key가 올바르지 않습니다. |
| Image | `RESTAURANT_IMAGE_NOT_FOUND` | 400 | 검증 완료된 식당 이미지를 찾을 수 없습니다. |
| Image | `RESTAURANT_IMAGE_ALREADY_USED` | 400 | 이미 다른 식당에 연결된 이미지입니다. |
| Image | `IMAGE_STORAGE_NOT_CONFIGURED` | 500 | 이미지 저장소 설정이 올바르지 않습니다. |
| Image | `IMAGE_STORAGE_REQUEST_FAILED` | 500 | 이미지 저장소 요청에 실패했습니다. |

## 7. 구현 경계에서 확인된 계약

- `LoginRequest.email`은 현재 `@NotBlank`만 적용되고 `@Email`은 적용되지 않는다. 회원가입 email에는 `@Email`이 적용된다.
- `SharedTableRequest.capacity`는 DTO에서 `@NotNull`만 검증하고, `2/4/6/8` 허용값은 서비스/도메인 경계에서 `INVALID_TABLE_CAPACITY`로 검증한다.
- `RestaurantCreateRequest.depositPerPerson`과 `RestaurantUpdateRequest.depositPerPerson`은 현재 DTO에서 `@NotNull`만 선언돼 있다.
- 공개 회차 조회 `GET /api/restaurants/{restaurantId}/dining-sessions`는 SecurityConfig 기준 인증이 필요하지 않다.
- PortOne Webhook Header 세 개는 Controller 파라미터에서 `required=false`지만 Controller가 직접 누락을 검사해 `400`을 반환한다.
- 신고 생성은 `201 Created`가 아니라 현재 Controller 구현대로 `200 OK`를 반환한다.
- API 문서를 맞추기 위해 위 구현 계약을 변경하지 않았다.

## 8. 검증 기준

이번 동기화에서 다음 소스를 전수 대조했다.

- `src/main/java/**/controller/*Controller.java`: 30개 `@RestController`, 70개 HTTP mapping
- `SecurityConfig`: 공개/인증/OWNER/ADMIN 경계
- Body Request DTO: `@Valid` 및 Jakarta Validation 제약
- Response DTO: 70개 Application API가 참조하는 top-level·중첩 응답 필드 계약
- `GlobalExceptionHandler`: Validation, Query 타입 오류, optimistic lock, unhandled exception 처리
- `Common/Member/Restaurant/SharedTable/TimeSlot/Reservation/Payment/Chat/ImageErrorCode`
- `ApiResponse`, `PageResponse` 공통 응답 구조

### 최종 Endpoint Diff

```text
Application Controller API = 70
Documented Application API = 70
Code-only Method+Path = 0
Document-only Method+Path = 0
Actuator operational endpoints = 2
Final documented HTTP routes = 72
```

## 9. 문서 관리 원칙

- 신규/변경 API가 생기면 `Controller + DTO + Security + ErrorCode` 변경과 같은 PR에서 이 문서를 갱신한다.
- README에는 70개 Endpoint를 복사하지 않고 대표 사용자 Journey와 이 문서 링크만 둔다.
- 내부 Transaction/Lock/Outbox/Kafka/Redis/운영 정책은 `ARCHITECTURE`, ADR, Evidence 문서에서 관리하고 API 명세와 중복시키지 않는다.
- 최종 QA(#67)에서 `develop`의 Controller 목록과 `Method + Path` diff를 한 번 더 확인한다.

# 관리자 / Moderation API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

모든 Endpoint는 `ROLE_ADMIN`이 필요하다.

## 운영 조회 Endpoint

| 구분 | Method | Path | Request | Response | Status |
|---|---|---|---|---|---:|
| 회원 | `GET` | `/api/admin/members` | Query `keyword?`, `role?`, `deleted?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminMemberListItemResponse>>` | 200 |
| 회원 | `GET` | `/api/admin/members/{memberId}` | Path `memberId` | `ApiResponse<AdminMemberDetailResponse>` | 200 |
| 식당 | `GET` | `/api/admin/restaurants` | Query `keyword?`, `restaurantStatus?`, `deleted?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminRestaurantListItemResponse>>` | 200 |
| 식당 | `GET` | `/api/admin/restaurants/{restaurantId}` | Path `restaurantId` | `ApiResponse<AdminRestaurantDetailResponse>` | 200 |
| 예약 | `GET` | `/api/admin/reservations` | Query `reservationStatus?`, `startDate?`, `endDate?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminReservationListItemResponse>>` | 200 |
| 결제 | `GET` | `/api/admin/payments` | Query `paymentStatus?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminPaymentListItemResponse>>` | 200 |
| 환불 | `GET` | `/api/admin/refunds` | Query `refundStatus?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminRefundListItemResponse>>` | 200 |
| 노쇼 | `GET` | `/api/admin/no-shows` | Query `memberId?`, `restaurantId?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminNoShowListItemResponse>>` | 200 |
| 통계 | `GET` | `/api/admin/statistics/overview` | - | `ApiResponse<AdminOverviewStatisticsResponse>` | 200 |
| 통계 | `GET` | `/api/admin/statistics/restaurants` | Query `startDate?`, `endDate?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminRestaurantStatisticsResponse>>` | 200 |
| 통계 | `GET` | `/api/admin/statistics/members/no-show-rates` | Query `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminMemberNoShowRateResponse>>` | 200 |

## Moderation / 신고 Endpoint

| Method | Path | Request | Response | Status |
|---|---|---|---|---:|
| `GET` | `/api/admin/moderation/members` | Query `status?(NORMAL\|REVIEW_REQUIRED)`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminMemberModerationListItemResponse>>` | 200 |
| `GET` | `/api/admin/moderation/members/{memberId}` | Path `memberId` | `ApiResponse<AdminMemberModerationDetailResponse>` | 200 |
| `GET` | `/api/admin/moderation/reports` | Query `status?(PENDING\|REVIEWED)`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<AdminModerationReportResponse>>` | 200 |
| `GET` | `/api/admin/moderation/reports/{reportId}` | Path `reportId` | `ApiResponse<AdminModerationReportDetailResponse>` | 200 |
| `PATCH` | `/api/admin/moderation/reports/{reportId}/review` | Path `reportId`; `AdminReportReviewRequest` | `ApiResponse<AdminModerationReportResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `AdminReportReviewRequest` | `decision` | `ReviewDecision` | `@NotNull`; `NO_VIOLATION`, `VIOLATION_CONFIRMED` |

## 주요 Response DTO

| DTO | data 필드 / 구조 | Nullable / 비고 |
|---|---|---|
| `AdminMemberListItemResponse` | `memberId`, `email`, `name`, `role`, `noShowCount`, `createdAt`, `deletedAt` | DTO 선언만으로 nullable 여부를 임의 단정하지 않음 |
| `AdminMemberDetailResponse` | `memberId`, `email`, `name`, `phoneNumber`, `role`, `noShowCount`, `createdAt`, `deletedAt` | DTO 선언만으로 nullable 여부를 임의 단정하지 않음 |
| `AdminRestaurantListItemResponse` | `restaurantId`, `ownerMemberId`, `ownerName`, `name`, `category`, `status`, `createdAt` | - |
| `AdminRestaurantDetailResponse` | `restaurantId`, `ownerMemberId`, `ownerName`, `name`, `address`, `category`, `description`, `keyword`, `depositPerPerson`, `status`, `createdAt`, `deletedAt` | DTO 선언만으로 nullable 여부를 임의 단정하지 않음 |
| `AdminReservationListItemResponse` | `reservationId`, `restaurantId`, `restaurantName`, `creatorMemberId`, `startAt`, `reservationStatus`, `recruitmentStatus`, `currentParticipantCount`, `capacity` | - |
| `AdminPaymentListItemResponse` | `paymentId`, `memberId`, `reservationId`, `amount`, `currency`, `paymentStatus`, `paidAt` | 미결제면 `paidAt=null` 가능 |
| `AdminRefundListItemResponse` | `refundId`, `paymentId`, `memberId`, `reservationId`, `amount`, `refundStatus`, `requestedAt`, `completedAt` | 상태에 따라 시간 필드 null 가능 |
| `AdminNoShowListItemResponse` | `noShowHistoryId`, `memberId`, `memberName`, `restaurantId`, `restaurantName`, `reservationId`, `participationId`, `partySize`, `processedAt` | `memberName` 마스킹 |
| `AdminOverviewStatisticsResponse` | `totalReservationCount`, `reservationConfirmationRate`, `noShowRate` | primitive 집계 |
| `AdminRestaurantStatisticsResponse` | `restaurantId`, `restaurantName`, `totalReservationCount`, `confirmedReservationCount`, `confirmationRate` | - |
| `AdminMemberNoShowRateResponse` | `memberId`, `name`, `totalReservationCount`, `noShowCount`, `noShowRate` | `name` 마스킹 |
| `AdminMemberModerationListItemResponse` | `memberId`, `profanityCount`, `personalInformationCount`, `spamCount`, `totalFlaggedCount`, `reviewTargetCount`, `reviewStatus`, `lastFlaggedAt` | - |
| `AdminMemberModerationDetailResponse` | `memberId`, `reviewStatus`, `totalFlaggedCount`, `reviewTargetCount`, `riskCounts`, `evidences` | evidence 목록 포함 |
| `AdminModerationReportResponse` | `reportId`, `chatRoomId`, `reporterMemberId`, `reportedMemberId`, `reason`, `status`, `anchorMessageId`, `createdAt`, `decision`, `reviewedByMemberId`, `reviewedAt` | **PENDING 상태에서는 `decision`, `reviewedByMemberId`, `reviewedAt`이 null 가능** |
| `AdminModerationReportDetailResponse` | 신고 기본 정보 + `context`, `moderationSignals`, `reportSignals` | 아래 중첩 계약 참조 |
| `ContextMessage` | `messageId`, `senderMemberId`, `content`, `sentAt`, `moderation` | **분석 레코드가 없으면 `moderation=null` 가능** |
| `Moderation` | `status`, `categories`, `riskLevel`, `promptVersion`, `policyVersion`, `analyzedAt` | context 내부 중첩 DTO |
| `ModerationSignals` | `totalFlaggedCount`, `reviewTargetCount`, `profanityCount`, `personalInformationCount`, `spamCount` | primitive 집계 |
| `ReportSignals` | `pendingReportCount`, `reviewedReportCount`, `confirmedViolationCount` | primitive 집계 |

## 주요 계약 / 오류

- 관리자 신고 동시 검토에서 `ObjectOptimisticLockingFailureException`은 `409 CHAT_ROOM_REPORT_ALREADY_REVIEWED`로 변환된다.
- 신고 상태는 `PENDING`, `REVIEWED`다.
- Review 결정은 `NO_VIOLATION`, `VIOLATION_CONFIRMED`다.
- 회원 Moderation 조회 상태는 `NORMAL`, `REVIEW_REQUIRED`다.
- 사용자 신고 생성 Endpoint는 [`chat-api.md`](chat-api.md)에 분리한다.

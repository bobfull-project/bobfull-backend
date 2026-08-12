# 노쇼 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `GET` | `/api/owner/reservations/{reservationId}/participations/no-show-candidates` | `OWNER` | Path `reservationId`; Query `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<NoShowCandidateResponse>>` | 200 |
| `POST` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | `OWNER` | Path `reservationId`, `participationId` | `ApiResponse<NoShowProcessResponse>` | 200 |
| `DELETE` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | `OWNER` | Path `reservationId`, `participationId` | `ApiResponse<NoShowProcessResponse>` | 200 |
| `GET` | `/api/owner/reservations/{reservationId}/no-show-histories` | `OWNER` | Path `reservationId`; Query `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<NoShowHistoryResponse>>` | 200 |
| `GET` | `/api/owner/restaurants/{restaurantId}/no-shows` | `OWNER` | Path `restaurantId`; Query `startDate?`, `endDate?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<NoShowCustomerResponse>>` | 200 |

관리자 전체 노쇼 현황은 [`admin-api.md`](admin-api.md)의 `/api/admin/no-shows`를 참조한다.

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `NoShowCandidateResponse` | `participationId`, `memberId`, `name`, `partySize`, `participationStatus` | `name` 마스킹 |
| `NoShowProcessResponse` | `reservationId: Long`, `participationId: Long` | 처리/해제 공통 응답 |
| `NoShowHistoryResponse` | `noShowHistoryId`, `participationId`, `memberId`, `name`, `partySize`, `isMarked`, `processedByMemberId`, `processedAt` | `name` 마스킹 |
| `NoShowCustomerResponse` | `memberId`, `name`, `noShowCount`, `latestNoShowAt`, `reservationId`, `participationId`, `partySize` | `name` 마스킹 |

## 권한 / 계약

노쇼 처리·해제·조회 API는 모두 `ROLE_OWNER`가 필요한 `/api/owner/**` 경로다. 이름 마스킹 여부는 DTO 구현을 기준으로 하며, 사장님용 일반 예약 참여자 조회의 원문 이름 계약과 구분한다.
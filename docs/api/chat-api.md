# 채팅 / 신고 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `GET` | `/api/reservations/{reservationId}/chat-room` | `AUTHENTICATED` | Path `reservationId` | `ApiResponse<ChatRoomResponse>` | 200 |
| `GET` | `/api/chat/rooms/{chatRoomId}/messages` | `AUTHENTICATED` | Path `chatRoomId`; Query `cursor?`, `size?` | `ApiResponse<ChatMessageSliceResponse>` | 200 |
| `POST` | `/api/chat-rooms/{chatRoomId}/members/{reportedMemberId}/reports` | `AUTHENTICATED` | Path `chatRoomId`, `reportedMemberId`; `ChatRoomMemberReportCreateRequest` | `ApiResponse<ChatRoomMemberReportResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `ChatRoomMemberReportCreateRequest` | `reason` | `ReportReason` | `@NotNull`; `ABUSE`, `SPAM`, `PERSONAL_INFORMATION`, `OTHER` |
|  | `anchorMessageId` | `Long` | optional |
|  | `detail` | `String` | optional, `@Size(max=500)` |

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `ChatRoomResponse` | `chatRoomId: Long`, `reservationId: Long` | - |
| `ChatMessageSliceResponse` | `content: List<ChatMessageResponse>`, `nextCursor: Long` | cursor 기반 조회 |
| `ChatMessageResponse` | `messageId`, `senderMemberId`, `senderName`, `content`, `sentAt` | - |
| `ChatRoomMemberReportResponse` | `reportId`, `chatRoomId`, `reporterMemberId`, `reportedMemberId`, `anchorMessageId`, `reason`, `detail`, `status`, `createdAt` | 요청에서 `anchorMessageId`, `detail` optional |

## 주요 계약 / 오류

- 메시지 조회 `size` 기본값은 `50`, 허용 범위는 `1..100`이다. 범위를 벗어나면 `400 INVALID_INPUT_VALUE`다.
- 신고 생성은 현재 구현대로 `201 Created`가 아니라 `200 OK`다.
- `CHAT_ROOM_ID_NOT_FOUND`, `CHAT_MESSAGE_ID_NOT_FOUND` — 404
- `CHAT_MESSAGE_SEND_NOT_ALLOWED` — 409
- `CHAT_ROOM_NOT_READY` — 503
- `CHAT_ROOM_REPORT_DUPLICATE` — 409
- `CHAT_ROOM_REPORT_SELF_FORBIDDEN` — 400
- 관리자 신고 검토와 AI Moderation 조회는 [`admin-api.md`](admin-api.md)에서 관리한다.

실시간 STOMP/WebSocket, Redis Pub/Sub, Kafka AI 분석 흐름은 HTTP API 계약과 분리해 Architecture/ADR/Evidence에서 관리한다.
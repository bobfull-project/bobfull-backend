# 회원 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `GET` | `/api/members/me` | `AUTHENTICATED` | - | `ApiResponse<MemberResponse>` | 200 |
| `PATCH` | `/api/members/me` | `AUTHENTICATED` | `MemberUpdateRequest` | `ApiResponse<MemberUpdateResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `MemberUpdateRequest` | `name` | `String` | `@NotBlank` |
|  | `phoneNumber` | `String` | `@NotBlank` |

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `MemberResponse` | `memberId: Long`, `email: String`, `name: String`, `phoneNumber: String`, `role: MemberRole`, `businessNumber: String` | MEMBER는 `businessNumber=null`; `NON_NULL` 적용으로 응답에서 생략 |
| `MemberUpdateResponse` | `result: boolean` | 성공 factory는 `true` |

## 주요 오류

- `MEMBER_NOT_FOUND` — 404
- `MEMBER_ID_NOT_FOUND` — 404
- `DUPLICATE_PHONE_NUMBER` — 409
- `UNAUTHORIZED` — 401

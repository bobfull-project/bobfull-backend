# 인증 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `POST` | `/api/auth/signup/users` | `PUBLIC` | `SignupUserRequest` | `ApiResponse<SignupResponse>` | 201 |
| `POST` | `/api/auth/signup/owners` | `PUBLIC` | `SignupOwnerRequest` | `ApiResponse<SignupResponse>` | 201 |
| `POST` | `/api/auth/login` | `PUBLIC` | `LoginRequest` | `ApiResponse<LoginResponse>` | 200 |
| `POST` | `/api/auth/reissue` | `PUBLIC` | `ReissueRequest` | `ApiResponse<ReissueResponse>` | 200 |
| `POST` | `/api/auth/logout` | `AUTHENTICATED` | Header `Authorization: Bearer ...` | `ApiResponse<LogoutResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `SignupUserRequest` | `email` | `String` | `@NotBlank`, `@Email` |
|  | `password` | `String` | `@NotBlank` |
|  | `name` | `String` | `@NotBlank` |
|  | `phoneNumber` | `String` | `@NotBlank` |
| `SignupOwnerRequest` | `email` | `String` | `@NotBlank`, `@Email` |
|  | `password` | `String` | `@NotBlank` |
|  | `name` | `String` | `@NotBlank` |
|  | `phoneNumber` | `String` | `@NotBlank` |
|  | `businessNumber` | `String` | `@NotBlank` |
| `LoginRequest` | `email` | `String` | `@NotBlank` |
|  | `password` | `String` | `@NotBlank` |
| `ReissueRequest` | `refreshToken` | `String` | `@NotBlank` |

## Response DTO

| DTO | data 필드 |
|---|---|
| `SignupResponse` | `memberId: Long`, `email: String`, `name: String`, `role: MemberRole` |
| `LoginResponse` | `accessToken: String`, `tokenType: String`, `refreshToken: String` |
| `ReissueResponse` | `accessToken: String`, `refreshToken: String` |
| `LogoutResponse` | `result: boolean` |

`LoginResponse.tokenType`은 현재 factory 기준 `Bearer`다. `LogoutResponse` 성공 factory는 `result=true`를 반환한다.

## 주요 계약

- `POST /api/auth/logout`만 `/api/auth/**` 중 인증이 필요하다.
- `LoginRequest.email`은 현재 `@Email`이 아니라 `@NotBlank`만 적용된다.
- 주요 인증 오류는 `UNAUTHORIZED`, `INVALID_CREDENTIALS`, 회원가입 중복 오류는 `DUPLICATE_EMAIL`, `DUPLICATE_PHONE_NUMBER`, `DUPLICATE_BUSINESS_NUMBER`다.
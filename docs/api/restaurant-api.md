# 식당 API

> 기준: `develop` `2f65b0f6974ef1b9d521711e6527e2a0cefd1e4b`
> 전체 명세: [`../BOBFULL_API_SPEC_COMPLETE.md`](../BOBFULL_API_SPEC_COMPLETE.md)

## Endpoint

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---:|
| `GET` | `/api/restaurants` | `PUBLIC` | Query `keyword?`, `category?`, `date?`, `time?`, `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<RestaurantSearchResponse>>` | 200 |
| `GET` | `/api/restaurants/{restaurantId}` | `PUBLIC` | Path `restaurantId` | `ApiResponse<RestaurantDetailResponse>` | 200 |
| `POST` | `/api/owner/restaurants` | `OWNER` | `RestaurantCreateRequest` | `ApiResponse<RestaurantIdResponse>` | 201 |
| `GET` | `/api/owner/restaurants` | `OWNER` | Query `page?`, `size?`, `sort?` | `ApiResponse<PageResponse<OwnerRestaurantListResponse>>` | 200 |
| `GET` | `/api/owner/restaurants/{restaurantId}` | `OWNER` | Path `restaurantId` | `ApiResponse<OwnerRestaurantDetailResponse>` | 200 |
| `PATCH` | `/api/owner/restaurants/{restaurantId}` | `OWNER` | Path `restaurantId`; `RestaurantUpdateRequest` | `ApiResponse<RestaurantIdResponse>` | 200 |
| `DELETE` | `/api/owner/restaurants/{restaurantId}` | `OWNER` | Path `restaurantId` | `ApiResponse<RestaurantIdResponse>` | 200 |
| `POST` | `/api/owner/restaurants/images/upload-url` | `OWNER` | `RestaurantImageUploadUrlRequest` | `ApiResponse<RestaurantImageUploadUrlResponse>` | 200 |

## Request DTO / Validation

| DTO | Field | Type | Validation |
|---|---|---|---|
| `RestaurantCreateRequest` | `name` | `String` | `@NotBlank` |
|  | `address` | `String` | `@NotBlank` |
|  | `category` | `String` | `@NotBlank` |
|  | `description` | `String` | `@NotBlank` |
|  | `keyword` | `String` | `@NotBlank` |
|  | `depositPerPerson` | `Integer` | `@NotNull` |
|  | `imageKey` | `String` | optional |
| `RestaurantUpdateRequest` | `name` | `String` | `@NotBlank` |
|  | `description` | `String` | `@NotBlank` |
|  | `keyword` | `String` | `@NotBlank` |
|  | `depositPerPerson` | `Integer` | `@NotNull` |
|  | `imageKey` | `String` | optional |
| `RestaurantImageUploadUrlRequest` | `extension` | `String` | `@NotBlank` |
|  | `contentType` | `String` | `@NotBlank` |
|  | `fileSize` | `Long` | `@NotNull`, `@Positive` |

## Response DTO

| DTO | data 필드 | 비고 |
|---|---|---|
| `RestaurantSearchResponse` | `restaurantId`, `name`, `address`, `category`, `keyword`, `depositPerPerson`, `imageUrl` | `imageUrl=null` 가능 |
| `RestaurantDetailResponse` | `restaurantId`, `name`, `address`, `category`, `description`, `keyword`, `depositPerPerson`, `imageUrl` | `imageUrl=null` 가능 |
| `RestaurantIdResponse` | `restaurantId: Long` | - |
| `OwnerRestaurantListResponse` | `restaurantId`, `name`, `address`, `category`, `depositPerPerson`, `status`, `imageUrl` | `imageUrl=null` 가능 |
| `OwnerRestaurantDetailResponse` | `restaurantId`, `name`, `address`, `category`, `description`, `keyword`, `depositPerPerson`, `status`, `imageUrl` | `imageUrl=null` 가능 |
| `RestaurantImageUploadUrlResponse` | `uploadUrl: String`, `tempImageKey: String`, `finalImageKey: String` | - |

## 주요 계약 / 오류

- 공개 식당 조회 2개는 인증 없이 접근 가능하다.
- `depositPerPerson`은 현재 DTO에서 `@NotNull`만 적용된다.
- `RESTAURANT_ID_NOT_FOUND` — 404
- `RESTAURANT_DELETE_NOT_ALLOWED` — 409
- 이미지 관련 오류는 `INVALID_IMAGE_EXTENSION`, `UNSUPPORTED_IMAGE_CONTENT_TYPE`, `IMAGE_EXTENSION_CONTENT_TYPE_MISMATCH`, `IMAGE_FILE_SIZE_EXCEEDED`, `INVALID_RESTAURANT_IMAGE_KEY` 등을 사용한다.
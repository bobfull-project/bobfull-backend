# 밥풀 API Specification — 전체 반영본

이 문서는 기능 목록에 기재된 **모든 실제 HTTP API**와 API가 아닌 **V2·V3 내부 구현 정책**을 반영한다.

> 상세 요청·응답 필드는 개발 과정에서 DTO와 ERD가 확정되면 조정할 수 있다.

---

# 0. 공통 규칙

## 0.1 Base URL

```text
/api
```

> 아래 각 API의 `Path`는 Base URL을 포함한 전체 경로다(예: `/api/auth/login`).
> 기능 단계는 `[V1]·[V2]·[V3]`로 구분하지만 URL에는 버전을 넣지 않는다.

## 0.2 인증 헤더

| Key | Value | 필수 | 설명 |
|---|---|---:|---|
| `Authorization` | `Bearer {accessToken}` | Y | JWT(JSON Web Token) Access Token |

## 0.3 공통 성공 응답

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

## 0.4 공통 에러 응답

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "에러 메시지"
}
```

## 0.5 공통 페이징 응답

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 0.6 주요 역할

```text
MEMBER
OWNER
ADMIN
```

## 0.7 주요 상태

```text
ReservationStatus: RECRUITING, CONFIRMED, CANCELLED, CLOSED
RecruitmentStatus: OPEN, CLOSED
ParticipationStatus: RESERVED, NO_SHOW, CANCELLED
PaymentStatus: READY, PAID, FAILED, CANCELLED
RefundStatus: REQUESTED, PROCESSING, COMPLETED, FAILED
```

---

# 1. 공통 API

## 1-1. API 모니터링 `[V3]`

## 1. INFO

- 설명: Prometheus 연동. Spring Boot Actuator 표준 엔드포인트이며 **공통 응답(0.3~0.4) 포맷을 적용하지 않는다.**
- Method: `GET`
- Path: `/actuator/prometheus`
- Auth: 운영 환경 내부 접근
- 담당자: 김홍기

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`
- Content-Type: `text/plain`(Prometheus text exposition format, JSON이 아니다)

```text
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space",} 1.2345678E7
```

## 4. Error

- 별도 도메인 오류 없음

---

## 1-2. 애플리케이션 상태 확인 `[V3]`

## 1. INFO

- 설명: 배포 헬스 체크. Spring Boot Actuator 표준 엔드포인트이며 **공통 응답(0.3~0.4) 포맷을 적용하지 않는다.**
- Method: `GET`
- Path: `/actuator/health`
- Auth: 운영 환경 내부 접근
- 담당자: 김홍기

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "status": "UP"
}
```

## 4. Error

- 도메인 에러 코드를 사용하지 않는다. 하나 이상의 컴포넌트가 비정상이면 `503 Service Unavailable`과 함께 Actuator 표준 형식(`{"status": "DOWN", ...}`)을 그대로 반환한다.

---


# 2. 회원·인증 API

## 2-1. 일반 사용자 회원가입 `[V1]`

## 1. INFO

- 설명: 이메일·닉네임 중복 검증
- Method: `POST`
- Path: `/api/auth/signup/users`
- Auth: 불필요
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "nickname": "밥친구",
  "phoneNumber": "01012345678"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | email 값 |
| `password` | String | Y | password 값 |
| `nickname` | String | Y | nickname 값 |
| `phoneNumber` | String | Y | phoneNumber 값 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "memberId": 1,
    "email": "user@example.com",
    "nickname": "밥친구",
    "role": "MEMBER"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |

---

## 2-2. 사장님 회원가입 `[V1]`

## 1. INFO

- 설명: OWNER 권한 부여
- Method: `POST`
- Path: `/api/auth/signup/owners`
- Auth: 불필요
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "email": "owner@example.com",
  "password": "Password123!",
  "nickname": "식당사장",
  "phoneNumber": "01012345678",
  "businessNumber": "1234567890"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | email 값 |
| `password` | String | Y | password 값 |
| `nickname` | String | Y | nickname 값 |
| `phoneNumber` | String | Y | phoneNumber 값 |
| `businessNumber` | String | Y | businessNumber 값 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "memberId": 1,
    "email": "owner@example.com",
    "nickname": "식당사장",
    "role": "OWNER"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |

---

## 2-3. 로그인 `[V1]`

## 1. INFO

- 설명: Access Token 발급
- Method: `POST`
- Path: `/api/auth/login`
- Auth: 불필요
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | email 값 |
| `password` | String | Y | password 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "accessToken": "access-token",
    "refreshToken": "refresh-token",
    "tokenType": "Bearer"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |

---

## 2-4. 내 정보 조회 `[V1]`

## 1. INFO

- 설명: 로그인 사용자 기준
- Method: `GET`
- Path: `/api/members/me`
- Auth: 필요
- 담당자: 정용태

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-5. 내 정보 수정 `[V2]`

## 1. INFO

- 설명: 닉네임 중복 검증
- Method: `PATCH`
- Path: `/api/members/me`
- Auth: 필요
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "nickname": "새닉네임",
  "phoneNumber": "01098765432"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `nickname` | String | Y | nickname 값 |
| `phoneNumber` | String | Y | phoneNumber 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-6. 로그아웃 `[V2]`

## 1. INFO

- 설명: Refresh Token 도입 시
- Method: `POST`
- Path: `/api/auth/logout`
- Auth: 필요
- 담당자: 정용태

## 2. Request

### Body

```json
{}
```

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-7. 토큰 재발급 `[V2]`

## 1. INFO

- 설명: Refresh Token 도입 시
- Method: `POST`
- Path: `/api/auth/reissue`
- Auth: Refresh Token
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "refreshToken": "refresh-token"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refreshToken` | String | Y | refreshToken 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-8. 회원 탈퇴 `[V2]`

## 1. INFO

- 설명: 예약 진행 중 탈퇴 제한 검토
- Method: `DELETE`
- Path: `/api/members/me`
- Auth: 필요
- 담당자: 정용태

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---


# 3. 식당 관리 API

## 3-1. 식당 등록 `[V1]`

## 1. INFO

- 설명: OWNER 권한
- Method: `POST`
- Path: `/api/owner/restaurants`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "name": "밥풀식당",
  "address": "서울특별시 강남구 테헤란로 123",
  "category": "KOREAN",
  "description": "합석 예약이 가능한 식당입니다.",
  "depositPerPerson": 10000
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | name 값 |
| `address` | String | Y | address 값 |
| `category` | String | Y | category 값 |
| `description` | String | Y | description 값 |
| `depositPerPerson` | Integer | Y | depositPerPerson 값 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |

---

## 3-2. 내 식당 목록 조회 `[V1]`

## 1. INFO

- 설명: 본인 식당만 조회
- Method: `GET`
- Path: `/api/owner/restaurants`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |

---

## 3-3. 내 식당 상세 조회 `[V1]`

## 1. INFO

- 설명: 식당 소유권 검증
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 3-4. 식당 정보 수정 `[V1]`

## 1. INFO

- 설명: 타인 식당 수정 시 403
- Method: `PATCH`
- Path: `/api/owner/restaurants/{restaurantId}`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Body

```json
{
  "name": "밥풀 한식당",
  "description": "수정된 식당 소개",
  "depositPerPerson": 12000
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | name 값 |
| `description` | String | Y | description 값 |
| `depositPerPerson` | Integer | Y | depositPerPerson 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 3-5. 사용자용 식당 목록 조회 `[V1]`

## 1. INFO

- 설명: 운영 중인 식당만 조회
- Method: `GET`
- Path: `/api/restaurants`
- Auth: 불필요
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `category` | String | N | category 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |
| `sort` | String | N | sort 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

- 별도 도메인 오류 없음

---

## 3-6. 사용자용 식당 상세 조회 `[V1]`

## 1. INFO

- 설명: 예약금·카테고리 포함
- Method: `GET`
- Path: `/api/restaurants/{restaurantId}`
- Auth: 불필요
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 3-7. 식당 운영 상태 변경 `[V2]`

## 1. INFO

- 설명: ACTIVE·INACTIVE
- Method: `PATCH`
- Path: `/api/owner/restaurants/{restaurantId}/status`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Body

```json
{
  "status": "INACTIVE"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | Y | status 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 3-8. 식당 삭제 `[V2]`

## 1. INFO

- 설명: 실제 삭제보다 비활성화 권장
- Method: `DELETE`
- Path: `/api/owner/restaurants/{restaurantId}`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---


# 4. 식당 검색 API

## 4-1. 식당 동적 검색 `[V1]`

## 1. INFO

- 설명: QueryDSL 적용
- Method: `GET`
- Path: `/api/restaurants/search`
- Auth: 불필요
- 담당자: 김홍기

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `category` | String | N | category 조건 |
| `date` | LocalDate | N | date 조건 |
| `time` | LocalTime | N | time 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |
| `sort` | String | N | sort 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

- 별도 도메인 오류 없음

---


# 5. 합석 테이블 API

## 5-1. 합석 테이블 등록 `[V1]`

## 1. INFO

- 설명: 정원 2·4·6·8명
- Method: `POST`
- Path: `/api/owner/restaurants/{restaurantId}/tables`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Body

```json
{
  "name": "A 테이블",
  "capacity": 8
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | name 값 |
| `capacity` | Integer | Y | 2·4·6·8 중 하나 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 5-2. 합석 테이블 목록 조회 `[V1]`

## 1. INFO

- 설명: 본인 식당 권한 검증
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/tables`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 5-3. 합석 테이블 상세 조회 `[V1]`

## 1. INFO

- 설명: 테이블 정원 포함
- Method: `GET`
- Path: `/api/owner/tables/{tableId}`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | tableId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---

## 5-4. 합석 테이블 수정 `[V1]`

## 1. INFO

- 설명: 예약 존재 시 정원 변경 제한
- Method: `PATCH`
- Path: `/api/owner/tables/{tableId}`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | tableId 식별자 |

### Body

```json
{
  "name": "창가 테이블",
  "capacity": 6
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | name 값 |
| `capacity` | Integer | Y | 2·4·6·8 중 하나 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---

## 5-5. 합석 테이블 상태 변경 `[V2]`

## 1. INFO

- 설명: 사용·미사용 상태
- Method: `PATCH`
- Path: `/api/owner/tables/{tableId}/status`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | tableId 식별자 |

### Body

```json
{
  "status": "INACTIVE"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | Y | status 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 5-6. 합석 테이블 삭제 `[V2]`

## 1. INFO

- 설명: 연결된 회차 존재 시 제한
- Method: `DELETE`
- Path: `/api/owner/tables/{tableId}`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | tableId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---


# 6. 합석 회차 API

## 6-1. 합석 회차 등록 `[V1]`

## 1. INFO

- 설명: 동일 테이블·시간 중복 방지
- Method: `POST`
- Path: `/api/owner/tables/{tableId}/dining-sessions`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | tableId 식별자 |

### Body

```json
{
  "sessionDate": "2026-07-25",
  "startTime": "18:00:00",
  "endTime": "20:00:00"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionDate` | LocalDate | Y | sessionDate 값 |
| `startTime` | LocalTime | Y | startTime 값 |
| `endTime` | LocalTime | Y | endTime 값 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---

## 6-2. 사장님용 회차 목록 조회 `[V1]`

## 1. INFO

- 설명: 날짜·상태 조건
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/dining-sessions`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `date` | LocalDate | N | date 조건 |
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 6-3. 사장님용 회차 상세 조회 `[V1]`

## 1. INFO

- 설명: 테이블·시간·상태 포함
- Method: `GET`
- Path: `/api/owner/dining-sessions/{sessionId}`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionId` | Long | Y | sessionId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "sessionId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `SESSION_ID_NOT_FOUND` | sessionId에 해당하는 대상을 찾을 수 없음 |

---

## 6-4. 사용자용 예약 가능 회차 조회 `[V1]`

## 1. INFO

- 설명: 예약 가능 상태만 조회
- Method: `GET`
- Path: `/api/restaurants/{restaurantId}/dining-sessions`
- Auth: 불필요
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `date` | LocalDate | N | date 조건 |
| `partySize` | Integer | N | 신청 인원 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 6-5. 합석 회차 수정 `[V2]`

## 1. INFO

- 설명: 예약 존재 시 시간 변경 제한
- Method: `PATCH`
- Path: `/api/owner/dining-sessions/{sessionId}`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionId` | Long | Y | sessionId 식별자 |

### Body

```json
{
  "startTime": "19:00:00",
  "endTime": "21:00:00"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startTime` | LocalTime | Y | startTime 값 |
| `endTime` | LocalTime | Y | endTime 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "sessionId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `SESSION_ID_NOT_FOUND` | sessionId에 해당하는 대상을 찾을 수 없음 |

---

## 6-6. 합석 회차 삭제 `[V2]`

## 1. INFO

- 설명: 진행 예약 존재 시 제한
- Method: `DELETE`
- Path: `/api/owner/dining-sessions/{sessionId}`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionId` | Long | Y | sessionId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "sessionId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `SESSION_ID_NOT_FOUND` | sessionId에 해당하는 대상을 찾을 수 없음 |

---

## 6-7. 합석 회차 일괄 등록 `[V2]`

## 1. INFO

- 설명: 날짜 범위·요일 반복 생성
- Method: `POST`
- Path: `/api/owner/tables/{tableId}/dining-sessions/batch`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | tableId 식별자 |

### Body

```json
{
  "startDate": "2026-07-25",
  "endDate": "2026-07-31",
  "startTime": "18:00:00",
  "endTime": "20:00:00",
  "daysOfWeek": [
    "MONDAY",
    "WEDNESDAY",
    "FRIDAY"
  ]
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | Y | startDate 값 |
| `endDate` | LocalDate | Y | endDate 값 |
| `startTime` | LocalTime | Y | startTime 값 |
| `endTime` | LocalTime | Y | endTime 값 |
| `daysOfWeek` | List<String> | Y | daysOfWeek 값 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---

## 6-8. 합석 회차 상태 변경 `[V2]`

## 1. INFO

- 설명: OPEN·CLOSED
- Method: `PATCH`
- Path: `/api/owner/dining-sessions/{sessionId}/status`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionId` | Long | Y | sessionId 식별자 |

### Body

```json
{
  "status": "CLOSED"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | Y | status 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "sessionId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `SESSION_ID_NOT_FOUND` | sessionId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---


# 7. 예약 생성 API

## 7-1. 최초 예약 가능 여부 확인 `[V1]`

## 1. INFO

- 설명: 회차·좌석·마감 시각 검증
- Method: `GET`
- Path: `/api/dining-sessions/{sessionId}/reservation-availability`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionId` | Long | Y | sessionId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `partySize` | Integer | N | partySize 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "available": true,
    "remainingCapacity": 8
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `SESSION_ID_NOT_FOUND` | sessionId에 해당하는 대상을 찾을 수 없음 |

---

## 7-2. 최초 예약 결제 준비 `[V1]`

## 1. INFO

- 설명: 본인 포함 N명 신청, 좌석 10분 임시 선점, PortOne 결제용 `paymentId` 발급
- Method: `POST`
- Path: `/api/dining-sessions/{sessionId}/reservations/prepare`
- Auth: 필요
- 담당자: 배지현
- 공동 검토: 김현승(결제), 김홍기(회차·좌석), 정용태(인증)

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `sessionId` | Long | Y | sessionId 식별자 |

### Body

```json
{
  "partySize": 3
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `partySize` | Integer | Y | 본인을 포함한 신청 인원 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001",
    "paymentStatus": "READY",
    "amount": 30000,
    "expiresAt": "2026-07-25T17:10:00+09:00"
  }
}
```

- 결제 성공 전에는 최종 예약과 참여자를 생성하지 않는다.
- 결제 실패 또는 10분 만료 시 임시 선점을 해제한다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `SESSION_ID_NOT_FOUND` | sessionId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

# 8. 예약 참여 API

## 8-1. 추가 참여 가능 여부 확인 `[V1]`

## 1. INFO

- 설명: 모집 상태·잔여 정원 검증
- Method: `GET`
- Path: `/api/reservations/{reservationId}/join-availability`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `partySize` | Integer | N | partySize 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "available": true,
    "remainingCapacity": 2
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 8-2. 예약 추가 참여 결제 준비 `[V1]`

## 1. INFO

- 설명: 중복 참여 방지, 좌석 10분 임시 선점, PortOne 결제용 `paymentId` 발급
- Method: `POST`
- Path: `/api/reservations/{reservationId}/participations/prepare`
- Auth: 필요
- 담당자: 배지현
- 공동 검토: 김현승(결제), 김홍기(회차·좌석), 정용태(인증)

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{
  "partySize": 2
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `partySize` | Integer | Y | 본인을 포함한 신청 인원 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0002",
    "paymentStatus": "READY",
    "amount": 20000,
    "expiresAt": "2026-07-25T17:10:00+09:00"
  }
}
```

- 결제 성공 전에는 최종 참여자를 생성하거나 현재 참여 인원에 반영하지 않는다.
- 결제 실패 또는 10분 만료 시 임시 선점을 해제한다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

# 9. 모집 예약 검색 API

## 9-1. 참여 가능한 예약 검색 `[V1]`

## 1. INFO

- 설명: 모집 OPEN·잔여 좌석 조건
- Method: `GET`
- Path: `/api/reservations/search`
- Auth: 필요
- 담당자: 김홍기

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | String | N | restaurantId 조건 |
| `date` | LocalDate | N | date 조건 |
| `partySize` | Integer | N | partySize 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

# 10. 예약 조회 API

## 10-1. 예약 상세 조회 `[V1]`

## 1. INFO

- 설명: 모집 상태·잔여 좌석 포함
- Method: `GET`
- Path: `/api/reservations/{reservationId}`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 10-2. 내 예약 목록 조회 `[V1]`

## 1. INFO

- 설명: 최초 예약·추가 참여 통합
- Method: `GET`
- Path: `/api/members/me/reservations`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 10-3. 내 예약 상세 조회 `[V1]`

## 1. INFO

- 설명: 본인 참여 검증
- Method: `GET`
- Path: `/api/members/me/reservations/{reservationId}`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 10-4. 내 참여 정보 조회 `[V1]`

## 1. INFO

- 설명: partySize·참여 상태
- Method: `GET`
- Path: `/api/reservations/{reservationId}/participations/me`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 10-5. 예약 참여자 목록 조회 `[V1]`

## 1. INFO

- 설명: MEMBER 공개 범위 DTO
- Method: `GET`
- Path: `/api/reservations/{reservationId}/participations`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 10-6. 예약 상태 변경 이력 조회 `[V2]`

## 1. INFO

- 설명: 상태 전이 감사 로그
- Method: `GET`
- Path: `/api/reservations/{reservationId}/histories`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

# 11. 모집 관리 API

## 11-1. 추가 모집 수동 마감 `[V1]`

## 1. INFO

- 설명: 최초 예약자만 가능, CONFIRMED 상태에서만 가능
- Method: `POST`
- Path: `/api/reservations/{reservationId}/recruitment/close`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{}
```

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 11-2. 모집 상태 조회 `[V1]`

## 1. INFO

- 설명: OPEN·CLOSED
- Method: `GET`
- Path: `/api/reservations/{reservationId}/recruitment`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

# 12. 예약 취소 API

## 12-1. 취소 예상 결과 조회 `[V2]`

## 1. INFO

- 설명: 환불 예상 금액·예약 영향 계산
- Method: `GET`
- Path: `/api/reservations/{reservationId}/participations/me/cancellation-preview`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 12-2. 내 예약 참여 취소 `[V2]`

## 1. INFO

- 설명: 사용자는 예약 참여만 취소하며 결제·환불은 서버 내부에서 처리
- Method: `POST`
- Path: `/api/reservations/{reservationId}/participations/me/cancel`
- Auth: 필요
- 담당자: 배지현
- 공동 검토: 김현승(환불), 김홍기(회차 복구), 정용태(권한)

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{
  "reason": "일정 변경"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reason` | String | Y | reason 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "participationStatus": "CANCELLED",
    "refundStatus": "REQUESTED"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

# 13. 사장님 예약 관리 API

## 13-1. 식당별 예약 목록 조회 `[V1]`

## 1. INFO

- 설명: 상태·날짜 조건
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/reservations`
- Auth: `OWNER`
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `date` | LocalDate | N | date 조건 |
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 13-2. 사장님용 예약 상세 조회 `[V1]`

## 1. INFO

- 설명: 본인 식당 예약만 조회
- Method: `GET`
- Path: `/api/owner/reservations/{reservationId}`
- Auth: `OWNER`
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 13-3. 예약 참여자 목록 조회 `[V1]`

## 1. INFO

- 설명: 노쇼 처리용 정보 포함
- Method: `GET`
- Path: `/api/owner/reservations/{reservationId}/participations`
- Auth: `OWNER`
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 13-4. 식당 귀책 예약 취소 `[V2]`

## 1. INFO

- 설명: 전액 환불·회차 복구
- Method: `POST`
- Path: `/api/owner/reservations/{reservationId}/cancel`
- Auth: `OWNER`
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{
  "reason": "식당 운영 사정"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reason` | String | Y | reason 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 13-5. 시간대별 예약 현황 조회 `[V2]`

## 1. INFO

- 설명: 회차·예약 상태 확인
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/reservation-calendar`
- Auth: `OWNER`
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---


# 14. 결제 API

## 14-1. 결제 완료 검증 `[V1]`

## 1. INFO

- 설명: 프론트 PortOne 결제창 종료 후 호출. 서버가 PortOne 결제 상태·금액·통화를 재조회하고 검증한다.
- Method: `POST`
- Path: `/api/payments/{paymentId}/complete`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | 서비스와 PortOne에서 사용하는 결제 식별자 |

### Body

```json
{}
```

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001",
    "status": "PAID",
    "reservationId": 101,
    "participationId": 1001
  }
}
```

- PortOne 결제 상태·금액·통화가 모두 일치해야 한다.
- 최초 예약 결제면 예약과 최초 참여자를 생성하고, 추가 참여 결제면 참여자를 생성한다.
- 동일 `paymentId`가 재호출되면 기존 성공 결과를 반환하며 중복 예약·참여를 생성하지 않는다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `PAYMENT_ID_NOT_FOUND` | paymentId에 해당하는 결제를 찾을 수 없음 |
| `409` | `PAYMENT_VERIFICATION_FAILED` | PortOne 상태·금액·통화 검증 실패 |
| `409` | `INVALID_STATE` | 결제 만료 또는 처리할 수 없는 상태 |

---

## 14-2. PortOne 결제 웹훅 `[V1]`

## 1. INFO

- 설명: PortOne 결제 상태 변경을 서버에 동기화한다. 사장님·관리자용 API가 아니다.
- Method: `POST`
- Path: `/api/webhooks/portone`
- Auth: PortOne 웹훅 서명 검증
- 담당자: 김현승

## 2. Request

### Body

```json
{
  "type": "Transaction.Paid",
  "data": {
    "paymentId": "PAY-20260725-0001"
  }
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `type` | String | Y | PortOne 이벤트 타입 |
| `data.paymentId` | String | Y | 결제 식별자 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

- 웹훅 Payload를 그대로 신뢰하지 않고 `paymentId`로 PortOne 결제 정보를 재조회한다.
- 완료 검증 API와 웹훅이 동시에 실행돼도 상태 변경과 예약·참여 반영은 한 번만 수행한다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 웹훅 Payload 검증 실패 |
| `401` | `UNAUTHORIZED` | 웹훅 서명 검증 실패 |
| `404` | `PAYMENT_ID_NOT_FOUND` | paymentId에 해당하는 결제를 찾을 수 없음 |

---

## 14-3. 내 결제 목록 조회 `[V1]`

## 1. INFO

- 설명: 로그인 사용자 결제만 조회
- Method: `GET`
- Path: `/api/members/me/payments`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 14-4. 결제 상세 조회 `[V1]`

## 1. INFO

- 설명: 본인 결제만 조회
- Method: `GET`
- Path: `/api/payments/{paymentId}`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | paymentId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001",
    "status": "PAID",
    "amount": 10000
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `PAYMENT_ID_NOT_FOUND` | paymentId에 해당하는 대상을 찾을 수 없음 |

---

## 14-5. 결제 상태 조회 `[V2]`

## 1. INFO

- 설명: 비동기 처리 상태 확인
- Method: `GET`
- Path: `/api/payments/{paymentId}/status`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | paymentId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001",
    "status": "PAID",
    "amount": 10000
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `PAYMENT_ID_NOT_FOUND` | paymentId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 14-6. 결제 실패 재검증 `[V3]`

## 1. INFO

- 설명: 운영자 보정
- Method: `POST`
- Path: `/api/admin/payments/{paymentId}/retry`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | paymentId 식별자 |

### Body

```json
{}
```

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `404` | `PAYMENT_ID_NOT_FOUND` | paymentId에 해당하는 대상을 찾을 수 없음 |

---


# 15. 환불 API

## 15-1. 내 환불 목록 조회 `[V1]`

## 1. INFO

- 설명: 사용자 본인 환불만 조회
- Method: `GET`
- Path: `/api/members/me/refunds`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 15-2. 환불 상세 조회 `[V1]`

## 1. INFO

- 설명: 환불 사유·처리 시각
- Method: `GET`
- Path: `/api/refunds/{refundId}`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refundId` | Long | Y | refundId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "refundId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `REFUND_ID_NOT_FOUND` | refundId에 해당하는 대상을 찾을 수 없음 |

---

## 15-3. 환불 상태 조회 `[V2]`

## 1. INFO

- 설명: REQUESTED·PROCESSING·COMPLETED·FAILED
- Method: `GET`
- Path: `/api/refunds/{refundId}/status`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refundId` | Long | Y | refundId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "refundId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `REFUND_ID_NOT_FOUND` | refundId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 15-4. 실패 환불 목록 조회 `[V3]`

## 1. INFO

- 설명: 운영자 재처리 대상
- Method: `GET`
- Path: `/api/admin/refunds/failed`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 15-5. 실패 환불 재처리 `[V3]`

## 1. INFO

- 설명: 운영자 수동 재처리
- Method: `POST`
- Path: `/api/admin/refunds/{refundId}/retry`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refundId` | Long | Y | refundId 식별자 |

### Body

```json
{}
```

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `404` | `REFUND_ID_NOT_FOUND` | refundId에 해당하는 대상을 찾을 수 없음 |

---


# 16. 노쇼 API

## 16-1. 노쇼 처리 대상 참여자 조회 `[V2]`

## 1. INFO

- 설명: 식사 종료 후 본인 식당 예약
- Method: `GET`
- Path: `/api/owner/reservations/{reservationId}/participations/no-show-candidates`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 16-2. 참여자 노쇼 처리 `[V2]`

## 1. INFO

- 설명: RESERVED → NO_SHOW
- Method: `POST`
- Path: `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |
| `participationId` | Long | Y | participationId 식별자 |

### Body

```json
{
  "reason": "미방문 확인"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reason` | String | Y | reason 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "participationStatus": "NO_SHOW"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `404` | `PARTICIPATION_ID_NOT_FOUND` | participationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 16-3. 노쇼 처리 해제 `[V2]`

## 1. INFO

- 설명: NO_SHOW → RESERVED
- Method: `DELETE`
- Path: `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |
| `participationId` | Long | Y | participationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "participationStatus": "RESERVED"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `404` | `PARTICIPATION_ID_NOT_FOUND` | participationId에 해당하는 대상을 찾을 수 없음 |

---

## 16-4. 예약별 노쇼 이력 조회 `[V2]`

## 1. INFO

- 설명: 처리·해제 이력
- Method: `GET`
- Path: `/api/owner/reservations/{reservationId}/no-show-histories`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 16-5. 식당 노쇼 현황 조회 `[V2]`

## 1. INFO

- 설명: 노쇼 수·노쇼율
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/no-shows`
- Auth: `OWNER`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---


# 17. 정산 API

## 17-1. 지급 예정 금액 조회 `[V1]`

## 1. INFO

- 설명: 실제 송금 제외
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/settlements/expected`
- Auth: `OWNER`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "expectedSettlementAmount": 120000
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 17-2. 예약별 지급 예정 내역 조회 `[V1]`

## 1. INFO

- 설명: 결제·환불·노쇼 반영
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/settlements/reservations`
- Auth: `OWNER`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 17-3. 예약별 지급 예정 상세 조회 `[V1]`

## 1. INFO

- 설명: 계산 근거 표시
- Method: `GET`
- Path: `/api/owner/settlements/reservations/{reservationId}`
- Auth: `OWNER`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "status": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentPartySize": 3,
    "remainingCapacity": 5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 17-4. 기간별 결제·환불 요약 조회 `[V2]`

## 1. INFO

- 설명: 기간 통계
- Method: `GET`
- Path: `/api/owner/restaurants/{restaurantId}/settlements/summary`
- Auth: `OWNER`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 17-5. 정산 데이터 재집계 `[V3]`

## 1. INFO

- 설명: 운영자 보정
- Method: `POST`
- Path: `/api/admin/settlements/recalculate`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

### Body

```json
{
  "restaurantId": 10,
  "startDate": "2026-07-01",
  "endDate": "2026-07-31"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 값 |
| `startDate` | LocalDate | Y | startDate 값 |
| `endDate` | LocalDate | Y | endDate 값 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---


# 18. 관리자 API

## 18-1. 회원 목록 조회 `[V2]`

## 1. INFO

- 설명: 관리자 조회 전용
- Method: `GET`
- Path: `/api/admin/members`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `role` | String | N | role 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 18-2. 회원 상세 조회 `[V2]`

## 1. INFO

- 설명: 예약·노쇼 요약 포함
- Method: `GET`
- Path: `/api/admin/members/{memberId}`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `memberId` | Long | Y | memberId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "memberId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `404` | `MEMBER_ID_NOT_FOUND` | memberId에 해당하는 대상을 찾을 수 없음 |

---

## 18-3. 식당 목록 조회 `[V2]`

## 1. INFO

- 설명: 운영 상태 조건
- Method: `GET`
- Path: `/api/admin/restaurants`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 18-4. 식당 상세 조회 `[V2]`

## 1. INFO

- 설명: 등록 정보 확인
- Method: `GET`
- Path: `/api/admin/restaurants/{restaurantId}`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `restaurantId` | Long | Y | restaurantId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 18-5. 전체 예약 현황 조회 `[V2]`

## 1. INFO

- 설명: 상태별 검색
- Method: `GET`
- Path: `/api/admin/reservations`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | N | status 조건 |
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 18-6. 전체 결제 현황 조회 `[V2]`

## 1. INFO

- 설명: 결제 상태 필터
- Method: `GET`
- Path: `/api/admin/payments`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 18-7. 전체 환불 현황 조회 `[V2]`

## 1. INFO

- 설명: 실패 환불 확인
- Method: `GET`
- Path: `/api/admin/refunds`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | N | status 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 18-8. 전체 노쇼 현황 조회 `[V2]`

## 1. INFO

- 설명: 사용자·식당 조건
- Method: `GET`
- Path: `/api/admin/no-shows`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `memberId` | String | N | memberId 조건 |
| `restaurantId` | String | N | restaurantId 조건 |
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 18-9. 전체 운영 지표 조회 `[V2]`

## 1. INFO

- 설명: 예약 성사율·노쇼율
- Method: `GET`
- Path: `/api/admin/statistics/overview`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "totalReservationCount": 1000,
    "reservationConfirmationRate": 78.0,
    "noShowRate": 3.5
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 18-10. 식당별 예약 성사율 조회 `[V2]`

## 1. INFO

- 설명: 기간 조건
- Method: `GET`
- Path: `/api/admin/statistics/restaurants`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | LocalDate | N | startDate 조건 |
| `endDate` | LocalDate | N | endDate 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 18-11. 사용자별 노쇼율 조회 `[V2]`

## 1. INFO

- 설명: 누적 노쇼율
- Method: `GET`
- Path: `/api/admin/statistics/members/no-show-rates`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `page` | Integer | N | page 조건 |
| `size` | Integer | N | size 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "result": true
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

# 19. 예약 참여자 채팅 API

## 19-1. 예약 채팅방 조회 `[V2]`

## 1. INFO

- 설명: 예약별 채팅방 1개를 조회한다. 최초 예약자와 결제 완료 참여자만 접근할 수 있다.
- Method: `GET`
- Path: `/api/reservations/{reservationId}/chat-room`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "chatRoomId": 1,
    "reservationId": 101
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 해당 예약의 결제 완료 참여자가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 19-2. 채팅 메시지 목록 조회 `[V2]`

## 1. INFO

- 설명: 예약 참여자 전용 채팅 메시지를 페이징 조회한다.
- Method: `GET`
- Path: `/api/chat-rooms/{chatRoomId}/messages`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `chatRoomId` | Long | Y | chatRoomId 식별자 |

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `cursor` | Long | N | 마지막 메시지 ID |
| `size` | Integer | N | 조회 개수 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "nextCursor": null
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 해당 예약의 결제 완료 참여자가 아님 |
| `404` | `CHAT_ROOM_ID_NOT_FOUND` | chatRoomId에 해당하는 대상을 찾을 수 없음 |

---

# 20. V2 내부 구현 정책

## 공통

### API 요청 로그

- 담당자: 김홍기
- 구현 정책: 경로·Method·응답 상태·처리 시간

### 비즈니스 이벤트 로그

- 담당자: 김홍기
- 구현 정책: 예약·결제·환불·노쇼 결과

## 식당 검색

### 검색 요청 로그

- 담당자: 김홍기
- 구현 정책: 검색 조건·결과 건수

### 검색 결과 없음 로그

- 담당자: 김홍기
- 구현 정책: 결과 0건 비율 확인

## 모집 예약 검색

### 모집 예약 검색 로그

- 담당자: 김홍기
- 구현 정책: 처리 시간·결과 건수

## 모집 관리

### 식사 시작 2시간 전 모집 마감

- 담당자: 배지현
- 구현 정책: 스케줄러 처리

### 모집 실패 예약 자동 취소

- 담당자: 배지현
- 구현 정책: 확정 기준 미달

### 모집 실패 참여자 환불 요청

- 담당자: 김현승
- 구현 정책: 전액 환불

## 예약 취소

### 취소 후 예약 상태 재계산

- 담당자: 배지현
- 구현 정책: CONFIRMED·RECRUITING

### 예약 전체 취소 시 회차 복구

- 담당자: 배지현
- 구현 정책: 회차 예약 가능 처리

## 결제

### PortOne 결제와 좌석 임시 선점

- 담당자: 김현승
- 구현 정책: 결제 준비 시 좌석을 10분 임시 선점하고 `PaymentStatus.READY`를 생성한다. 결제 성공 시 `PAID`, 실패·만료 시 `FAILED`와 좌석 해제를 반영한다.

### 결제 완료 검증·웹훅 멱등성

- 담당자: 김현승
- 구현 정책: 완료 검증 API와 PortOne 웹훅이 동시에 실행돼도 예약·참여와 결제 상태를 한 번만 반영한다.

## 환불

### 사용자 취소 환불 처리

- 담당자: 김현승
- 구현 정책: 취소 시점에 따라 판단

### 모집 실패 전액 환불

- 담당자: 김현승
- 구현 정책: 참여자 전원

### 식당 귀책 전액 환불

- 담당자: 김현승
- 구현 정책: 사용자 귀책 없음

### 귀책 없는 참여자 환불

- 담당자: 김현승
- 구현 정책: 다른 사용자 취소로 진행 불가

### 환불 완료 후 결제 상태

- 담당자: 김현승
- 구현 정책: `RefundStatus.COMPLETED`가 되면 해당 결제를 `PaymentStatus.CANCELLED`로 변경한다. 사용자 한 명의 `partySize` 결제 전체만 환불한다.

## 채팅

### 예약 참여자 전용 채팅

- 담당자: 김현승
- 구현 정책: 예약별 채팅방 1개, 최초 예약자와 결제 완료 참여자만 접근, 사장님·관리자 제외

## 로그·모니터링

### 요청 ID 기록

- 담당자: 김홍기
- 구현 정책: MDC 사용

### 로그인 사용자 ID 기록

- 담당자: 김홍기
- 구현 정책: 인증 정보 연계

### API 경로·Method 기록

- 담당자: 김홍기
- 구현 정책: Interceptor 적용

### HTTP 응답 상태 기록

- 담당자: 김홍기
- 구현 정책: 성공·실패 구분

### API 처리 시간 기록

- 담당자: 김홍기
- 구현 정책: 성능 확인

### 예외·오류 로그

- 담당자: 김홍기
- 구현 정책: 에러 코드 포함

### 민감정보 로그 제외

- 담당자: 김홍기
- 구현 정책: 비밀번호·토큰·결제키

### 예약 처리 결과 로그

- 담당자: 김홍기
- 구현 정책: 생성·참여·확정·취소

### 결제·환불 처리 결과 로그

- 담당자: 김홍기
- 구현 정책: 성공·실패

### 노쇼 처리 결과 로그

- 담당자: 김홍기
- 구현 정책: 처리·해제

## 배포·인프라

### Redis 실행 환경 구성

- 담당자: 김홍기
- 구현 정책: 애플리케이션 연결

### Kafka 실행 환경 구성

- 담당자: 김홍기
- 구현 정책: 애플리케이션 연결

### 개발·운영 설정 분리

- 담당자: 김홍기
- 구현 정책: Spring Profile

### Docker 이미지 버전 관리

- 담당자: 김홍기
- 구현 정책: 커밋 기반 태그

### 배포 전후 헬스 체크

- 담당자: 김홍기
- 구현 정책: 실패 시 기존 서버 유지

### 이전 이미지 롤백

- 담당자: 김홍기
- 구현 정책: 배포 실패 대응


# 21. V3 내부 구현·고도화 정책

## 회원·인증

### 로그인 API 부하 테스트

- 담당자: 정용태
- 구현 정책: K6로 RPS·P95 측정

## 식당 관리

### 식당 조회 성능 개선

- 담당자: 정용태
- 구현 정책: 인덱스 적용 전후 비교

## 식당 검색

### 검색 API 메트릭

- 담당자: 김홍기
- 구현 정책: 응답 시간·오류율

### 검색 API 부하 테스트

- 담당자: 김홍기
- 구현 정책: K6 성능 측정

### 검색 인덱스 개선

- 담당자: 김홍기
- 구현 정책: EXPLAIN 분석

## 예약 생성

### 예약 생성 이벤트 발행

- 담당자: 배지현
- 구현 정책: Kafka 이벤트

## 예약 참여

### 예약 참여 이벤트 발행

- 담당자: 배지현
- 구현 정책: Kafka 이벤트

### 예약 동시성 테스트

- 담당자: 배지현
- 구현 정책: 마지막 자리 동시 요청

## 모집 예약 검색

### 모집 검색 성능 모니터링

- 담당자: 김홍기
- 구현 정책: 응답 시간·오류율

## 모집 관리

### 모집 마감 이벤트 발행

- 담당자: 배지현
- 구현 정책: Kafka 이벤트

## 예약 취소

### 예약 취소 이벤트 발행

- 담당자: 배지현
- 구현 정책: Kafka 이벤트

## 결제

### 결제 완료 이벤트 발행

- 담당자: 김현승
- 구현 정책: Kafka 이벤트

## 환불

### 환불 요청 이벤트 처리

- 담당자: 김현승
- 구현 정책: Kafka 비동기 처리

## 노쇼

### 노쇼 처리 이벤트 발행

- 담당자: 정용태
- 구현 정책: 운영 지표 연동

## 정산

### 정산 집계 이벤트 처리

- 담당자: 김현승
- 구현 정책: Kafka 이벤트 기반

## 로그·모니터링

### 전체 API 요청 수 모니터링

- 담당자: 김홍기
- 구현 정책: Micrometer

### API 응답 시간 모니터링

- 담당자: 김홍기
- 구현 정책: 평균·P95·P99

### Grafana 대시보드 구성

- 담당자: 김홍기
- 구현 정책: 주요 지표 시각화

### DB 커넥션 풀 모니터링

- 담당자: 김홍기
- 구현 정책: HikariCP

### JVM 상태 모니터링

- 담당자: 김홍기
- 구현 정책: Heap·CPU·메모리

### API 오류율 모니터링

- 담당자: 김홍기
- 구현 정책: HTTP 상태별

## 배포·인프라

### 도메인 연결

- 담당자: 김홍기
- 구현 정책: Route 53

### Grafana 인프라 구성

- 담당자: 김홍기
- 구현 정책: 대시보드 연결

### Prometheus 인프라 구성

- 담당자: 김홍기
- 구현 정책: 메트릭 수집

### Blue-Green 배포

- 담당자: 김홍기
- 구현 정책: 정상 확인 후 전환

### ALB 연결

- 담당자: 김홍기
- 구현 정책: 헬스 체크·트래픽 전달

### HTTPS 적용

- 담당자: 김홍기
- 구현 정책: ACM 인증서

# 22. API 목록 요약

- 실제 HTTP API 수: **87개**
- API가 아닌 기능은 V2·V3 내부 구현 정책으로 별도 정리했다.

| 기능 분류 | 버전 | 기능명 | Method | Path | 담당자 |
|---|---|---|---|---|---|
| 공통 | V3 | API 모니터링 | `GET` | `/actuator/prometheus` | 김홍기 |
| 공통 | V3 | 애플리케이션 상태 확인 | `GET` | `/actuator/health` | 김홍기 |
| 회원·인증 | V1 | 일반 사용자 회원가입 | `POST` | `/api/auth/signup/users` | 정용태 |
| 회원·인증 | V1 | 사장님 회원가입 | `POST` | `/api/auth/signup/owners` | 정용태 |
| 회원·인증 | V1 | 로그인 | `POST` | `/api/auth/login` | 정용태 |
| 회원·인증 | V1 | 내 정보 조회 | `GET` | `/api/members/me` | 정용태 |
| 회원·인증 | V2 | 내 정보 수정 | `PATCH` | `/api/members/me` | 정용태 |
| 회원·인증 | V2 | 로그아웃 | `POST` | `/api/auth/logout` | 정용태 |
| 회원·인증 | V2 | 토큰 재발급 | `POST` | `/api/auth/reissue` | 정용태 |
| 회원·인증 | V2 | 회원 탈퇴 | `DELETE` | `/api/members/me` | 정용태 |
| 식당 관리 | V1 | 식당 등록 | `POST` | `/api/owner/restaurants` | 정용태 |
| 식당 관리 | V1 | 내 식당 목록 조회 | `GET` | `/api/owner/restaurants` | 정용태 |
| 식당 관리 | V1 | 내 식당 상세 조회 | `GET` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 식당 정보 수정 | `PATCH` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 사용자용 식당 목록 조회 | `GET` | `/api/restaurants` | 정용태 |
| 식당 관리 | V1 | 사용자용 식당 상세 조회 | `GET` | `/api/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V2 | 식당 운영 상태 변경 | `PATCH` | `/api/owner/restaurants/{restaurantId}/status` | 정용태 |
| 식당 관리 | V2 | 식당 삭제 | `DELETE` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 검색 | V1 | 식당 동적 검색 | `GET` | `/api/restaurants/search` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 등록 | `POST` | `/api/owner/restaurants/{restaurantId}/tables` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/tables` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 상세 조회 | `GET` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 수정 | `PATCH` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 테이블 | V2 | 합석 테이블 상태 변경 | `PATCH` | `/api/owner/tables/{tableId}/status` | 김홍기 |
| 합석 테이블 | V2 | 합석 테이블 삭제 | `DELETE` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 회차 | V1 | 합석 회차 등록 | `POST` | `/api/owner/tables/{tableId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 사장님용 회차 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 사장님용 회차 상세 조회 | `GET` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V1 | 사용자용 예약 가능 회차 조회 | `GET` | `/api/restaurants/{restaurantId}/dining-sessions` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 수정 | `PATCH` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 삭제 | `DELETE` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 일괄 등록 | `POST` | `/api/owner/tables/{tableId}/dining-sessions/batch` | 김홍기 |
| 합석 회차 | V2 | 합석 회차 상태 변경 | `PATCH` | `/api/owner/dining-sessions/{sessionId}/status` | 김홍기 |
| 예약 생성 | V1 | 최초 예약 가능 여부 확인 | `GET` | `/api/dining-sessions/{sessionId}/reservation-availability` | 배지현 |
| 예약 생성 | V1 | 최초 예약 결제 준비 | `POST` | `/api/dining-sessions/{sessionId}/reservations/prepare` | 배지현 |
| 예약 참여 | V1 | 추가 참여 가능 여부 확인 | `GET` | `/api/reservations/{reservationId}/join-availability` | 배지현 |
| 예약 참여 | V1 | 예약 추가 참여 결제 준비 | `POST` | `/api/reservations/{reservationId}/participations/prepare` | 배지현 |
| 모집 예약 검색 | V1 | 참여 가능한 예약 검색 | `GET` | `/api/reservations/search` | 김홍기 |
| 예약 조회 | V1 | 예약 상세 조회 | `GET` | `/api/reservations/{reservationId}` | 배지현 |
| 예약 조회 | V1 | 내 예약 목록 조회 | `GET` | `/api/members/me/reservations` | 배지현 |
| 예약 조회 | V1 | 내 예약 상세 조회 | `GET` | `/api/members/me/reservations/{reservationId}` | 배지현 |
| 예약 조회 | V1 | 내 참여 정보 조회 | `GET` | `/api/reservations/{reservationId}/participations/me` | 배지현 |
| 예약 조회 | V1 | 예약 참여자 목록 조회 | `GET` | `/api/reservations/{reservationId}/participations` | 배지현 |
| 예약 조회 | V2 | 예약 상태 변경 이력 조회 | `GET` | `/api/reservations/{reservationId}/histories` | 배지현 |
| 모집 관리 | V1 | 추가 모집 수동 마감 | `POST` | `/api/reservations/{reservationId}/recruitment/close` | 배지현 |
| 모집 관리 | V1 | 모집 상태 조회 | `GET` | `/api/reservations/{reservationId}/recruitment` | 배지현 |
| 예약 취소 | V2 | 취소 예상 결과 조회 | `GET` | `/api/reservations/{reservationId}/participations/me/cancellation-preview` | 배지현 |
| 예약 취소 | V2 | 내 예약 참여 취소 | `POST` | `/api/reservations/{reservationId}/participations/me/cancel` | 배지현 |
| 사장님 예약 관리 | V1 | 식당별 예약 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/reservations` | 배지현 |
| 사장님 예약 관리 | V1 | 사장님용 예약 상세 조회 | `GET` | `/api/owner/reservations/{reservationId}` | 배지현 |
| 사장님 예약 관리 | V1 | 예약 참여자 목록 조회 | `GET` | `/api/owner/reservations/{reservationId}/participations` | 배지현 |
| 사장님 예약 관리 | V2 | 식당 귀책 예약 취소 | `POST` | `/api/owner/reservations/{reservationId}/cancel` | 배지현 |
| 사장님 예약 관리 | V2 | 시간대별 예약 현황 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/reservation-calendar` | 배지현 |
| 결제 | V1 | 결제 완료 검증 | `POST` | `/api/payments/{paymentId}/complete` | 김현승 |
| 결제 | V1 | PortOne 결제 웹훅 | `POST` | `/api/webhooks/portone` | 김현승 |
| 결제 | V1 | 내 결제 목록 조회 | `GET` | `/api/members/me/payments` | 김현승 |
| 결제 | V1 | 결제 상세 조회 | `GET` | `/api/payments/{paymentId}` | 김현승 |
| 결제 | V2 | 결제 상태 조회 | `GET` | `/api/payments/{paymentId}/status` | 김현승 |
| 결제 | V3 | 결제 실패 재검증 | `POST` | `/api/admin/payments/{paymentId}/retry` | 김현승 |
| 환불 | V1 | 내 환불 목록 조회 | `GET` | `/api/members/me/refunds` | 김현승 |
| 환불 | V1 | 환불 상세 조회 | `GET` | `/api/refunds/{refundId}` | 김현승 |
| 환불 | V2 | 환불 상태 조회 | `GET` | `/api/refunds/{refundId}/status` | 김현승 |
| 환불 | V3 | 실패 환불 목록 조회 | `GET` | `/api/admin/refunds/failed` | 김현승 |
| 환불 | V3 | 실패 환불 재처리 | `POST` | `/api/admin/refunds/{refundId}/retry` | 김현승 |
| 노쇼 | V2 | 노쇼 처리 대상 참여자 조회 | `GET` | `/api/owner/reservations/{reservationId}/participations/no-show-candidates` | 정용태 |
| 노쇼 | V2 | 참여자 노쇼 처리 | `POST` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | 정용태 |
| 노쇼 | V2 | 노쇼 처리 해제 | `DELETE` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | 정용태 |
| 노쇼 | V2 | 예약별 노쇼 이력 조회 | `GET` | `/api/owner/reservations/{reservationId}/no-show-histories` | 정용태 |
| 노쇼 | V2 | 식당 노쇼 현황 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/no-shows` | 정용태 |
| 정산 | V1 | 지급 예정 금액 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/expected` | 김현승 |
| 정산 | V1 | 예약별 지급 예정 내역 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/reservations` | 김현승 |
| 정산 | V1 | 예약별 지급 예정 상세 조회 | `GET` | `/api/owner/settlements/reservations/{reservationId}` | 김현승 |
| 정산 | V2 | 기간별 결제·환불 요약 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/settlements/summary` | 김현승 |
| 정산 | V3 | 정산 데이터 재집계 | `POST` | `/api/admin/settlements/recalculate` | 김현승 |
| 관리자 | V2 | 회원 목록 조회 | `GET` | `/api/admin/members` | 정용태 |
| 관리자 | V2 | 회원 상세 조회 | `GET` | `/api/admin/members/{memberId}` | 정용태 |
| 관리자 | V2 | 식당 목록 조회 | `GET` | `/api/admin/restaurants` | 정용태 |
| 관리자 | V2 | 식당 상세 조회 | `GET` | `/api/admin/restaurants/{restaurantId}` | 정용태 |
| 관리자 | V2 | 전체 예약 현황 조회 | `GET` | `/api/admin/reservations` | 정용태 |
| 관리자 | V2 | 전체 결제 현황 조회 | `GET` | `/api/admin/payments` | 정용태 |
| 관리자 | V2 | 전체 환불 현황 조회 | `GET` | `/api/admin/refunds` | 정용태 |
| 관리자 | V2 | 전체 노쇼 현황 조회 | `GET` | `/api/admin/no-shows` | 정용태 |
| 관리자 | V2 | 전체 운영 지표 조회 | `GET` | `/api/admin/statistics/overview` | 정용태 |
| 관리자 | V2 | 식당별 예약 성사율 조회 | `GET` | `/api/admin/statistics/restaurants` | 정용태 |
| 관리자 | V2 | 사용자별 노쇼율 조회 | `GET` | `/api/admin/statistics/members/no-show-rates` | 정용태 |
| 예약 참여자 채팅 | V2 | 예약 채팅방 조회 | `GET` | `/api/reservations/{reservationId}/chat-room` | 김현승 |
| 예약 참여자 채팅 | V2 | 채팅 메시지 목록 조회 | `GET` | `/api/chat-rooms/{chatRoomId}/messages` | 김현승 |

# 23. 구현 Issue에서 결정할 사항

아래 항목은 현재 확정 정책과 충돌하지 않으며, 실제 구현 시 세부 계약을 정한다.

- PortOne 공식 SDK·API 버전에 따른 웹훅 Payload와 서명 검증 코드
- 좌석 임시 선점 저장소와 만료 처리 방식
- 결제 완료 검증과 웹훅 동시 실행에 대한 락·트랜잭션·멱등성 구현
- 도메인별 타인 리소스 접근 거부 에러 코드 이름
- 채팅 STOMP 경로, 메시지 보관 기간과 종료 후 조회 정책
- 상세 DTO 필드와 ERD 컬럼

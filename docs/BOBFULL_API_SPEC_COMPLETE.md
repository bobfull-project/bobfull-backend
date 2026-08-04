# 밥풀 API Specification — 전체 반영본 

이 문서는 기능 목록에 기재된 **모든 실제 HTTP API**와 API가 아닌 **V2·V3 내부 구현 정책**을 반영한다.

> 상세 요청·응답 필드는 개발 과정에서 DTO와 ERD가 확정되면 조정할 수 있다.

---

# 0. 공통 규칙

## 0.1 Base URL

```text
/api
```

> 일반 서비스 API의 `Path`는 Base URL `/api`를 포함한 전체 경로다(예: `/api/auth/login`). Actuator는 `/actuator/**`, WebSocket 연결 Endpoint는 `/ws`를 사용하며 `/api` Base URL의 예외다.
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
PaymentStatus: READY, PAID, EXPIRED, FAILED, REFUNDED
RefundStatus: REQUESTED, PROCESSING, COMPLETED, FAILED
```

## 0.8 예약 인원·확정 정책

- `capacity`는 `2`, `4`, `6`, `8`만 허용한다.
- 예약 생성(`CREATE`)의 `partySize`는 `1 <= partySize <= table.capacity`여야 한다.
- 추가 참여(`JOIN`)의 `partySize`는 `1 <= partySize <= availableCapacity`여야 한다.
- `currentParticipantCount`는 `PAID` 상태의 유효 참여자 `partySize` 합계다.
- 임시 선점 인원은 `expiresAt > now`인 `READY` 결제의 `partySize` 합계다. 스케줄러의 `EXPIRED` 정규화 전에도 만료 좌석은 즉시 반환된다.
- `availableCapacity = capacity - currentParticipantCount - 임시 선점 인원`이다.
- 예약 확정 기준은 정원 `2`면 `2명`, `4`면 `3명`, `6`이면 `5명`, `8`이면 `7명`이다.
- 최초 결제 완료 시 예약은 `RECRUITING + OPEN`으로 시작한다. 확정 기준 도달 시 `CONFIRMED + OPEN`, 정원 도달 시 `CONFIRMED + CLOSED`가 된다.
- 식사 시작 2시간 전에 모집은 `CLOSED`가 된다. 이때 확정 기준 미달이면 예약은 `CANCELLED`가 되고 전액 환불한다.
- `CONFIRMED`는 모집 종료를 뜻하지 않는다.

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

- 설명: 이메일·전화번호 중복 검증, 이름 중복 허용
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
  "name": "홍길동",
  "phoneNumber": "01012345678"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | email 값 |
| `password` | String | Y | password 값 |
| `name` | String | Y | name 값 |
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
    "name": "홍길동",
    "role": "MEMBER"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `409` | `DUPLICATE_EMAIL` | 이미 사용 중인 email |
| `409` | `DUPLICATE_PHONE_NUMBER` | 이미 사용 중인 phoneNumber |

---

## 2-2. 사장님 회원가입 `[V1]`

## 1. INFO

- 설명: 이메일·전화번호·사업자등록번호 중복 검증 후 OWNER 권한 부여
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
  "name": "김사장",
  "phoneNumber": "01012345678",
  "businessNumber": "1234567890"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | email 값 |
| `password` | String | Y | password 값 |
| `name` | String | Y | name 값 |
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
    "name": "김사장",
    "role": "OWNER"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `409` | `DUPLICATE_EMAIL` | 이미 사용 중인 email |
| `409` | `DUPLICATE_PHONE_NUMBER` | 이미 사용 중인 phoneNumber |
| `409` | `DUPLICATE_BUSINESS_NUMBER` | 이미 사용 중인 businessNumber |

---

## 2-3. 로그인 `[V1]`

## 1. INFO

- 설명: Access Token과 Refresh Token을 함께 발급한다. Refresh Token은 Redis에만 저장하며(회원당 1건), 재발급마다 회전한다.
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
    "tokenType": "Bearer",
    "refreshToken": "refresh-token"
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
    "memberId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "phoneNumber": "01012345678",
    "role": "MEMBER"
  }
}
```

- OWNER 본인이 조회하는 경우 `businessNumber`를 추가로 반환한다. 일반 MEMBER 응답에는 `businessNumber`를 포함하지 않는다.

OWNER 응답 예시:

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "memberId": 2,
    "email": "owner@example.com",
    "name": "김사장",
    "phoneNumber": "01012345678",
    "role": "OWNER",
    "businessNumber": "1234567890"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-5. 내 정보 수정 `[V1]`

## 1. INFO

- 설명: 전화번호 중복 검증
- Method: `PATCH`
- Path: `/api/members/me`
- Auth: 필요
- 담당자: 정용태

## 2. Request

### Body

```json
{
  "name": "새이름",
  "phoneNumber": "01098765432"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | name 값 |
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
| `409` | `DUPLICATE_PHONE_NUMBER` | 이미 사용 중인 phoneNumber. 본인 기존 phoneNumber는 중복으로 보지 않음 |

---

## 2-6. 로그아웃 `[V2]`

## 1. INFO

- 설명: 인증된 회원의 Refresh Token을 Redis에서 즉시 삭제한다. Access Token은 별도로 무효화하지 않으며 자연 만료까지 유효하다.
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
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-7. 토큰 재발급 `[V2]`

## 1. INFO

- 설명: Refresh Token을 검증하고 회전(rotation)한다. 기존 Refresh Token은 즉시 삭제되고 새 Access·Refresh Token을 발급한다. Redis 조회 실패를 포함해 유효하지 않은 모든 경우를 401로 거부한다(fail-closed).
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
    "accessToken": "new-access-token",
    "refreshToken": "new-refresh-token"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 2-8. 회원 탈퇴 `[V1]`

## 1. INFO

- 설명: 소프트 딜리트 처리, 진행 중 예약이 있으면 탈퇴 제한
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
| `409` | `MEMBER_HAS_ACTIVE_RESERVATION` | 진행 중 예약이 있어 탈퇴할 수 없음 |

---

# 3. 식당 관리 API

## 3-1. 식당 등록 `[V1]`

## 1. INFO

- 설명: OWNER 권한
- 생성 시 서버가 식당 `status`를 `ACTIVE`로 적용한다. 상태 변경 API는 이번 범위에 없다.
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
  "keyword": "흑돼지,혼밥",
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
| `keyword` | String | Y | 사장님이 직접 입력하는 식당 키워드 |
| `depositPerPerson` | Integer | Y | depositPerPerson 값 |

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
    "content": [
      {
        "restaurantId": 1,
        "name": "밥풀식당",
        "address": "제주시 애월읍 1",
        "category": "한식",
        "depositPerPerson": 10000,
        "status": "ACTIVE"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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
    "restaurantId": 1,
    "name": "밥풀식당",
    "address": "제주시 애월읍 1",
    "category": "한식",
    "description": "합석 예약이 가능한 식당입니다.",
    "keyword": "흑돼지,혼밥",
    "depositPerPerson": 10000,
    "status": "ACTIVE"
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
  "keyword": "한식,혼밥",
  "depositPerPerson": 12000
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | name 값 |
| `description` | String | Y | description 값 |
| `keyword` | String | Y | 사장님이 직접 입력하는 식당 키워드 |
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

## 3-5. 사용자용 식당 목록·검색 `[V1]`

## 1. INFO

- 설명: 운영 중인 식당을 목록 조회하며 검색어·카테고리·날짜·시간 조건으로 예약 가능한 식당을 동적 검색한다. 제주는 초기 운영 타깃이며 제주 외 지역을 시스템적으로 차단하지 않는다. V1은 별도 지역 검색·필터를 제공하지 않는다.
- Method: `GET`
- Path: `/api/restaurants`
- Auth: 불필요
- 담당자: 정용태·김홍기

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | 식당명·메뉴 검색어 |
| `category` | String | N | 음식 카테고리 필터 |
| `date` | LocalDate | N | 예약 희망 날짜 |
| `time` | LocalTime | N | 예약 희망 시간 |
| `page` | Integer | N | 페이지 번호 |
| `size` | Integer | N | 페이지 크기 |
| `sort` | String | N | 정렬 기준 |

- 검색 조건이 없으면 운영 중인 식당 전체 목록을 조회한다.
- 날짜·시간 조건이 있으면 해당 조건에 예약 가능한 합석 회차가 존재하는 식당만 조회한다.
- 요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "restaurantId": 1,
        "name": "밥풀식당",
        "address": "제주시 애월읍 1",
        "category": "한식",
        "keyword": "흑돼지,혼밥",
        "depositPerPerson": 10000
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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
    "restaurantId": 1,
    "name": "밥풀식당",
    "address": "제주시 애월읍 1",
    "category": "한식",
    "description": "합석 예약이 가능한 식당입니다.",
    "keyword": "흑돼지,혼밥",
    "depositPerPerson": 10000
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 3-7. 식당 삭제 `[V1]`

## 1. INFO

- 설명: 소프트 딜리트 처리
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
| `409` | `RESTAURANT_DELETE_NOT_ALLOWED` | 연결된 테이블·회차·예약이 있어 삭제할 수 없음 |

---

# 4. 합석 테이블 API

## 4-1. 합석 테이블 등록 `[V1]`

## 1. INFO

- 설명: 정원 2·4·6·8명
- 생성 시 서버가 합석 테이블 `status`를 `ACTIVE`로 적용한다. 상태 변경 API는 이번 범위에 없다.
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
  "capacity": 8
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `capacity` | Integer | Y | capacity 값(2·4·6·8 중 하나) |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1,
    "displayNumber": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_TABLE_CAPACITY` | capacity가 `2`, `4`, `6`, `8` 중 하나가 아님 |
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 4-1-1. 합석 테이블 일괄 등록 `[V1]`

## 1. INFO

- 설명: 동일 정원의 테이블을 한 번에 1~10개 등록한다. 표시 번호는 식당별 기존 최대 번호 다음부터 연속 발급한다.
- Method: `POST`
- Path: `/api/owner/restaurants/{restaurantId}/tables/bulk`
- Auth: `OWNER`

## 2. Request

```json
{
  "capacity": 4,
  "count": 3
}
```

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "createdTableCount": 3,
    "tables": [
      { "tableId": 1, "restaurantId": 1, "displayNumber": 1, "capacity": 4, "status": "ACTIVE" },
      { "tableId": 2, "restaurantId": 1, "displayNumber": 2, "capacity": 4, "status": "ACTIVE" },
      { "tableId": 3, "restaurantId": 1, "displayNumber": 3, "capacity": 4, "status": "ACTIVE" }
    ]
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_TABLE_CAPACITY` | capacity가 `2`, `4`, `6`, `8` 중 하나가 아님 |
| `400` | `INVALID_INPUT_VALUE` | count가 1~10 범위를 벗어나거나 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 본인 식당이 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 4-2. 합석 테이블 목록 조회 `[V1]`

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
    "content": [
      {
        "tableId": 1,
        "restaurantId": 1,
        "displayNumber": 1,
        "capacity": 4,
        "status": "ACTIVE"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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

## 4-3. 합석 테이블 상세 조회 `[V1]`

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
    "tableId": 1,
    "restaurantId": 1,
    "displayNumber": 1,
    "capacity": 4,
    "status": "ACTIVE"
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

## 4-4. 합석 테이블 수정 `[V1]`

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
  "capacity": 4
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `capacity` | Integer | Y | capacity 값(2·4·6·8 중 하나) |

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
| `400` | `INVALID_TABLE_CAPACITY` | capacity가 `2`, `4`, `6`, `8` 중 하나가 아님 |
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---

## 4-5. 합석 테이블 삭제 `[V1]`

## 1. INFO

- 설명: 소프트 딜리트 처리, 연결된 회차가 있으면 삭제 제한
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
| `409` | `TABLE_HAS_DINING_SESSION` | 연결된 회차가 있어 삭제할 수 없음 |

---

# 5. 합석 회차 API

## 5-1. 합석 회차 등록 `[V1]`

## 1. INFO

- 설명: 날짜·시작·종료 시간을 지정해 회차 1건을 등록한다.
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
  "startAt": "2026-07-25T18:00:00",
  "endAt": "2026-07-25T20:00:00"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startAt` | String | Y | startAt 값 |
| `endAt` | String | Y | endAt 값 |

## 3. Response

- Status: `201 Created`

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
| `404` | `TABLE_ID_NOT_FOUND` | tableId에 해당하는 대상을 찾을 수 없음 |

---

## 5-2. 기존 테이블 합석 회차 일괄 등록 `[V1]`

## 1. INFO

- 설명: 이미 등록된 합석 테이블(`tableId`)을 대상으로 여러 날짜의 시작·종료 시간 범위를 `intervalMinutes` 단위로 나누어 회차를 일괄 생성한다. 테이블 생성은 4-1 합석 테이블 등록 API에서 먼저 수행하고, 이 API는 기존 테이블의 예약 가능 시간 등록에만 사용한다.
- Method: `POST`
- Path: `/api/owner/tables/{tableId}/dining-sessions/bulk`
- Auth: `OWNER`
- 담당자: 김홍기

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `tableId` | Long | Y | 회차를 추가할 기존 합석 테이블 식별자 |

### Body

```json
{
  "dates": [
    "2026-07-25",
    "2026-07-26"
  ],
  "startTime": "18:00:00",
  "endTime": "20:00:00",
  "intervalMinutes": 30
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `dates` | Array&lt;LocalDate&gt; | Y | 회차를 생성할 날짜 목록 |
| `startTime` | LocalTime | Y | 각 날짜의 첫 회차 시작 시간 |
| `endTime` | LocalTime | Y | 각 날짜의 회차 생성 종료 기준 시간 |
| `intervalMinutes` | Integer | Y | 회차 길이와 다음 회차 시작 간격(분). `startTime`~`endTime` 범위를 나누어 떨어지게 해야 함 |

## 3. Response

- Status: `201 Created`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "tableId": 1,
    "createdSessionCount": 8
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
| `409` | `DUPLICATE_DINING_SESSION` | 동일 시간대 회차가 중복됨 |

---

## 5-3. 사장님용 회차 목록 조회 `[V1]`

## 1. INFO

- 설명: 날짜 필터
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

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "sessionId": 1,
        "tableId": 1,
        "capacity": 4,
        "startAt": "2026-07-25T18:00:00+09:00",
        "endAt": "2026-07-25T20:00:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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

## 5-4. 사용자용 예약 가능 회차 조회 `[V1]`

## 1. INFO

- 설명: 예약 가능한 회차만
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
| `date` | LocalDate | Y | date 조건 |
| `partySize` | Integer | N | partySize 조건 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "restaurantId": 1,
    "content": [
      {
        "sessionId": 1,
        "tableId": 1,
        "capacity": 4,
        "startAt": "2026-07-25T18:00:00+09:00",
        "endAt": "2026-07-25T20:00:00+09:00",
        "availableCapacity": 4,
        "reservationId": null,
        "currentParticipantCount": 0
      }
    ]
  }
}
```

- `reservationId`는 이 회차를 이미 점유한 활성(`RECRUITING`/`CONFIRMED`) Reservation이 없으면 `null`이다. `null`이면 새 예약 생성(`type=CREATE`), 값이 있으면 해당 예약 참여(`type=JOIN`)의 targetId로 사용한다.
- `currentParticipantCount`는 활성 Reservation의 결제 완료(`RESERVED`) 참여자 partySize 합계이며, 활성 Reservation이 없으면 `0`이다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `404` | `RESTAURANT_ID_NOT_FOUND` | restaurantId에 해당하는 대상을 찾을 수 없음 |

---

## 5-5. 합석 회차 수정 `[V1]`

## 1. INFO

- 설명: 예약 존재 시 수정 제한
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
  "startAt": "2026-07-25T18:30:00",
  "endAt": "2026-07-25T20:30:00"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startAt` | String | Y | startAt 값 |
| `endAt` | String | Y | endAt 값 |

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

## 5-6. 합석 회차 삭제 `[V1]`

## 1. INFO

- 설명: 소프트 딜리트 처리, 예약이 존재하면 삭제 제한
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
| `409` | `SESSION_HAS_RESERVATION` | 연결된 예약이 있어 삭제할 수 없음 |

---

# 6. 예약·참여 API

## 6-1. 예약 가능 여부 확인 `[V1]`

## 1. INFO

- 설명: 최초 예약 생성과 기존 예약 추가 참여 가능 여부를 하나의 API에서 확인한다.
- Method: `GET`
- Path: `/api/reservations/availability`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `type` | String | Y | `CREATE` 또는 `JOIN` |
| `targetId` | Long | Y | `CREATE`는 sessionId, `JOIN`은 reservationId |
| `partySize` | Integer | Y | `CREATE`는 `1 <= partySize <= table.capacity`, `JOIN`은 `1 <= partySize <= availableCapacity` |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "available": true,
    "availableCapacity": 4,
    "reason": null
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_PARTY_SIZE` | partySize가 1 이상이 아니거나 CREATE의 테이블 정원을 초과함 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESOURCE_NOT_FOUND` | 대상 회차 또는 예약을 찾을 수 없음 |
| `409` | `ACTIVE_RESERVATION_ALREADY_EXISTS` | CREATE 대상 TimeSlot에 `RECRUITING` 또는 `CONFIRMED` Reservation, 또는 만료되지 않은 CREATE READY Payment가 이미 존재함 |
| `409` | `INSUFFICIENT_REMAINING_CAPACITY` | JOIN의 partySize가 availableCapacity를 초과함 |
| `409` | `INVALID_STATE` | 현재 상태에서 예약 또는 참여가 불가능함 |

---

## 6-2. 예약 결제 준비 `[V1]`

## 1. INFO

- 설명: 최초 예약 생성과 기존 예약 추가 참여의 결제 준비를 하나의 API에서 처리한다. 좌석을 10분간 임시 선점하고 PortOne 결제용 `paymentId`를 발급한다.
- Method: `POST`
- Path: `/api/reservations/prepare`
- Auth: 필요
- 담당자: 배지현
- 공동 검토: 김현승(결제), 김홍기(회차·좌석), 정용태(인증)

## 2. Request

### Body

```json
{
  "type": "CREATE",
  "targetId": 10,
  "partySize": 3
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `type` | String | Y | `CREATE` 또는 `JOIN` |
| `targetId` | Long | Y | `CREATE`는 sessionId, `JOIN`은 reservationId |
| `partySize` | Integer | Y | `CREATE`는 `1 <= partySize <= table.capacity`, `JOIN`은 `1 <= partySize <= availableCapacity` |

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

- 결제 성공 전에는 예약 또는 참여자를 생성하지 않는다.
- 결제 실패 시 `FAILED`, 시간 만료 시 `EXPIRED`로 정규화하며 좌석은 `expiresAt` 기준으로 즉시 반환한다.
- `CREATE`는 대상 TimeSlot을 잠근 뒤 활성 Reservation과 만료되지 않은 CREATE READY Payment를 차례로 확인한다. 둘 다 없을 때만 CREATE READY를 생성하며, 유효한 CREATE READY는 TimeSlot당 최대 1건이다.
- 유효한 CREATE READY가 있으면 `409 ACTIVE_RESERVATION_ALREADY_EXISTS`를 반환한다. 만료 또는 `FAILED` 처리 후에는 새 CREATE 요청을 허용한다. `JOIN`은 기존 Reservation의 `availableCapacity`를 기준으로 별도 처리한다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_PARTY_SIZE` | partySize가 1 이상이 아니거나 CREATE의 테이블 정원을 초과함 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESOURCE_NOT_FOUND` | 대상 회차 또는 예약을 찾을 수 없음 |
| `409` | `ACTIVE_RESERVATION_ALREADY_EXISTS` | CREATE 대상 TimeSlot에 `RECRUITING` 또는 `CONFIRMED` Reservation, 또는 만료되지 않은 CREATE READY Payment가 이미 존재함 |
| `409` | `INSUFFICIENT_REMAINING_CAPACITY` | JOIN의 partySize가 availableCapacity를 초과함 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

## 6-3. 참여 가능한 예약 검색 `[V1]`

## 1. INFO

- 설명: QueryDSL 적용
- Method: `GET`
- Path: `/api/reservations/search`
- Auth: 불필요
- 담당자: 김홍기

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `date` | LocalDate | N | date 조건 |
| `time` | LocalTime | N | time 조건 |
| `capacity` | Integer | N | capacity 조건 |
| `minimumRemainingSeats` | Integer | N | minimumRemainingSeats 조건 |
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
    "content": [
      {
        "reservationId": 1,
        "restaurantId": 1,
        "restaurantName": "밥풀식당",
        "sessionId": 1,
        "tableId": 1,
        "capacity": 4,
        "startAt": "2026-07-25T18:00:00+09:00",
        "endAt": "2026-07-25T20:00:00+09:00",
        "reservationStatus": "RECRUITING",
        "recruitmentStatus": "OPEN",
        "currentParticipantCount": 2,
        "availableCapacity": 2,
        "confirmationThreshold": 3
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

- 별도 도메인 오류 없음

---

## 6-4. 예약 상세 조회 `[V1]`

## 1. INFO

- 설명: V1에서는 참여자 상세 목록 대신 예약 상태와 인원 집계만 조회한다.
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
    "restaurantId": 1,
    "restaurantName": "밥풀식당",
    "sessionId": 1,
    "tableId": 1,
    "capacity": 4,
    "startAt": "2026-07-25T18:00:00+09:00",
    "endAt": "2026-07-25T20:00:00+09:00",
    "reservationStatus": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentParticipantCount": 3,
    "availableCapacity": 1,
    "confirmationThreshold": 7
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 6-5. 내 예약 목록 조회 `[V1]`

## 1. INFO

- 설명: 최초·추가 참여 모두
- Method: `GET`
- Path: `/api/members/me/reservations`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationStatus` | String | N | ReservationStatus 조건(`RECRUITING`, `CONFIRMED`, `CANCELLED`, `CLOSED`) |
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
    "content": [
      {
        "reservationId": 1,
        "restaurantId": 1,
        "restaurantName": "밥풀식당",
        "sessionId": 1,
        "startAt": "2026-07-25T18:00:00+09:00",
        "endAt": "2026-07-25T20:00:00+09:00",
        "reservationStatus": "RECRUITING",
        "recruitmentStatus": "OPEN",
        "participationId": 10,
        "partySize": 2,
        "participationStatus": "RESERVED",
        "paymentStatus": "PAID"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 6-6. 내 예약 상세 조회 `[V1]`

## 1. INFO

- 설명: 본인 참여 정보 포함
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
    "restaurantId": 1,
    "restaurantName": "밥풀식당",
    "sessionId": 1,
    "startAt": "2026-07-25T18:00:00+09:00",
    "endAt": "2026-07-25T20:00:00+09:00",
    "reservationStatus": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "participationId": 10,
    "partySize": 2,
    "participationStatus": "RESERVED",
    "paymentId": "PAY-20260725-0001",
    "paymentStatus": "PAID"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 6-7. 내 참여 정보 조회 `[V1]`

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
    "participationId": 10,
    "partySize": 2,
    "participationStatus": "RESERVED",
    "isCreator": false
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

---

## 6-8. 예약 참여자 목록 조회 `[V2]`

## 1. INFO

- 설명: 해당 예약의 유효 참여자만 최소 참여자 목록을 조회한다. V1은 6-4의 집계 정보만 제공한다.
- Method: `GET`
- Path: `/api/reservations/{reservationId}/participations`
- Auth: 유효 참여자
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
    "content": [
      {
        "name": "밥풀러",
        "partySize": 2
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 해당 예약의 유효 참여자가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |

- `memberId`, 이메일, 전화번호, 결제·환불 정보, 노쇼 이력은 제공하지 않는다.

---

## 6-9. 모집 상태 변경 `[V1]`

## 1. INFO

- 설명: 최초 예약자만 `CONFIRMED + OPEN` 예약의 모집 상태를 `CLOSED`로 수동 마감한다. `RECRUITING` 예약은 수동 마감할 수 없고, 마감한 모집은 다시 `OPEN`으로 변경할 수 없다.
- Method: `PATCH`
- Path: `/api/reservations/{reservationId}/recruitment`
- Auth: 필요
- 담당자: 배지현

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{
  "status": "CLOSED"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | String | Y | 변경할 모집 상태(`CLOSED`만 허용) |

- 수동 마감 이후 추가 참여자가 허용 시간 내 취소하면 `currentParticipantCount`를 재계산한다. 확정 기준 이상이면 `CONFIRMED + CLOSED`를 유지한다. 기준 미달이면 Reservation 전체를 `CANCELLED`로 전환하고 남은 유효 참여자를 전액 환불하며, 모집을 다시 열지 않는다.
- 전체 취소 시 ChatRoom 신규 메시지 전송은 종료된다. 현재 시간이 식사 시작 2시간 전보다 이전이고 다른 활성 Reservation이나 OWNER·시스템 사용 제한이 없을 때만 TimeSlot을 새 예약에 사용할 수 있다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "recruitmentStatus": "CLOSED"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `403` | `ACCESS_DENIED` | 최초 예약자가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | 예약을 찾을 수 없음 |
| `409` | `RECRUITMENT_CLOSE_NOT_ALLOWED` | Reservation이 `CONFIRMED`가 아니거나 recruitmentStatus가 `OPEN`이 아님 |
| `409` | `RECRUITMENT_ALREADY_CLOSED` | recruitmentStatus가 이미 `CLOSED`임 |

---

## 6-10. 내 예약 참여 취소 `[V2]`

## 1. INFO

- 설명: 인증된 MEMBER가 본인의 `ReservationParticipant` 한 건에 신청한 `partySize` 전체를 취소한다. 부분 취소는 지원하지 않는다.
- Method: `POST`
- Path: `/api/reservations/{reservationId}/participations/me/cancel`
- Auth: 필요
- 담당자: 배지현

- 서버 시간 기준 식사 시작 2시간 전까지만 취소할 수 있다. 2시간 이내에는 `CANCELLATION_DEADLINE_PASSED`를 반환한다.
- 대상 참여자가 최초 예약자면 예약 전체를 `CANCELLED`로 변경하고, 모든 유효 참여자의 Payment 전체 금액에 대해 환불을 요청한 뒤 TimeSlot을 새 예약 가능 상태로 복구한다. 기존 채팅방은 신규 메시지 전송을 종료하며 기존 메시지 조회는 유지한다.
- 대상 참여자가 추가 참여자면 해당 `ReservationParticipant`만 `CANCELLED`로 변경하고 본인 Payment 전체 금액을 환불한다. `currentParticipantCount`와 `availableCapacity`를 다시 계산하며, 모집이 `OPEN`이고 확정 기준 미달이면 Reservation은 `RECRUITING`, 기준 이상이면 `CONFIRMED`를 유지한다.
- `NO_SHOW` 또는 이미 `CANCELLED`인 참여자는 MEMBER 취소 대상이 아니다. 현재 ParticipationStatus에는 `VISITED` 상태가 없다.
- 동일 Payment에는 Refund를 한 건만 생성한다. 기존 환불이 있으면 새 환불을 만들지 않고 `REFUND_ALREADY_REQUESTED`를 반환한다.
- 최초 예약자 취소로 예약 전체가 취소되는 경우 요청 사유는 각 유효 참여자의 `cancelReason`에 동일하게 기록한다. Reservation에는 별도 취소 사유 컬럼을 두지 않는다.

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{
  "reason": "개인 일정 변경"
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
    "participationId": 10,
    "participationStatus": "CANCELLED",
    "cancellationScope": "PARTICIPATION",
    "refundStatus": "REQUESTED"
  }
}
```

- `cancellationScope`는 추가 참여자 취소 시 `PARTICIPATION`, 최초 예약자 취소 시 `RESERVATION`이다.
- 최초 예약자 취소의 Response `refundStatus`는 최초 예약자 Payment의 환불 요청 상태다. 다른 유효 참여자 Payment의 환불도 같은 트랜잭션에서 요청한다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `CANCELLATION_NOT_ALLOWED` | 본인 참여가 아니거나 `NO_SHOW` 상태 등 MEMBER 취소가 허용되지 않음 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `404` | `PARTICIPATION_NOT_FOUND` | 본인 ReservationParticipant를 찾을 수 없음 |
| `409` | `CANCELLATION_DEADLINE_PASSED` | 서버 시간 기준 식사 시작 2시간 이내임 |
| `409` | `PARTICIPATION_ALREADY_CANCELLED` | 대상 참여자가 이미 `CANCELLED` 상태임 |
| `409` | `RESERVATION_ALREADY_CANCELLED` | 대상 Reservation이 이미 `CANCELLED` 상태임 |
| `409` | `REFUND_ALREADY_REQUESTED` | 대상 Payment에 대한 Refund가 이미 존재함 |

---

## 6-11. 식당별 예약 목록 조회 `[V1]`

## 1. INFO

- 설명: 날짜·상태 조건
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
| `reservationStatus` | String | N | ReservationStatus 조건(`RECRUITING`, `CONFIRMED`, `CANCELLED`, `CLOSED`) |
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
    "content": [
      {
        "reservationId": 1,
        "sessionId": 1,
        "tableId": 1,
        "capacity": 4,
        "startAt": "2026-07-25T18:00:00+09:00",
        "endAt": "2026-07-25T20:00:00+09:00",
        "reservationStatus": "RECRUITING",
        "recruitmentStatus": "OPEN",
        "currentParticipantCount": 2,
        "availableCapacity": 2,
        "confirmationThreshold": 3
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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

## 6-12. 사장님용 예약 상세 조회 `[V1]`

## 1. INFO

- 설명: 본인 식당 예약만
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
    "restaurantId": 1,
    "sessionId": 1,
    "tableId": 1,
    "capacity": 4,
    "startAt": "2026-07-25T18:00:00+09:00",
    "endAt": "2026-07-25T20:00:00+09:00",
    "reservationStatus": "RECRUITING",
    "recruitmentStatus": "OPEN",
    "currentParticipantCount": 2,
    "availableCapacity": 2,
    "confirmationThreshold": 3
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

## 6-13. 사장님용 예약 참여자 목록 조회 `[V1]`

## 1. INFO

- 설명: 신청 인원·참여 상태
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
    "content": [
      {
        "participationId": 10,
        "memberId": 15,
        "name": "홍길동",
        "partySize": 2,
        "participationStatus": "RESERVED"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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

## 6-14. 식당 귀책 예약 취소 `[V2]`

## 1. INFO

- 설명: OWNER가 본인 식당 사유로 예약 전체를 취소하고 유효 참여자 전액 환불을 요청한다. MEMBER 취소 API와 권한·처리 범위가 다르다.
- Method: `POST`
- Path: `/api/owner/reservations/{reservationId}/cancel`
- Auth: `OWNER`
- 담당자: 배지현

- 예약은 `CANCELLED`가 되며 참여자를 `NO_SHOW`로 처리하지 않는다. TimeSlot은 예약 시작 전이며 다른 제약이 없는 경우만 새 예약 가능 상태로 복구한다.
- 전체 취소 사유는 각 유효 참여자의 `cancelReason`에 동일하게 기록한다. Reservation에는 별도 취소 사유 컬럼을 두지 않는다.

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `reservationId` | Long | Y | reservationId 식별자 |

### Body

```json
{
  "reason": "식당 내부 사정"
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
    "reservationId": 1
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

# 7. 결제 API

## 7-1. 결제 완료 검증 `[V1]`

## 1. INFO

- 설명: 인증된 결제 당사자가 PortOne 결제 결과를 서버에서 검증하고 결제 완료를 확정한다. `Payment.memberId`와 인증 사용자 ID가 일치해야 한다.
- Method: `POST`
- Path: `/api/payments/{paymentId}/complete`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | 결제 식별자 |

## 3. Response

- Status: `200 OK`
- 이미 완료된 Payment를 다시 요청하면 기존 완료 결과를 담아 `200 OK`로 멱등 응답한다.

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001",
    "paymentStatus": "PAID",
    "reservationId": 1,
    "participationId": 10
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `PAYMENT_ACCESS_DENIED` | Payment.memberId와 인증 사용자 ID가 다름 |
| `404` | `PAYMENT_NOT_FOUND` | paymentId에 해당하는 대상을 찾을 수 없음 |
| `409` | `PAYMENT_VERIFICATION_FAILED` | 결제 검증 실패 |
| `409` | `PAYMENT_EXPIRED` | 결제 가능 시간이 만료됨 |

---

## 7-2. PortOne 결제 웹훅 `[V1]`

## 1. INFO

- 설명: PortOne 웹훅 서명을 검증한 뒤 `paymentId`로 결제 정보를 재조회한다. 완료 검증 API와 동시에 호출되어도 한 번만 반영한다.
- Method: `POST`
- Path: `/api/webhooks/portone`
- Auth: PortOne 웹훅 서명 검증
- 담당자: 김현승

## 2. Request

- `webhook-id`, `webhook-signature`, `webhook-timestamp` 헤더는 필수다.
- PortOne API·Store·Webhook 시크릿과 `payment.expiration` 실행값은 공통 `application.yml`이 아닌 `application-local.yml` 또는 배포 환경변수로 주입한다. Webhook 시크릿에 기본값을 두지 않으며, 웹훅이 활성화된 환경에서 필수 값이 없으면 애플리케이션 시작이 실패한다.
- Controller는 JSON DTO 역직렬화 전에 원본 Body(`String` 또는 `byte[]`)와 위 헤더를 PortOne 공식 SDK 검증기에 전달한다. 검증 성공 후에만 이벤트를 해석한다.
- SecurityConfig는 이 POST 경로를 `permitAll`로 두고 JWT 필터도 이 경로를 인증 실패(`401`)로 차단하지 않는다. 접근 허용은 서명 검증을 대체하지 않는다.
- `Transaction.Paid`만 `paymentId`를 추출해 공통 결과 반영으로 전달한다. 지원하지 않는 이벤트는 성공으로 무시한다.

## 3. Response

- 서명 헤더 누락·서명 검증 실패는 `400 Bad Request`다.
- 지원하지 않는 이벤트, 이미 `PAID`, Payment 미존재, 금액·통화·외부 paymentId 불일치, 내부 `EXPIRED` 또는 만료된 `READY`는 `200 OK`로 확인 처리한다.
- 지원하지 않는 이벤트와 알려진 영구 업무 실패의 확인 처리는 `200 OK`와 빈 본문으로 반환한다.
- 서명 헤더 누락·서명 검증 실패는 `400 Bad Request`와 빈 본문으로 반환한다.
- PortOne 네트워크 오류, DB 장애, 분류되지 않은 업무 오류, 예상하지 못한 서버 오류는 공통 예외 응답 구조의 `5xx`로 반환한다. 알려진 영구 업무 실패만 웹훅 입구에서 `200`으로 변환하며 예외를 무조건 삼키지 않는다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 필수 서명 헤더 누락 또는 서명 검증 실패 |
| `200` | - | 미지원 이벤트·이미 PAID·Payment 미존재·검증 불일치·`PAYMENT_EXPIRED`는 확인 처리 |
| `5xx` | `INTERNAL_SERVER_ERROR` | PortOne 네트워크·DB·예상하지 못한 서버 오류 |

---

## 7-3. 내 결제 목록 조회 `[V1]`

## 1. INFO

- 설명: 페이징 적용
- Method: `GET`
- Path: `/api/members/me/payments`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentStatus` | String | N | PaymentStatus 조건(`READY`, `PAID`, `FAILED`, `REFUNDED`) |
| `page` | Integer | N | 페이지 번호 |
| `size` | Integer | N | 페이지 크기 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "paymentId": "PAY-20260725-0001",
        "reservationId": 1,
        "participationId": 10,
        "paymentPurpose": "CREATE",
        "partySize": 2,
        "amount": 30000,
        "currency": "KRW",
        "paymentStatus": "PAID",
        "paidAt": "2026-07-25T17:30:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 7-4. 결제 상세 조회 `[V1]`

## 1. INFO

- 설명: 상태를 포함한 결제 당사자만 조회
- Method: `GET`
- Path: `/api/payments/{paymentId}`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | PortOne 결제 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001",
    "reservationId": 1,
    "participationId": 10,
    "paymentPurpose": "CREATE",
    "partySize": 2,
    "paymentStatus": "PAID",
    "amount": 30000,
    "currency": "KRW",
    "expiresAt": "2026-07-25T17:40:00+09:00",
    "paidAt": "2026-07-25T17:30:00+09:00"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `PAYMENT_NOT_FOUND` | paymentId에 해당하는 대상을 찾을 수 없음 |

---

## 7-5. 결제 실패 재검증 `[V3]`

## 1. INFO

- 설명: 운영자가 PortOne 결제 상태를 다시 조회하되 예약·참여 반영은 멱등하게 처리한다.
- Method: `POST`
- Path: `/api/admin/payments/{paymentId}/retry`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

### Path Variables

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `paymentId` | String | Y | PortOne 결제 식별자 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": "PAY-20260725-0001"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT_VALUE` | 요청값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |
| `404` | `PAYMENT_NOT_FOUND` | paymentId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

# 8. 환불 API

## 8-1. 내 환불 목록 조회 `[V1]`

## 1. INFO

- 설명: 환불 상태 포함
- Method: `GET`
- Path: `/api/members/me/refunds`
- Auth: 필요
- 담당자: 김현승

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refundStatus` | String | N | RefundStatus 조건(`REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED`) |
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
    "content": [
      {
        "refundId": 1,
        "paymentId": "PAY-20260725-0001",
        "reservationId": 1,
        "amount": 30000,
        "refundStatus": "COMPLETED",
        "requestedAt": "2026-07-25T19:00:00+09:00",
        "completedAt": "2026-07-25T19:05:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 8-2. 환불 상세 조회 `[V1]`

## 1. INFO

- 설명: 상태를 포함한 대상 사용자만 조회
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
    "refundId": 1,
    "paymentId": "PAY-20260725-0001",
    "reservationId": 1,
    "amount": 30000,
    "refundStatus": "COMPLETED",
    "requestedAt": "2026-07-25T19:00:00+09:00",
    "completedAt": "2026-07-25T19:05:00+09:00"
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `REFUND_ID_NOT_FOUND` | refundId에 해당하는 대상을 찾을 수 없음 |

---

## 8-3. 실패 환불 목록 조회 `[V3]`

## 1. INFO

- 설명: 재처리 대상
- Method: `GET`
- Path: `/api/admin/refunds/failed`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "refundId": 1,
        "paymentId": "PAY-20260725-0001",
        "memberId": 15,
        "reservationId": 1,
        "amount": 30000,
        "refundStatus": "FAILED",
        "requestedAt": "2026-07-25T19:00:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 8-4. 실패 환불 재처리 `[V3]`

## 1. INFO

- 설명: 중복 환불 방지
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
    "refundId": 1
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
| `409` | `INVALID_STATE` | 현재 상태에서 요청을 처리할 수 없음 |

---

# 9. 노쇼 API

## 9-1. 노쇼 처리 대상 참여자 조회 `[V2]`

## 1. INFO

- 설명: 식사 종료 후에만 조회 가능
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
    "content": [
      {
        "participationId": 501,
        "memberId": 15,
        "name": "김○○",
        "partySize": 2,
        "participationStatus": "RESERVED"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없거나 본인 리소스가 아님 |
| `404` | `RESERVATION_ID_NOT_FOUND` | reservationId에 해당하는 대상을 찾을 수 없음 |
| `409` | `INVALID_STATE` | 식사 종료 전이라 노쇼 처리 대상을 조회할 수 없음 |

---

## 9-2. 참여자 노쇼 처리 `[V2]`

## 1. INFO

- 설명: 신청 인원 전체 처리. 노쇼는 사유 없이 방문하지 않은 상태이므로 처리 사유를 저장하지 않는다.
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

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "reservationId": 1,
    "participationId": 1
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
| `409` | `INVALID_STATE` | 이미 처리된 참여 등 현재 상태에서 노쇼 처리를 할 수 없음 |

---

## 9-3. 노쇼 처리 해제 `[V2]`

## 1. INFO

- 설명: RESERVED로 복구
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
    "participationId": 1
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
| `409` | `INVALID_STATE` | NO_SHOW 상태가 아니어서 해제할 수 없음 |

---

## 9-4. 예약별 노쇼 이력 조회 `[V2]`

## 1. INFO

- 설명: 처리자·처리 시각
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
    "content": [
      {
        "noShowHistoryId": 1,
        "participationId": 501,
        "memberId": 15,
        "name": "김○○",
        "partySize": 2,
        "isMarked": true,
        "processedByMemberId": 3,
        "processedAt": "2026-07-25T21:00:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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

## 9-5. 식당 노쇼 고객 조회 `[V2]`

## 1. INFO

- 설명: 해당 식당에서 노쇼 처리된 고객 목록을 기간 조건과 함께 조회한다.
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
| `startDate` | LocalDate | N | 노쇼 기간 시작일 |
| `endDate` | LocalDate | N | 노쇼 기간 종료일 |
| `page` | Integer | N | 페이지 번호 |
| `size` | Integer | N | 페이지 크기 |

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "memberId": 15,
        "name": "김○○",
        "noShowCount": 2,
        "latestNoShowAt": "2026-07-25T18:00:00+09:00",
        "reservationId": 101,
        "participationId": 501,
        "partySize": 2
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

- 본인 식당에서 노쇼 처리된 고객만 조회한다.
- 이름은 마스킹하고 이메일·전화번호는 제공하지 않는다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 본인 식당이 아님 |
| `404` | `RESTAURANT_ID_NOT_FOUND` | 식당을 찾을 수 없음 |

---

# 10. 정산 API

## 10-1. 지급 예정 금액 조회 `[V1]`

## 1. INFO

- 설명: `paidAt`이 존재하는 결제 완료 이력 합계－`COMPLETED` 환불 금액 합계. 기간은 예약 회차(`diningSessionAt`)의 Asia/Seoul 날짜를 양 끝 포함으로 적용하며, 한쪽만 전달하면 열린 구간이다.
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

`startDate`가 `endDate`보다 늦으면 `400 INVALID_INPUT_VALUE`를 반환한다.

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "totalPaidAmount": 1500000,
    "totalRefundedAmount": 300000,
    "expectedSettlementAmount": 1200000
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

## 10-2. 예약별 지급 예정 내역 조회 `[V1]`

## 1. INFO

- 설명: 예약 회차(`diningSessionAt`)의 Asia/Seoul 날짜 기준 기간·페이징 적용. 시작일·종료일은 양 끝 포함이며, 한쪽만 전달하면 열린 구간이다.
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

`startDate`가 `endDate`보다 늦으면 `400 INVALID_INPUT_VALUE`를 반환한다.

요청 Body는 사용하지 않는다.

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "reservationId": 101,
        "diningSessionAt": "2026-07-25T18:00:00+09:00",
        "totalPaidAmount": 90000,
        "totalRefundedAmount": 0,
        "expectedSettlementAmount": 90000
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
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

## 10-3. 예약별 지급 예정 상세 조회 `[V1]`

## 1. INFO

- 설명: 결제·환불 내역 포함
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
    "expectedSettlementAmount": 90000,
    "payments": [
      {
        "paymentId": "PAY-20260725-0001",
        "paymentStatus": "PAID",
        "amount": 30000
      }
    ],
    "refunds": [
      {
        "refundId": 1,
        "refundStatus": "COMPLETED",
        "amount": 0
      }
    ]
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

## 10-4. 기간별 결제·환불 요약 조회 `[V2]`

## 1. INFO

- 설명: 시작일·종료일
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
    "restaurantId": 1,
    "startDate": "2026-07-01",
    "endDate": "2026-07-31",
    "totalPaidAmount": 500000,
    "totalRefundedAmount": 80000,
    "expectedSettlementAmount": 420000,
    "reservationCount": 20,
    "refundCount": 3
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

## 10-5. 정산 데이터 재집계 `[V3]`

## 1. INFO

- 설명: 운영 관리자 기능
- Method: `POST`
- Path: `/api/admin/settlements/recalculate`
- Auth: `ADMIN`
- 담당자: 김현승

## 2. Request

### Body

```json
{
  "startDate": "2026-07-01",
  "endDate": "2026-07-31"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `startDate` | String | Y | startDate 값 |
| `endDate` | String | Y | endDate 값 |

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

---

# 11. 관리자 API

## 11-1. 회원 목록 조회 `[V2]`

## 1. INFO

- 설명: 검색·페이징
- Method: `GET`
- Path: `/api/admin/members`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `role` | String | N | MemberRole 조건(`MEMBER`, `OWNER`, `ADMIN`) |
| `deleted` | Boolean | N | 탈퇴 여부 조건. `true`는 `deletedAt`이 있는 회원, `false`는 활성 회원 |
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
    "content": [
      {
        "memberId": 1,
        "email": "user@example.com",
        "name": "홍길동",
        "role": "MEMBER",
        "noShowCount": 1,
        "createdAt": "2026-07-21T10:00:00+09:00",
        "deletedAt": null
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-2. 회원 상세 조회 `[V2]`

## 1. INFO

- 설명: 노쇼 정보 포함
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
    "memberId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "phoneNumber": "01012345678",
    "role": "MEMBER",
    "noShowCount": 1,
    "createdAt": "2026-07-21T10:00:00+09:00",
    "deletedAt": null
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

## 11-3. 식당 목록 조회 `[V2]`

## 1. INFO

- 설명: 검색·페이징
- Method: `GET`
- Path: `/api/admin/restaurants`
- Auth: `ADMIN`
- 담당자: 정용태

## 2. Request

### Query Parameters

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `keyword` | String | N | keyword 조건 |
| `restaurantStatus` | String | N | RestaurantStatus 조건(현재 `ACTIVE`) |
| `deleted` | Boolean | N | 삭제 여부 조건. `true`는 `deletedAt`이 있는 식당, `false`는 활성 식당 |
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
    "content": [
      {
        "restaurantId": 1,
        "ownerMemberId": 3,
        "ownerName": "사장님",
        "name": "밥풀식당",
        "category": "한식",
        "status": "ACTIVE",
        "createdAt": "2026-07-21T10:00:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-4. 식당 상세 조회 `[V2]`

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
    "restaurantId": 1,
    "ownerMemberId": 3,
    "ownerName": "사장님",
    "name": "밥풀식당",
    "address": "제주시 애월읍 1",
    "category": "한식",
    "description": "합석 예약이 가능한 식당입니다.",
    "keyword": "흑돼지,혼밥",
    "depositPerPerson": 10000,
    "status": "ACTIVE",
    "createdAt": "2026-07-21T10:00:00+09:00",
    "deletedAt": null
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

## 11-5. 전체 예약 현황 조회 `[V2]`

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
| `reservationStatus` | String | N | ReservationStatus 조건(`RECRUITING`, `CONFIRMED`, `CANCELLED`, `CLOSED`) |
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
    "content": [
      {
        "reservationId": 1,
        "restaurantId": 1,
        "restaurantName": "밥풀식당",
        "creatorMemberId": 15,
        "startAt": "2026-07-25T18:00:00+09:00",
        "reservationStatus": "CONFIRMED",
        "recruitmentStatus": "OPEN",
        "currentParticipantCount": 3,
        "capacity": 4
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-6. 전체 결제 현황 조회 `[V2]`

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
| `paymentStatus` | String | N | PaymentStatus 조건(`READY`, `PAID`, `FAILED`, `REFUNDED`) |
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
    "content": [
      {
        "paymentId": "PAY-20260725-0001",
        "memberId": 15,
        "reservationId": 1,
        "amount": 30000,
        "currency": "KRW",
        "paymentStatus": "PAID",
        "paidAt": "2026-07-25T17:30:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-7. 전체 환불 현황 조회 `[V2]`

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
| `refundStatus` | String | N | RefundStatus 조건(`REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED`) |
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
    "content": [
      {
        "refundId": 1,
        "paymentId": "PAY-20260725-0001",
        "memberId": 15,
        "reservationId": 1,
        "amount": 30000,
        "refundStatus": "COMPLETED",
        "requestedAt": "2026-07-25T19:00:00+09:00",
        "completedAt": "2026-07-25T19:05:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-8. 전체 노쇼 현황 조회 `[V2]`

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
| `memberId` | Long | N | memberId 조건 |
| `restaurantId` | Long | N | restaurantId 조건 |
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
    "content": [
      {
        "noShowHistoryId": 1,
        "memberId": 15,
        "memberName": "김○○",
        "restaurantId": 1,
        "restaurantName": "밥풀식당",
        "reservationId": 101,
        "participationId": 501,
        "partySize": 2,
        "processedAt": "2026-07-25T21:00:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-9. 전체 운영 지표 조회 `[V2]`

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

## 11-10. 식당별 예약 성사율 조회 `[V2]`

## 1. INFO

- 설명: 기간 조건에 따라 식당별 예약 성사율 통계를 조회한다.
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
    "content": [
      {
        "restaurantId": 1,
        "restaurantName": "밥풀식당",
        "totalReservationCount": 120,
        "confirmedReservationCount": 90,
        "confirmationRate": 75.0
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

## 11-11. 사용자별 노쇼율 조회 `[V2]`

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
    "content": [
      {
        "memberId": 1,
        "name": "홍○동",
        "totalReservationCount": 10,
        "noShowCount": 2,
        "noShowRate": 20.0
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

- 이름은 마스킹하며 이메일·전화번호 등 신규 개인정보는 포함하지 않는다.

## 4. Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ACCESS_DENIED` | 접근 권한이 없음 |

---

# 12. 예약 참여자 채팅 API

## 12-1. 예약 채팅방 조회 `[V2]`

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

## 12-2. 채팅 메시지 목록 조회 `[V2]`

## 1. INFO

- 설명: 유효 참여자가 채팅방 단위로 저장된 과거 메시지를 커서 기반 조회한다.
- Method: `GET`
- Path: `/api/chat/rooms/{chatRoomId}/messages`
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
| `cursor` | Long | N | 이전 메시지 기준 커서 |
| `size` | Integer | N | 조회 개수 |

## 3. Response

- Status: `200 OK`

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "messageId": 1001,
        "senderMemberId": 15,
        "senderName": "밥풀러",
        "content": "곧 도착합니다.",
        "sentAt": "2026-07-25T17:50:00+09:00"
      }
    ],
    "nextCursor": 1001
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

- 최초 예약 결제 완료 시 예약당 채팅방 1개를 생성한다. 별도 채팅방 생성 API는 없다.
- 유효 참여자는 결제 완료 참여자 중 `CANCELLED`가 아닌 참여자다. 유효 참여자만 접근하며, `CANCELLED` 참여자는 즉시 접근이 종료된다. OWNER와 ADMIN은 참여하지 않는다.
- 예약이 `CANCELLED` 또는 `CLOSED`가 되면 신규 메시지 전송은 종료하지만 기존 메시지는 조회할 수 있다.
- 메시지는 DB에 저장한다.
- WebSocket 연결 Endpoint는 `/ws`다.
- STOMP 전송 경로는 `/pub/chat/rooms/{chatRoomId}/messages`, 구독 경로는 `/sub/chat/rooms/{chatRoomId}`다.
- 읽음 처리, 이미지·파일, 메시지 수정·삭제, 신고·차단, Redis Pub/Sub, Kafka는 범위에서 제외한다.

---

# 13. V2 내부 구현 정책

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
- 구현 정책: 추가 참여자 취소 후 `recruitmentStatus=OPEN`이면 현재 참여 인원이 확정 기준 미달일 때만 `RECRUITING`, 기준 이상일 때 `CONFIRMED`를 유지한다. `recruitmentStatus=CLOSED`인 `CONFIRMED` 예약은 기준 이상이면 `CONFIRMED + CLOSED`를 유지하고, 기준 미달이면 예약 전체를 `CANCELLED`로 전환해 유효 참여자를 전액 환불한다. CLOSED 모집은 다시 OPEN하지 않으며 취소 좌석을 재모집하지 않는다.

### 예약 전체 취소 시 회차 복구

- 담당자: 배지현
- 구현 정책: 최초 예약자 취소·모집 실패·수동 마감 이후 기준 미달 취소·OWNER 또는 시스템 귀책 취소는 기존 Reservation이 `CANCELLED`이고 현재 시간이 식사 시작 2시간 전보다 이전이며 다른 활성 Reservation 또는 OWNER·시스템 사용 제한이 없을 때만 회차를 새 예약 가능 상태로 복구한다.

## 결제

### PortOne 결제와 좌석 임시 선점

- 담당자: 김현승
- 구현 정책: 결제 준비 시 좌석을 10분 임시 선점하고 `PaymentStatus.READY`를 생성한다. 결제 성공 시 `PAID`, 검증 실패 시 `FAILED`, 시간 만료 시 `EXPIRED`로 구분한다. 좌석은 `expiresAt` 기반 계산으로 즉시 반환하며 스케줄러는 상태 정규화만 담당한다.

### 결제 완료 검증·웹훅 멱등성

- 담당자: 김현승
- 구현 정책: 완료 검증 API와 PortOne 웹훅은 PortOne 재조회·상태/금액/통화 검증 뒤 동일 Payment 행 비관적 락으로 수렴한다. PAID 전환과 `Reservation`·`ReservationParticipant` 생성 및 결과 ID 연결은 하나의 트랜잭션이며, `ReservationConfirmationService`는 `MANDATORY`로 참여한다.

### READY 만료 정리

- 구현 정책: `application-local.yml` 또는 배포 환경변수에서 `payment.expiration.enabled=true`, `fixed-delay=60s`, `batch-size=100`을 V1 기본값으로 사용한다(테스트 환경 비활성화).
- 후보는 `payment_status=READY AND expires_at <= cutoff ORDER BY expires_at ASC, payment_id ASC LIMIT 100`으로 내부 `payment_id`만 조회한다. 각 건은 별도 REQUIRED 트랜잭션에서 같은 내부 PK를 비관적 락으로 다시 조회해 `expireIfNeeded(now)`를 수행한다.
- 전체 batch를 하나의 트랜잭션으로 묶지 않으며, 외부 취소·환불·`ReservationConfirmationPort` 호출은 하지 않는다.

## 환불

### 사용자 취소 환불 처리

- 담당자: 김현승
- 구현 정책: 서버 시간 기준 식사 시작 2시간 전까지만 MEMBER 본인 참여 전체 취소를 허용하고 Payment 전체 금액을 환불한다. 부분 환불과 마감 이후 MEMBER 무환불 취소는 범위에 없다.

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
- 구현 정책: `RefundStatus.COMPLETED`가 되면 해당 결제를 `PaymentStatus.REFUNDED`로 변경한다. 사용자 한 명의 `partySize` 결제 전체만 환불한다. READY 결제의 명시적 사용자 취소는 현재 범위에 없으며, 필요해질 때 별도 CANCELLED 상태를 계약으로 도입한다.

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

## 소프트 딜리트 정책

### 회원 탈퇴

- 회원 데이터는 실제 삭제하지 않고 삭제 상태 또는 삭제 시각을 기록한다.
- 진행 중 예약이 있으면 탈퇴를 제한한다.
- 탈퇴 후에도 동일 `email`, `phoneNumber`, `businessNumber` 재사용은 허용하지 않는다. 고유 식별자 값은 변경하지 않고 보존한다.

### 식당·합석 테이블·합석 회차 삭제

- 예약·결제·노쇼 이력을 보존하기 위해 실제 삭제하지 않는다.
- 일반 조회에서는 삭제된 데이터를 제외한다.
- 연결된 예약이나 회차가 있으면 삭제 가능 여부를 검증한다.
- 합석 회차는 삭제된 이력을 보존하되, 같은 테이블·같은 시작 시각의 신규 회차를 다시 생성할 수 있다. 중복 검증은 `deletedAt`이 없는 활성 회차를 기준으로 한다.

---

# 14. V3 내부 구현·고도화 정책

## Kafka 적용 경계

- Kafka는 V3 확정 기술이다. 좌석 차감, 예약 확정, 결제 검증, 환불 상태 변경은 동기 트랜잭션 안에서 완료한다.
- Kafka는 트랜잭션 완료 후 알림, 운영 지표 집계, 정산 후속 처리, 실패 이벤트 재처리에 사용한다. Consumer는 중복 수신을 고려해 멱등성을 보장한다.

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

---

# 15. API 목록 요약

- 실제 HTTP API 수: **74개**
- API가 아닌 기능은 V2·V3 내부 구현 정책(13·14장)으로 별도 정리했다.
- 상세 명세가 없거나 다른 API와 중복돼 이번 정리에서 제거한 항목: `GET /api/restaurants/search`(3-5와 중복), `GET /api/payments/{paymentId}/status`(7-4와 중복), `GET /api/refunds/{refundId}/status`(8-2와 중복).
- 상세 명세도 요약표도 근거가 부족해 이번 집계에서 제외하고 16장 "결정 필요"로 넘긴 항목: `PATCH /api/owner/dining-sessions/{sessionId}/status`.
- 재검토 결과 제외한 항목: `POST /api/owner/restaurants/{restaurantId}/tables/dining-sessions`. 사장님 화면 흐름이 합석 테이블을 먼저 설정한 뒤 예약 가능 시간을 등록하는 구조이므로, 새 테이블 생성은 4-1 합석 테이블 등록 API로 처리하고 회차 일괄 생성은 5-2 기존 테이블 합석 회차 일괄 등록 API로 처리한다.

| 기능 분류 | 버전 | 기능명 | Method | Path | 담당자 |
|---|---|---|---|---|---|
| 공통 | V3 | API 모니터링 | `GET` | `/actuator/prometheus` | 김홍기 |
| 공통 | V3 | 애플리케이션 상태 확인 | `GET` | `/actuator/health` | 김홍기 |
| 회원·인증 | V1 | 일반 사용자 회원가입 | `POST` | `/api/auth/signup/users` | 정용태 |
| 회원·인증 | V1 | 사장님 회원가입 | `POST` | `/api/auth/signup/owners` | 정용태 |
| 회원·인증 | V1 | 로그인 | `POST` | `/api/auth/login` | 정용태 |
| 회원·인증 | V1 | 내 정보 조회 | `GET` | `/api/members/me` | 정용태 |
| 회원·인증 | V1 | 내 정보 수정 | `PATCH` | `/api/members/me` | 정용태 |
| 회원·인증 | V2 | 로그아웃 | `POST` | `/api/auth/logout` | 정용태 |
| 회원·인증 | V2 | 토큰 재발급 | `POST` | `/api/auth/reissue` | 정용태 |
| 회원·인증 | V1 | 회원 탈퇴 | `DELETE` | `/api/members/me` | 정용태 |
| 식당 관리 | V1 | 식당 등록 | `POST` | `/api/owner/restaurants` | 정용태 |
| 식당 관리 | V1 | 내 식당 목록 조회 | `GET` | `/api/owner/restaurants` | 정용태 |
| 식당 관리 | V1 | 내 식당 상세 조회 | `GET` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 식당 정보 수정 | `PATCH` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 사용자용 식당 목록·검색 | `GET` | `/api/restaurants` | 정용태·김홍기 |
| 식당 관리 | V1 | 사용자용 식당 상세 조회 | `GET` | `/api/restaurants/{restaurantId}` | 정용태 |
| 식당 관리 | V1 | 식당 삭제 | `DELETE` | `/api/owner/restaurants/{restaurantId}` | 정용태 |
| 합석 테이블 | V1 | 합석 테이블 등록 | `POST` | `/api/owner/restaurants/{restaurantId}/tables` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/tables` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 상세 조회 | `GET` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 수정 | `PATCH` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 테이블 | V1 | 합석 테이블 삭제 | `DELETE` | `/api/owner/tables/{tableId}` | 김홍기 |
| 합석 회차 | V1 | 합석 회차 등록 | `POST` | `/api/owner/tables/{tableId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 기존 테이블 합석 회차 일괄 등록 | `POST` | `/api/owner/tables/{tableId}/dining-sessions/bulk` | 김홍기 |
| 합석 회차 | V1 | 사장님용 회차 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 사용자용 예약 가능 회차 조회 | `GET` | `/api/restaurants/{restaurantId}/dining-sessions` | 김홍기 |
| 합석 회차 | V1 | 합석 회차 수정 | `PATCH` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 합석 회차 | V1 | 합석 회차 삭제 | `DELETE` | `/api/owner/dining-sessions/{sessionId}` | 김홍기 |
| 예약·참여 | V1 | 예약 가능 여부 확인 | `GET` | `/api/reservations/availability` | 배지현 |
| 예약·참여 | V1 | 예약 결제 준비 | `POST` | `/api/reservations/prepare` | 배지현 |
| 예약·참여 | V1 | 참여 가능한 예약 검색 | `GET` | `/api/reservations/search` | 김홍기 |
| 예약·참여 | V1 | 예약 상세 조회 | `GET` | `/api/reservations/{reservationId}` | 배지현 |
| 예약·참여 | V1 | 내 예약 목록 조회 | `GET` | `/api/members/me/reservations` | 배지현 |
| 예약·참여 | V1 | 내 예약 상세 조회 | `GET` | `/api/members/me/reservations/{reservationId}` | 배지현 |
| 예약·참여 | V1 | 내 참여 정보 조회 | `GET` | `/api/reservations/{reservationId}/participations/me` | 배지현 |
| 예약·참여 | V2 | 예약 참여자 목록 조회 | `GET` | `/api/reservations/{reservationId}/participations` | 배지현 |
| 예약·참여 | V1 | 모집 상태 변경 | `PATCH` | `/api/reservations/{reservationId}/recruitment` | 배지현 |
| 예약·참여 | V2 | 내 예약 참여 취소 | `POST` | `/api/reservations/{reservationId}/participations/me/cancel` | 배지현 |
| 예약·참여 | V1 | 식당별 예약 목록 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/reservations` | 배지현 |
| 예약·참여 | V1 | 사장님용 예약 상세 조회 | `GET` | `/api/owner/reservations/{reservationId}` | 배지현 |
| 예약·참여 | V1 | 사장님용 예약 참여자 목록 조회 | `GET` | `/api/owner/reservations/{reservationId}/participations` | 배지현 |
| 예약·참여 | V2 | 식당 귀책 예약 취소 | `POST` | `/api/owner/reservations/{reservationId}/cancel` | 배지현 |
| 결제 | V1 | 결제 완료 검증 | `POST` | `/api/payments/{paymentId}/complete` | 김현승 |
| 결제 | V1 | PortOne 결제 웹훅 | `POST` | `/api/webhooks/portone` | 김현승 |
| 결제 | V1 | 내 결제 목록 조회 | `GET` | `/api/members/me/payments` | 김현승 |
| 결제 | V1 | 결제 상세 조회 | `GET` | `/api/payments/{paymentId}` | 김현승 |
| 결제 | V3 | 결제 실패 재검증 | `POST` | `/api/admin/payments/{paymentId}/retry` | 김현승 |
| 환불 | V1 | 내 환불 목록 조회 | `GET` | `/api/members/me/refunds` | 김현승 |
| 환불 | V1 | 환불 상세 조회 | `GET` | `/api/refunds/{refundId}` | 김현승 |
| 환불 | V3 | 실패 환불 목록 조회 | `GET` | `/api/admin/refunds/failed` | 김현승 |
| 환불 | V3 | 실패 환불 재처리 | `POST` | `/api/admin/refunds/{refundId}/retry` | 김현승 |
| 노쇼 | V2 | 노쇼 처리 대상 참여자 조회 | `GET` | `/api/owner/reservations/{reservationId}/participations/no-show-candidates` | 정용태 |
| 노쇼 | V2 | 참여자 노쇼 처리 | `POST` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | 정용태 |
| 노쇼 | V2 | 노쇼 처리 해제 | `DELETE` | `/api/owner/reservations/{reservationId}/participations/{participationId}/no-show` | 정용태 |
| 노쇼 | V2 | 예약별 노쇼 이력 조회 | `GET` | `/api/owner/reservations/{reservationId}/no-show-histories` | 정용태 |
| 노쇼 | V2 | 식당 노쇼 고객 조회 | `GET` | `/api/owner/restaurants/{restaurantId}/no-shows` | 정용태 |
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
| 예약 참여자 채팅 | V2 | 채팅 메시지 목록 조회 | `GET` | `/api/chat/rooms/{chatRoomId}/messages` | 김현승 |

---

# 16. 구현 Issue에서 결정할 사항

아래 항목은 현재 확정 정책과 충돌하지 않으며, 실제 구현 시 세부 계약을 정한다.

- PortOne 공식 SDK·API 버전에 따른 웹훅 Payload와 서명 검증 코드
- 통합 예약 결제 준비 API의 좌석 임시 선점 저장소와 만료 처리 방식
- 결제 완료 검증과 웹훅 동시 실행에 대한 락·트랜잭션·멱등성 구현
- 도메인별 타인 리소스 접근 거부 에러 코드 이름
- DTO 클래스명·패키지 구조와 내부 매핑 방식
- `PATCH /api/owner/dining-sessions/{sessionId}/status`(합석 회차 상태 변경): 요약표에만 존재하고 상세 명세가 없었다. 0.7 공통 상태 목록에 회차(Session) 자체의 상태 enum이 정의돼 있지 않아, 현재 파일만으로는 합석 회차 수정(5-5)과 별개의 독립 기능인지 확정할 수 없다. 독립 기능이라면 SessionStatus 값 정의와 Request/Response/Error 명세를 추가해야 한다.
- (해결됨) tableId 기준 여러 날짜·시간 회차 일괄 생성 API: 사장님 화면 흐름은 합석 테이블 설정 후 예약 가능 시간을 등록하는 구조로 확정했다. 5-2 "기존 테이블 합석 회차 일괄 등록"(`POST /api/owner/tables/{tableId}/dining-sessions/bulk`)은 기존 테이블에 `intervalMinutes` 기준 회차를 여러 개 생성한다. 새 테이블 생성과 회차 생성을 한 번에 묶는 별도 API는 적용하지 않는다.
- (해결됨) 로그인 Access/Refresh Token 계약: 사용자 확인 결과 "V1 로그인은 Access Token만 반환하고, Refresh Token 발급·재발급·로그아웃은 V2에서 제공"(A안)으로 확정했다. 2-3 로그인 Response는 accessToken만 포함하며, 2-6 로그아웃·2-7 토큰 재발급은 `[V2]`로 유지한다.

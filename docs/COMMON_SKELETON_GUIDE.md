# 공통 개발 골격 사용 가이드

이 문서는 `common` 패키지에서 제공하는 공통 응답·예외·시간·보안 골격을 다른 도메인에서 사용하는 방법을 설명한다.
[docs/CODE_CONVENTION.md](./CODE_CONVENTION.md)의 §10~§12 규칙을 실제 클래스와 연결한 실행 가이드다.

## 1. 공통 응답 — `common.response.ApiResponse`

모든 Controller는 `ApiResponse<T>`로 응답을 감싼다.

```java
@GetMapping("/api/reservations/{id}")
public ApiResponse<ReservationResponse> getReservation(@PathVariable Long id) {
    return ApiResponse.success(reservationService.getReservation(id));
}
```

- 성공: `ApiResponse.success(data)`
- 실패는 직접 만들지 않는다. `CustomException`을 던지면 `GlobalExceptionHandler`가 자동으로 `ApiResponse.fail(errorCode)` 응답을 만든다.

## 2. 공통 예외 — `common.exception.ErrorCode`, `CustomException`

새 에러 코드가 필요하면 `common.exception.ErrorCode`에 상수를 추가한다.

```java
RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "식당을 찾을 수 없습니다."),
```

Service·Domain에서는 아래처럼 던진다. `GlobalExceptionHandler`는 수정하지 않는다.

```java
throw new CustomException(ErrorCode.RESTAURANT_NOT_FOUND);
```

Bean Validation 실패(`@Valid`)와 처리되지 않은 예외는 `GlobalExceptionHandler`가 각각
`INVALID_INPUT_VALUE`, `INTERNAL_SERVER_ERROR`로 자동 처리한다.

## 3. 인증 회원 — `common.security.AuthMember`, `MemberRole`

Controller는 인증된 회원 정보를 `@AuthenticationPrincipal`로 받는다. 클라이언트가 보낸 회원 ID는 사용하지 않는다.

```java
@PostMapping("/api/reservations")
public ApiResponse<ReservationResponse> create(
        @AuthenticationPrincipal AuthMember authMember,
        @Valid @RequestBody ReservationCreateRequest request
) {
    return ApiResponse.success(reservationService.create(authMember.id(), request));
}
```

- 인증 필요 API를 추가하려면 `common.security.SecurityConfig`의 `authorizeHttpRequests`에 경로 규칙을 추가한다.
- 인증 실패(401)·권한 부족(403) 응답은 `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`가 공통 포맷으로 처리하므로 별도 처리가 필요 없다.
- 실제 JWT 인증 필터는 이 Issue의 범위가 아니며 이후 Issue에서 추가된다.

## 4. 시간 정책 — `common.config.ClockConfig`, `JpaAuditingConfig`, `common.entity.BaseTimeEntity`

- DB에는 UTC(`Instant`)로 저장하고, API 응답은 `application.yml`의 `spring.jackson.time-zone: Asia/Seoul` 설정으로 자동 변환된다.
- 현재 시각이 필요하면 시스템 시각을 직접 호출하지 않고 주입된 `Clock`을 사용한다.

```java
public ReservationService(Clock clock) {
    this.clock = clock;
}
```

- 생성·수정 시각이 필요한 Entity는 `BaseTimeEntity`를 상속한다.

```java
public class Reservation extends BaseTimeEntity {
}
```

## 5. 이 가이드에 포함되지 않는 것

회원가입·로그인, JWT 발급·검증, 각 도메인 권한 세부 정책은 이 골격의 범위가 아니며 해당 Issue에서 별도로 다룬다.

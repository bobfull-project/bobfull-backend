package com.bobfull.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 예약 결제 준비(#35) 전용 에러 코드다.
 * 다른 도메인은 같은 방식으로 BaseErrorCode를 구현하는 별도 Enum을 추가한다.
 */
public enum ReservationErrorCode implements BaseErrorCode {

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상 회차 또는 예약을 찾을 수 없습니다."),
    INVALID_PARTY_SIZE(HttpStatus.BAD_REQUEST, "partySize가 1 이상이 아니거나 허용 범위를 초과했습니다."),
    ACTIVE_RESERVATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 예약 또는 결제 준비가 있습니다."),
    INSUFFICIENT_REMAINING_CAPACITY(HttpStatus.CONFLICT, "남은 참여 가능 인원을 초과했습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서 요청을 처리할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ReservationErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}

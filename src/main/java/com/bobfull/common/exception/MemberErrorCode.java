package com.bobfull.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 회원 도메인 전용 에러 코드다.
 * 다른 도메인(식당·예약 등)은 같은 방식으로 BaseErrorCode를 구현하는 별도 Enum을 추가한다.
 */
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    MemberErrorCode(HttpStatus httpStatus, String message) {
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

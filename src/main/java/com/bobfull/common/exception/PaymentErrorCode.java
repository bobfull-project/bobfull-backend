package com.bobfull.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Payment 도메인 전용 에러 코드다.
 */
public enum PaymentErrorCode implements BaseErrorCode {

    DUPLICATE_PAYMENT_ID(HttpStatus.CONFLICT, "이미 존재하는 paymentId입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "결제 접근 권한이 없습니다."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.CONFLICT, "결제 검증에 실패했습니다."),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되었습니다."),
    RESERVATION_CONFIRMATION_NOT_READY(HttpStatus.CONFLICT, "예약 확정 기능이 아직 준비되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    PaymentErrorCode(HttpStatus httpStatus, String message) {
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

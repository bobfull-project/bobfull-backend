package com.bobfull.common.exception;

/**
 * 도메인 비즈니스 예외의 공통 상위 타입이다.
 * ErrorCode를 함께 전달해 GlobalExceptionHandler가 일관된 응답을 만들도록 한다.
 */
public class CustomException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public CustomException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BaseErrorCode getErrorCode() {
        return errorCode;
    }
}

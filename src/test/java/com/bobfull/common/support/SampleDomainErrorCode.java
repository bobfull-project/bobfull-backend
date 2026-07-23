package com.bobfull.common.support;

import com.bobfull.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 새 도메인이 BaseErrorCode만 구현하면 GlobalExceptionHandler를 수정하지 않고도
 * 동일한 공통 실패 응답이 만들어지는지 검증하기 위한 테스트 전용 Enum이다.
 */
public enum SampleDomainErrorCode implements BaseErrorCode {

    SAMPLE_DOMAIN_CONFLICT(HttpStatus.CONFLICT, "테스트용 도메인 충돌 에러입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    SampleDomainErrorCode(HttpStatus httpStatus, String message) {
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

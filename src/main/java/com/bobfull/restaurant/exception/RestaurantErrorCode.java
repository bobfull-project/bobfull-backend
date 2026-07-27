package com.bobfull.restaurant.exception;

import com.bobfull.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 식당 식별과 소유권 확인 과정에서 사용하는 에러 코드다.
 */
public enum RestaurantErrorCode implements BaseErrorCode {

    RESTAURANT_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "식당을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    RestaurantErrorCode(HttpStatus httpStatus, String message) {
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

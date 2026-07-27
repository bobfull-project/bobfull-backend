package com.bobfull.sharedtable.exception;

import com.bobfull.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 합석 테이블 API 전용 에러 코드다.
 */
public enum SharedTableErrorCode implements BaseErrorCode {

    INVALID_TABLE_CAPACITY(HttpStatus.BAD_REQUEST, "합석 테이블 정원은 2, 4, 6, 8 중 하나여야 합니다."),
    TABLE_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "합석 테이블을 찾을 수 없습니다."),
    TABLE_HAS_DINING_SESSION(HttpStatus.CONFLICT, "연결된 회차가 있어 삭제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    SharedTableErrorCode(HttpStatus httpStatus, String message) {
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

package com.bobfull.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러 코드 Enum이 구현해야 하는 최소 계약이다.
 * 새 도메인은 이 인터페이스를 구현하는 별도 Enum을 추가하기만 하면 되고,
 * GlobalExceptionHandler·ApiResponse는 이 인터페이스만 알면 되므로 수정이 필요 없다.
 */
public interface BaseErrorCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}

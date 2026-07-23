package com.bobfull.common.support;

import jakarta.validation.constraints.NotBlank;

/**
 * GlobalExceptionHandler의 Bean Validation 실패 처리를 검증하기 위한 테스트 전용 요청 DTO다.
 */
public record TestRequest(@NotBlank String name) {
}

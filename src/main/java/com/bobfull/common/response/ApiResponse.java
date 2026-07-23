package com.bobfull.common.response;

import com.bobfull.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 응답이 공유하는 공통 응답 포맷이다.
 * 성공 응답은 data를, 실패 응답은 code를 채운다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String code;

    private ApiResponse(boolean success, String message, T data, String code) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.code = code;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "요청이 성공했습니다.", data, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getMessage(), null, errorCode.name());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getCode() {
        return code;
    }
}

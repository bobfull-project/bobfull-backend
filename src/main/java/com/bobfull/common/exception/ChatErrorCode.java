package com.bobfull.common.exception;
import org.springframework.http.HttpStatus;
public enum ChatErrorCode implements BaseErrorCode {
    CHAT_ROOM_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "chatRoomId에 해당하는 대상을 찾을 수 없습니다.");
    private final HttpStatus status; private final String message;
    ChatErrorCode(HttpStatus status, String message) { this.status=status; this.message=message; }
    public HttpStatus getHttpStatus() { return status; } public String getCode() { return name(); } public String getMessage() { return message; }
}

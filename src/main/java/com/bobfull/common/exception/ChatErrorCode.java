package com.bobfull.common.exception;
import org.springframework.http.HttpStatus;
public enum ChatErrorCode implements BaseErrorCode {
    CHAT_ROOM_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "chatRoomId에 해당하는 대상을 찾을 수 없습니다."),
    CHAT_MESSAGE_ID_NOT_FOUND(HttpStatus.NOT_FOUND, "messageId에 해당하는 대상을 찾을 수 없습니다."),
    CHAT_MESSAGE_SEND_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 예약 상태에서는 메시지를 전송할 수 없습니다."),
    CHAT_ROOM_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "채팅방을 아직 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");
    private final HttpStatus status; private final String message;
    ChatErrorCode(HttpStatus status, String message) { this.status=status; this.message=message; }
    public HttpStatus getHttpStatus() { return status; } public String getCode() { return name(); } public String getMessage() { return message; }
}

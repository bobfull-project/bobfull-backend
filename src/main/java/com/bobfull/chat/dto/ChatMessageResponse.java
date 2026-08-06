package com.bobfull.chat.dto;
import com.bobfull.chat.entity.ChatMessage;
import java.time.OffsetDateTime;
public record ChatMessageResponse(Long messageId, Long senderMemberId, String senderName, String content, OffsetDateTime sentAt) {
    public static ChatMessageResponse of(ChatMessage m, String name, OffsetDateTime sentAt) { return new ChatMessageResponse(m.getId(), m.getSenderMemberId(), name, m.getContent(), sentAt); }
}

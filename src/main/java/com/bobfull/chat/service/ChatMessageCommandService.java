package com.bobfull.chat.service;
import com.bobfull.chat.dto.ChatMessageSentResponse;
import com.bobfull.chat.entity.*;
import com.bobfull.chat.port.*;
import com.bobfull.chat.repository.*;
import com.bobfull.common.exception.*;
import com.bobfull.common.security.*;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class ChatMessageCommandService {
    private final ChatRoomRepository rooms; private final ChatMessageRepository messages; private final ReservationChatAccessReader access; private final MemberNameReader names; private final Clock clock;
    public ChatMessageCommandService(ChatRoomRepository rooms, ChatMessageRepository messages, ReservationChatAccessReader access, MemberNameReader names, Clock clock) { this.rooms=rooms;this.messages=messages;this.access=access;this.names=names;this.clock=clock; }
    @Transactional public ChatMessageSentResponse send(Long roomId, AuthMember member, String content) {
        if(member.role()!=MemberRole.MEMBER) throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        if(content==null||content.isBlank()||content.length()>1000) throw new CustomException(CommonErrorCode.INVALID_INPUT_VALUE);
        ChatRoom room=rooms.findById(roomId).orElseThrow(()->new CustomException(ChatErrorCode.CHAT_ROOM_ID_NOT_FOUND));
        ReservationChatAccessReader.ChatAccess current=access.read(room.getReservationId(),member.id());
        if(current==null||!current.isActive()) throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        if(!current.canSend(clock.instant())) throw new CustomException(ChatErrorCode.CHAT_MESSAGE_SEND_NOT_ALLOWED);
        ChatMessage saved=messages.save(ChatMessage.create(roomId,member.id(),current.participantId(),content));
        Map<Long,String> namesById=names.readNames(java.util.Set.of(member.id()));
        return ChatMessageSentResponse.of(saved,namesById.get(member.id()));
    }
}

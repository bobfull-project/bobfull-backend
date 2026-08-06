package com.bobfull.chat.service;
import com.bobfull.chat.dto.ChatRoomResponse;
import com.bobfull.chat.port.ReservationChatAccessReader;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ChatErrorCode;
import com.bobfull.common.security.MemberRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ChatRoomQueryService {
    private final ChatRoomRepository rooms; private final ReservationChatAccessReader access;
    public ChatRoomQueryService(ChatRoomRepository rooms, ReservationChatAccessReader access) { this.rooms=rooms; this.access=access; }
    @Transactional(readOnly = true) public ChatRoomResponse get(Long memberId, MemberRole role, Long reservationId) {
        if (role != MemberRole.MEMBER) throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        ReservationChatAccessReader.ChatAccess chatAccess = access.read(reservationId, memberId);
        if (chatAccess == null || !chatAccess.isActive()) throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        return ChatRoomResponse.from(rooms.findByReservationId(reservationId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_ROOM_ID_NOT_FOUND)));
    }
}

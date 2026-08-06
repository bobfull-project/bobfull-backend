package com.bobfull.chat.dto;
import com.bobfull.chat.entity.ChatRoom;
public record ChatRoomResponse(Long chatRoomId, Long reservationId) { public static ChatRoomResponse from(ChatRoom room) { return new ChatRoomResponse(room.getId(), room.getReservationId()); } }

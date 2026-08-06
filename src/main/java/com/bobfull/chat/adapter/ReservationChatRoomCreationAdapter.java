package com.bobfull.chat.adapter;
import com.bobfull.chat.entity.ChatRoom;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.reservation.port.ChatRoomCreationPort;
import org.springframework.stereotype.Component;
/** 채팅방 UNIQUE 제약과 함께 CREATE 완료의 중복 전달에도 한 방만 남기게 한다. */
@Component
public class ReservationChatRoomCreationAdapter implements ChatRoomCreationPort {
    private final ChatRoomRepository repository;
    public ReservationChatRoomCreationAdapter(ChatRoomRepository repository) { this.repository = repository; }
    @Override public void createForReservation(Long reservationId) {
        if (repository.findByReservationId(reservationId).isPresent()) return;
        repository.save(ChatRoom.create(reservationId));
    }
}

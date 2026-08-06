package com.bobfull.chat.repository;
import com.bobfull.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> { Optional<ChatRoom> findByReservationId(Long reservationId); }

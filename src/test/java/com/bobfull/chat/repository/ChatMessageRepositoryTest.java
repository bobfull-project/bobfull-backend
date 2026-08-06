package com.bobfull.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bobfull.chat.entity.ChatMessage;
import com.bobfull.chat.entity.ChatRoom;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class ChatMessageRepositoryTest {
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;

    @Test
    void 같은_예약에는_채팅방을_하나만_저장한다() {
        // given
        chatRoomRepository.saveAndFlush(ChatRoom.create(10L));

        // when & then
        assertThatThrownBy(() -> chatRoomRepository.saveAndFlush(ChatRoom.create(10L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cursor보다_작은_같은_채팅방_메시지만_최신순으로_조회한다() {
        // given
        ChatRoom first = chatRoomRepository.save(ChatRoom.create(10L));
        ChatRoom other = chatRoomRepository.save(ChatRoom.create(20L));
        ChatMessage oldest = chatMessageRepository.save(ChatMessage.create(first.getId(), 1L, 1L, "첫 메시지"));
        ChatMessage cursor = chatMessageRepository.save(ChatMessage.create(first.getId(), 1L, 1L, "두 번째 메시지"));
        chatMessageRepository.save(ChatMessage.create(first.getId(), 1L, 1L, "세 번째 메시지"));
        chatMessageRepository.save(ChatMessage.create(other.getId(), 2L, 2L, "다른 방 메시지"));

        // when
        List<ChatMessage> result = chatMessageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(
                first.getId(), cursor.getId(), PageRequest.of(0, 3));

        // then
        assertThat(result).extracting(ChatMessage::getId).containsExactly(oldest.getId());
    }
}

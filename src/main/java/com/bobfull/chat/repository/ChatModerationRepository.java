package com.bobfull.chat.repository;

import com.bobfull.chat.entity.ChatModeration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatModerationRepository extends JpaRepository<ChatModeration, Long> {
    Optional<ChatModeration> findByMessageId(Long messageId);
}

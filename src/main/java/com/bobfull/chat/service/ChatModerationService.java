package com.bobfull.chat.service;

import com.bobfull.chat.dto.AiModerationResponse;
import com.bobfull.chat.adapter.ModerationPrompt;
import com.bobfull.chat.entity.ChatMessage;
import com.bobfull.chat.entity.ChatModeration;
import com.bobfull.chat.repository.ChatMessageRepository;
import com.bobfull.chat.repository.ChatModerationRepository;
import com.bobfull.chat.port.AiModerationPort;
import com.bobfull.common.exception.ChatErrorCode;
import com.bobfull.common.exception.CustomException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** ChatMessage를 짧게 조회한 뒤 트랜잭션 밖에서 AI를 호출하고 결과만 영속화한다. */
@Service
public class ChatModerationService {
    private static final Logger log = LoggerFactory.getLogger(ChatModerationService.class);
    private final ChatMessageRepository messages;
    private final ChatModerationRepository moderations;
    private final AiModerationPort aiModerationPort;
    private final Clock clock;
    public ChatModerationService(ChatMessageRepository messages, ChatModerationRepository moderations,
            AiModerationPort aiModerationPort, Clock clock) {
        this.messages = messages; this.moderations = moderations; this.aiModerationPort = aiModerationPort; this.clock = clock;
    }
    public void analyze(Long messageId) {
        ChatModeration existing = moderations.findByMessageId(messageId).orElse(null);
        if (existing != null && existing.isCompleted()) {
            log.info("event=CHAT_MODERATION_SKIPPED messageId={} status={}", messageId, existing.getStatus());
            return;
        }
        ChatMessage message = messages.findById(messageId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_MESSAGE_ID_NOT_FOUND));
        long startedAt = System.nanoTime();
        try {
            AiModerationResponse response = aiModerationPort.analyze(message.getContent());
            ModerationResultValidator.validate(response == null ? null : response.result());
            persistCompleted(messageId, existing, response, elapsedMillis(startedAt));
        } catch (ModerationAnalysisException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String errorCode = exception.getClass().getSimpleName();
            throw new ModerationAnalysisException(errorCode, exception);
        }
    }
    /** #59가 Kafka Retry를 소진하고 DLT로 보낼 때만 호출하는 최종 실패 기록 진입점이다. */
    public void recordFinalFailure(Long messageId, String errorCode) {
        ChatModeration existing = moderations.findByMessageId(messageId).orElse(null);
        if (existing != null && existing.isCompleted()) return;
        persistFailure(messageId, existing, 0L, errorCode);
    }
    private void persistCompleted(Long messageId, ChatModeration existing, AiModerationResponse response, long latencyMillis) {
        Instant now = clock.instant();
        ChatModeration moderation = existing == null ? ChatModeration.completed(messageId, response.result().result(), response.result().categories(),
                response.result().riskLevel(), response.provider(), response.model(), ModerationPrompt.PROMPT_VERSION, ModerationPrompt.POLICY_VERSION, latencyMillis,
                response.promptTokens(), response.completionTokens(), response.totalTokens(), now) : existing;
        if (existing != null) moderation.complete(response.result().result(), response.result().categories(), response.result().riskLevel(),
                response.provider(), response.model(), ModerationPrompt.PROMPT_VERSION, ModerationPrompt.POLICY_VERSION, latencyMillis, response.promptTokens(),
                response.completionTokens(), response.totalTokens(), now);
        try { moderations.saveAndFlush(moderation); }
        catch (DataIntegrityViolationException exception) {
            if (moderations.findByMessageId(messageId).filter(ChatModeration::isCompleted).isPresent()) return;
            throw exception;
        }
        log.info("event=CHAT_MODERATION_COMPLETED messageId={} result={} categories={} riskLevel={} latencyMillis={}",
                messageId, response.result().result(), response.result().categories(), response.result().riskLevel(), latencyMillis);
    }
    private void persistFailure(Long messageId, ChatModeration existing, long latencyMillis, String errorCode) {
        Instant now = clock.instant();
        ChatModeration moderation = existing == null ? ChatModeration.failed(messageId, "OpenAI", "NOT_MEASURED", ModerationPrompt.PROMPT_VERSION,
                ModerationPrompt.POLICY_VERSION, latencyMillis, now, errorCode) : existing;
        if (existing != null) moderation.fail("OpenAI", "NOT_MEASURED", ModerationPrompt.PROMPT_VERSION, ModerationPrompt.POLICY_VERSION, latencyMillis, now, errorCode);
        moderations.saveAndFlush(moderation);
        log.warn("event=CHAT_MODERATION_FAILED messageId={} errorCode={} latencyMillis={}", messageId, errorCode, latencyMillis);
    }
    private static long elapsedMillis(long startedAt) { return (System.nanoTime() - startedAt) / 1_000_000; }
}

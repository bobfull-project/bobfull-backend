package com.bobfull.outbox.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ChatMessage 저장 트랜잭션의 커밋 스레드가 Kafka ACK 대기로 막히지 않도록,
 * {@code ChatMessageOutboxProcessor.signal(...)} 호출을 별도 스레드로 넘긴다.
 * signal 디스패치 자체가 유실돼도 {@code ChatMessageOutboxScheduler}가 PENDING Outbox를
 * 폴링해 재처리하므로 정합성에는 영향이 없다(순수 응답성 최적화 경로).
 */
@Component
public class ChatMessageOutboxSignalDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageOutboxSignalDispatcher.class);

    private final ChatMessageOutboxProcessor processor;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chat-message-outbox-signal");
        thread.setDaemon(true);
        return thread;
    });

    public ChatMessageOutboxSignalDispatcher(ChatMessageOutboxProcessor processor) {
        this.processor = processor;
    }

    public void dispatch(Long outboxEventId) {
        executor.execute(() -> {
            try {
                processor.signal(outboxEventId);
            } catch (RuntimeException exception) {
                log.error("event=OUTBOX_SIGNAL_DISPATCH_FAILED outboxEventId={} reason={}",
                        outboxEventId, exception.toString(), exception);
            }
        });
    }
}

package com.bobfull.chat.adapter;

import com.bobfull.chat.service.ChatRoomCreationService;
import com.bobfull.common.monitoring.BusinessMetricEvent;
import com.bobfull.common.monitoring.BusinessMetricRecorder;
import com.bobfull.reservation.event.ReservationConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 핵심 결제·예약 확정 트랜잭션이 실제로 커밋된 뒤에만 ChatRoom을 생성한다(AFTER_COMMIT).
 * 트랜잭션이 롤백되면 이 리스너 자체가 실행되지 않는다. ChatRoom 생성이 실패해도 이미 커밋된
 * Payment·Reservation·Participant에는 영향이 없어야 하므로 예외를 다시 던지지 않고 구조화
 * 로그만 남긴다 — 누락 복구는 채팅방 조회 시점의 최소 복구 경로가 담당한다(#50 PR #174).
 */
@Component
public class ChatRoomCreationEventListener {
    private static final Logger log = LoggerFactory.getLogger(ChatRoomCreationEventListener.class);

    private final ChatRoomCreationService chatRoomCreationService;
    private final BusinessMetricRecorder businessMetricRecorder;

    public ChatRoomCreationEventListener(
            ChatRoomCreationService chatRoomCreationService,
            BusinessMetricRecorder businessMetricRecorder
    ) {
        this.chatRoomCreationService = chatRoomCreationService;
        this.businessMetricRecorder = businessMetricRecorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReservationConfirmedEvent event) {
        try {
            chatRoomCreationService.createIfAbsent(event.reservationId());
        } catch (RuntimeException exception) {
            log.error("event=CHAT_ROOM_CREATION_REQUIRED reservationId={}", event.reservationId(), exception);
            businessMetricRecorder.increment(BusinessMetricEvent.CHAT_ROOM_CREATION_REQUIRED);
        }
    }
}

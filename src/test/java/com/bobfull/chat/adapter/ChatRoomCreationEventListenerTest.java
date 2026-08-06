package com.bobfull.chat.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.chat.service.ChatRoomCreationService;
import com.bobfull.reservation.event.ReservationConfirmedEvent;
import org.junit.jupiter.api.Test;

class ChatRoomCreationEventListenerTest {

    private final ChatRoomCreationService chatRoomCreationService =
            org.mockito.Mockito.mock(ChatRoomCreationService.class);
    private final ChatRoomCreationEventListener listener =
            new ChatRoomCreationEventListener(chatRoomCreationService);

    @Test
    void 이벤트를_받으면_해당_reservationId로_ChatRoom_생성을_시도한다() {
        // when
        listener.handle(new ReservationConfirmedEvent(10L));

        // then
        verify(chatRoomCreationService).createIfAbsent(10L);
    }

    @Test
    void ChatRoom_생성이_실패해도_예외를_밖으로_전파하지_않는다() {
        // given
        given(chatRoomCreationService.createIfAbsent(10L)).willThrow(new IllegalStateException("강제 실패(테스트)"));

        // when & then
        assertThatCode(() -> listener.handle(new ReservationConfirmedEvent(10L))).doesNotThrowAnyException();
    }
}

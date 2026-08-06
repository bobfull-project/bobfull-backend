package com.bobfull.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bobfull.chat.entity.ChatRoom;
import com.bobfull.chat.port.ReservationChatAccessReader;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.security.MemberRole;
import com.bobfull.reservation.entity.ParticipationStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatRoomQueryServiceTest {
    private final ChatRoomRepository rooms = org.mockito.Mockito.mock(ChatRoomRepository.class);
    private final ReservationChatAccessReader access = org.mockito.Mockito.mock(ReservationChatAccessReader.class);
    private final ChatRoomQueryService service = new ChatRoomQueryService(rooms, access);

    @Test
    void 비참여자와_CANCELLED_참여자와_MEMBER가_아닌_역할은_403으로_거부한다() {
        // given
        given(access.read(10L, 1L)).willReturn(null);
        given(access.read(10L, 2L)).willReturn(new ReservationChatAccessReader.ChatAccess(3L, ParticipationStatus.CANCELLED));

        // when & then
        assertAccessDenied(() -> service.get(1L, MemberRole.MEMBER, 10L));
        assertAccessDenied(() -> service.get(2L, MemberRole.MEMBER, 10L));
        assertAccessDenied(() -> service.get(3L, MemberRole.OWNER, 10L));
    }

    private void assertAccessDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}

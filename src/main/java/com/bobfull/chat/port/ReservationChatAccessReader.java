package com.bobfull.chat.port;
import com.bobfull.reservation.entity.ParticipationStatus;
public interface ReservationChatAccessReader {
    ChatAccess read(Long reservationId, Long memberId);
    record ChatAccess(Long participantId, ParticipationStatus participationStatus) { public boolean isActive() { return participationStatus != ParticipationStatus.CANCELLED; } }
}

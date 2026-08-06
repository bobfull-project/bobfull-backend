package com.bobfull.chat.port;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationStatus;
public interface ReservationChatAccessReader {
    ChatAccess read(Long reservationId, Long memberId);
    record ChatAccess(Long participantId, ParticipationStatus participationStatus, ReservationStatus reservationStatus) {
        public ChatAccess(Long participantId, ParticipationStatus participationStatus) { this(participantId, participationStatus, ReservationStatus.RECRUITING); }
        public boolean isActive() { return participationStatus != ParticipationStatus.CANCELLED; }
        public boolean canSend() { return isActive() && (reservationStatus == ReservationStatus.RECRUITING || reservationStatus == ReservationStatus.CONFIRMED); }
    }
}

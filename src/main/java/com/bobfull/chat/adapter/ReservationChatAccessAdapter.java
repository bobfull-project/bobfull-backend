package com.bobfull.chat.adapter;
import com.bobfull.chat.port.ReservationChatAccessReader;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import org.springframework.stereotype.Component;
@Component
public class ReservationChatAccessAdapter implements ReservationChatAccessReader {
    private final ReservationParticipantRepository repository;
    public ReservationChatAccessAdapter(ReservationParticipantRepository repository) { this.repository = repository; }
    public ChatAccess read(Long reservationId, Long memberId) {
        return repository.findByReservationIdAndMemberId(reservationId, memberId)
                .map(p -> new ChatAccess(p.getId(), p.getParticipationStatus())).orElse(null);
    }
}

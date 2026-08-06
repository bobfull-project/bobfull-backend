package com.bobfull.chat.adapter;
import com.bobfull.chat.port.ReservationChatAccessReader;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Component;
@Component
public class ReservationChatAccessAdapter implements ReservationChatAccessReader {
    private final ReservationParticipantRepository repository;
    private final ReservationRepository reservationRepository;
    public ReservationChatAccessAdapter(ReservationParticipantRepository repository, ReservationRepository reservationRepository) { this.repository = repository; this.reservationRepository = reservationRepository; }
    public ChatAccess read(Long reservationId, Long memberId) {
        return reservationRepository.findById(reservationId).flatMap(reservation -> repository.findByReservationIdAndMemberId(reservationId, memberId)
                .map(p -> new ChatAccess(p.getId(), p.getParticipationStatus(), reservation.getReservationStatus()))).orElse(null);
    }
}

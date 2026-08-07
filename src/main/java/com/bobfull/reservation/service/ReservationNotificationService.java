package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.member.entity.Member;
import com.bobfull.member.repository.MemberRepository;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.port.ReservationNotificationPort;
import com.bobfull.reservation.port.ReservationNotificationPort.Recipient;
import com.bobfull.reservation.port.ReservationNotificationPort.ReservationResultNotification;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * #47 모집 마감 처리 결과(확정·인원 미달 취소)와 결제 완료(접수·참여) 결과를 이메일로 안내한다
 * (Issue #168 V2). 호출자(각 AFTER_COMMIT 이벤트 리스너)는 핵심 트랜잭션이 이미 커밋된 뒤에만
 * 이 서비스를 호출하며, 이 클래스는 안내 대상 조회부터 실제 발송 요청까지의 모든 예외를 삼켜
 * 로그만 남긴다 — 여기서 발생하는 어떤 실패도 이미 커밋된 예약·결제 결과에 영향을 주면 안 되기
 * 때문이다.
 */
@Service
public class ReservationNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationNotificationService.class);

    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SharedTableRepository sharedTableRepository;
    private final RestaurantRepository restaurantRepository;
    private final MemberRepository memberRepository;
    private final ReservationNotificationPort notificationPort;

    public ReservationNotificationService(
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            TimeSlotRepository timeSlotRepository,
            SharedTableRepository sharedTableRepository,
            RestaurantRepository restaurantRepository,
            MemberRepository memberRepository,
            ReservationNotificationPort notificationPort
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.sharedTableRepository = sharedTableRepository;
        this.restaurantRepository = restaurantRepository;
        this.memberRepository = memberRepository;
        this.notificationPort = notificationPort;
    }

    /** 모집 마감 시점에 확정 기준을 이미 충족한 예약의 유효 참여자에게 확정 안내를 보낸다. */
    public void notifyConfirmed(Long reservationId) {
        notify(reservationId,
                () -> reservationParticipantRepository.findAllByReservationIdAndParticipationStatus(
                        reservationId, ParticipationStatus.RESERVED),
                notificationPort::notifyConfirmed);
    }

    /** 모집 마감 시점에 인원 미달로 취소 접수된 참여자들에게 취소 안내를 보낸다. */
    public void notifyCancelledDueToInsufficientParticipants(Long reservationId, List<Long> participantIds) {
        notify(reservationId,
                () -> reservationParticipantRepository.findAllById(participantIds),
                notificationPort::notifyCancelledDueToInsufficientParticipants);
    }

    /** 최초(CREATE) 결제 완료로 예약이 접수된 참여자 본인에게 접수 안내를 보낸다. */
    public void notifyReservationCreated(Long reservationId, Long participantId) {
        notify(reservationId,
                () -> reservationParticipantRepository.findAllById(List.of(participantId)),
                notificationPort::notifyReservationCreated);
    }

    /** 추가(JOIN) 결제 완료로 참여가 완료된 참여자 본인에게 참여 완료 안내를 보낸다. */
    public void notifyParticipationCompleted(Long reservationId, Long participantId) {
        notify(reservationId,
                () -> reservationParticipantRepository.findAllById(List.of(participantId)),
                notificationPort::notifyParticipationCompleted);
    }

    private void notify(
            Long reservationId,
            Supplier<List<ReservationParticipant>> participantSupplier,
            Consumer<ReservationResultNotification> sender
    ) {
        try {
            List<ReservationParticipant> participants = participantSupplier.get();
            if (participants.isEmpty()) {
                return;
            }
            sender.accept(buildNotification(reservationId, participants));
        } catch (RuntimeException exception) {
            // 안내 대상 조회 또는 발송 요청 중 어떤 예외가 나도 이미 커밋된 예약 결과와 무관하게
            // 이 자리에서 흡수한다 — 예약 상태에는 영향을 주지 않고 실패만 남긴다.
            log.error("event=RESERVATION_NOTIFICATION_FAILED reservationId={} reason={}",
                    reservationId, exception.toString());
        }
    }

    private ReservationResultNotification buildNotification(Long reservationId, List<ReservationParticipant> participants) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESERVATION_ID_NOT_FOUND));
        TimeSlot timeSlot = timeSlotRepository.findByIdAndDeletedAtIsNull(reservation.getTimeSlotId())
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
        SharedTable sharedTable = sharedTableRepository.findByIdAndDeletedAtIsNull(timeSlot.getSharedTableId())
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(sharedTable.getRestaurantId())
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESOURCE_NOT_FOUND));

        List<Long> memberIds = participants.stream().map(ReservationParticipant::getMemberId).distinct().toList();
        Map<Long, Member> membersById = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, member -> member));
        List<Recipient> recipients = memberIds.stream()
                .map(membersById::get)
                .filter(Objects::nonNull)
                .map(member -> new Recipient(member.getId(), member.getEmail(), member.getName()))
                .toList();

        return new ReservationResultNotification(
                reservationId, restaurant.getName(), restaurant.getAddress(), timeSlot.getStartAt(), recipients);
    }
}

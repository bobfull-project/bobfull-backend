package com.bobfull.reservation.port;

/** 최초 예약 확정 트랜잭션에서 채팅방 생성을 요청하는 예약 도메인의 출력 포트다. */
public interface ChatRoomCreationPort {
    void createForReservation(Long reservationId);
}

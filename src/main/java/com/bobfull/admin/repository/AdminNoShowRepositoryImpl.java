package com.bobfull.admin.repository;

import com.bobfull.admin.dto.AdminNoShowResult;
import com.bobfull.member.entity.QMember;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.QNoShowHistory;
import com.bobfull.reservation.entity.QReservation;
import com.bobfull.reservation.entity.QReservationParticipant;
import com.bobfull.restaurant.entity.QRestaurant;
import com.bobfull.sharedtable.entity.QSharedTable;
import com.bobfull.timeslot.entity.QTimeSlot;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** ADMIN 전체 노쇼 현황 조회(§11-8)를 담당한다(Issue #134). */
@Repository
public class AdminNoShowRepositoryImpl implements AdminNoShowRepository {

    private final JPAQueryFactory queryFactory;

    public AdminNoShowRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<AdminNoShowResult> searchNoShows(Long memberId, Long restaurantId, Pageable pageable) {
        QNoShowHistory history = QNoShowHistory.noShowHistory;
        QReservationParticipant participant = QReservationParticipant.reservationParticipant;
        QReservation reservation = QReservation.reservation;
        QTimeSlot timeSlot = QTimeSlot.timeSlot;
        QSharedTable sharedTable = QSharedTable.sharedTable;
        QRestaurant restaurant = QRestaurant.restaurant;
        QMember member = QMember.member;

        BooleanBuilder predicates = new BooleanBuilder();
        predicates.and(history.marked.isTrue());
        // 노쇼 처리 후 해제된(RESERVED로 복귀한) 참여자의 과거 이력은 "현재 노쇼 현황"에서 제외한다.
        predicates.and(participant.participationStatus.eq(ParticipationStatus.NO_SHOW));
        if (memberId != null) {
            predicates.and(participant.memberId.eq(memberId));
        }
        if (restaurantId != null) {
            predicates.and(restaurant.id.eq(restaurantId));
        }

        var content = queryFactory
                .select(Projections.constructor(
                        AdminNoShowResult.class,
                        history.id,
                        participant.memberId,
                        member.name,
                        restaurant.id,
                        restaurant.name,
                        participant.reservationId,
                        participant.id,
                        participant.partySize,
                        history.processedAt))
                .from(history)
                .join(participant).on(participant.id.eq(history.reservationParticipantId))
                .join(reservation).on(reservation.id.eq(participant.reservationId))
                .join(timeSlot).on(timeSlot.id.eq(reservation.timeSlotId))
                .join(sharedTable).on(sharedTable.id.eq(timeSlot.sharedTableId))
                .join(restaurant).on(restaurant.id.eq(sharedTable.restaurantId))
                .join(member).on(member.id.eq(participant.memberId))
                .where(predicates)
                .orderBy(history.processedAt.desc(), history.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(history.count())
                .from(history)
                .join(participant).on(participant.id.eq(history.reservationParticipantId))
                .join(reservation).on(reservation.id.eq(participant.reservationId))
                .join(timeSlot).on(timeSlot.id.eq(reservation.timeSlotId))
                .join(sharedTable).on(sharedTable.id.eq(timeSlot.sharedTableId))
                .join(restaurant).on(restaurant.id.eq(sharedTable.restaurantId))
                .where(predicates)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}

package com.bobfull.timeslottemp.repository;

import com.bobfull.timeslottemp.entity.TimeSlot;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    /**
     * 예약 결제 준비의 TimeSlot 활성 예약 정합성(docs/ERD.md §9, docs/adr/0001)을 위해
     * 대상 회차 행을 비관적 락으로 조회하고 트랜잭션 종료까지 잠금을 유지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TimeSlot t where t.id = :id and t.deletedAt is null")
    Optional<TimeSlot> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "select st.capacity as capacity, r.deposit_per_person as depositPerPerson "
            + "from shared_table st join restaurant r on r.restaurant_id = st.restaurant_id "
            + "where st.shared_table_id = :sharedTableId and st.deleted_at is null",
            nativeQuery = true)
    Optional<TableInfoProjection> findTableInfo(@Param("sharedTableId") Long sharedTableId);
}

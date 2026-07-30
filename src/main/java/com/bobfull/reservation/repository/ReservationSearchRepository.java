package com.bobfull.reservation.repository;

import com.bobfull.reservation.dto.ReservationSearchRequest;
import com.bobfull.reservation.dto.ReservationSearchResult;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReservationSearchRepository {

    Page<ReservationSearchResult> searchRecruitingReservations(
            ReservationSearchRequest request,
            Instant now,
            Pageable pageable
    );
}

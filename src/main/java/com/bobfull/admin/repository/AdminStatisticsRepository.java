package com.bobfull.admin.repository;

import com.bobfull.admin.dto.AdminMemberNoShowRateResult;
import com.bobfull.admin.dto.AdminRestaurantStatisticsResult;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminStatisticsRepository {

    Page<AdminRestaurantStatisticsResult> aggregateRestaurantStatistics(Instant startAt, Instant endAt, Pageable pageable);

    Page<AdminMemberNoShowRateResult> aggregateMemberNoShowRates(Pageable pageable);
}

package com.bobfull.admin.repository;

import com.bobfull.admin.dto.AdminNoShowResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminNoShowRepository {

    Page<AdminNoShowResult> searchNoShows(Long memberId, Long restaurantId, Pageable pageable);
}

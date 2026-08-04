package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.NoShowHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoShowHistoryRepository extends JpaRepository<NoShowHistory, Long> {
}

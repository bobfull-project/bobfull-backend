package com.bobfull.sharedtable.repository;

import com.bobfull.sharedtable.entity.SharedTable;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedTableRepository extends JpaRepository<SharedTable, Long> {

    Page<SharedTable> findAllByRestaurantIdAndDeletedAtIsNull(Long restaurantId, Pageable pageable);

    Optional<SharedTable> findByIdAndDeletedAtIsNull(Long id);
}

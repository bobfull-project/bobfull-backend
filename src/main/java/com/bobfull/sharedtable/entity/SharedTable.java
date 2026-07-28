package com.bobfull.sharedtable.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 식당의 합석 정원 단위 테이블이다(docs/ERD.md 4.3).
 */
@Entity
@Table(name = "shared_table")
public class SharedTable extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shared_table_id")
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharedTableStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SharedTable() {
    }

    private SharedTable(Long restaurantId, Integer capacity) {
        this.restaurantId = restaurantId;
        this.capacity = capacity;
        this.status = SharedTableStatus.ACTIVE;
    }

    public static SharedTable create(Long restaurantId, Integer capacity) {
        return new SharedTable(restaurantId, capacity);
    }

    public void updateCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public void softDelete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public SharedTableStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

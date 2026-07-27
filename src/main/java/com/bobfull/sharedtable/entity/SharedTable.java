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
 * 식당에 소속되는 합석 정원 단위 테이블이다.
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
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharedTableStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SharedTable() {
    }

    private SharedTable(Long restaurantId, int capacity) {
        this.restaurantId = restaurantId;
        this.capacity = capacity;
        this.status = SharedTableStatus.ACTIVE;
    }

    public static SharedTable create(Long restaurantId, int capacity) {
        return new SharedTable(restaurantId, capacity);
    }

    public void updateCapacity(int capacity) {
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

    public int getCapacity() {
        return capacity;
    }

    public SharedTableStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

package com.bobfull.sharedtable.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import com.bobfull.restaurant.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharedTableStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SharedTable() {
    }

    private SharedTable(Restaurant restaurant, int capacity) {
        this.restaurant = restaurant;
        this.capacity = capacity;
        this.status = SharedTableStatus.ACTIVE;
    }

    public static SharedTable create(Restaurant restaurant, int capacity) {
        return new SharedTable(restaurant, capacity);
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

    public Restaurant getRestaurant() {
        return restaurant;
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

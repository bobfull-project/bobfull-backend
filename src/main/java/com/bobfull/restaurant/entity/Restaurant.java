package com.bobfull.restaurant.entity;

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
 * #31 병합 전 #32의 소유권 검증을 위해 둔 최소 Restaurant 엔티티다.
 * 식당 관리 API 전체 계약을 대체하지 않으며, #31 병합 후 실제 계약에 맞춰 재정리해야 한다.
 */
@Entity
@Table(name = "restaurant")
public class Restaurant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restaurant_id")
    private Long id;

    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RestaurantStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Restaurant() {
    }

    private Restaurant(Long ownerMemberId) {
        this.ownerMemberId = ownerMemberId;
        this.status = RestaurantStatus.ACTIVE;
    }

    public static Restaurant createTemporary(Long ownerMemberId) {
        return new Restaurant(ownerMemberId);
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public RestaurantStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}

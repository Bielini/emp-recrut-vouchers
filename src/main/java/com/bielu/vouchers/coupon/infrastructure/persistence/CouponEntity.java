package com.bielu.vouchers.coupon.infrastructure.persistence;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA mapping of the coupon table. Kept out of the domain so the domain stays free of JPA.
 * No setters: the usage counter is changed only by an atomic SQL UPDATE, never via dirty checking.
 */
@Entity
@Table(name = "coupon")
class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, updatable = false, length = 32)
    private String code;

    @Column(name = "max_uses", nullable = false, updatable = false)
    private int maxUses;

    @Column(name = "current_uses", nullable = false)
    private int currentUses;

    @Column(name = "country_code", nullable = false, updatable = false, length = 2)
    private String countryCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CouponEntity() {
        // for JPA
    }

    CouponEntity(CouponCode code, int maxUses, CountryCode country, Instant createdAt) {
        this.code = code.value();
        this.maxUses = maxUses;
        this.currentUses = 0;
        this.countryCode = country.value();
        this.createdAt = createdAt;
    }

    Coupon toDomain() {
        return new Coupon(id, new CouponCode(code), maxUses, currentUses, new CountryCode(countryCode), createdAt);
    }
}

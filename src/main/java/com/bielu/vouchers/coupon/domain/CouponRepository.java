package com.bielu.vouchers.coupon.domain;

import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for coupons. Uniqueness and usage limits are guaranteed by the database,
 * never by a "check, then write" sequence in Java.
 */
public interface CouponRepository {

    /**
     * @throws CouponAlreadyExistsException when a coupon with the same (normalized) code exists
     */
    Coupon create(CouponCode code, int maxUses, CountryCode country, Instant createdAt);

    Optional<Coupon> findByCode(CouponCode code);
}

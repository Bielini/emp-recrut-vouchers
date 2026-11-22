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

    /**
     * Atomically registers one use of the coupon by the given user (BR-2, BR-4).
     * Safe under concurrent calls from many threads and many application instances.
     */
    RedemptionOutcome redeem(long couponId, String userId, Instant redeemedAt);

    /**
     * Atomically reverses the user's active redemption and releases one use of the coupon (BR-6).
     * Safe under concurrent calls: a redemption is reversed and the counter decremented at most once.
     *
     * @return {@code false} when the user has no active redemption of the coupon
     */
    boolean reverseRedemption(long couponId, String userId, Instant reversedAt);
}

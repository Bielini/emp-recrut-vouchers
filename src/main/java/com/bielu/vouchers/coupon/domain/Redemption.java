package com.bielu.vouchers.coupon.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A successful use of a coupon, carrying the normalized coupon code and user id that were actually stored.
 */
public record Redemption(CouponCode couponCode, String userId, Instant redeemedAt) {

    public Redemption {
        Objects.requireNonNull(couponCode, "couponCode");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(redeemedAt, "redeemedAt");
    }
}

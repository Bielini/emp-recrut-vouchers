package com.bielu.vouchers.coupon.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Snapshot of a coupon's state. The usage counter is never mutated here –
 * concurrent redemptions are coordinated atomically by the database (see JpaCouponRepository).
 */
public record Coupon(
        Long id,
        CouponCode code,
        int maxUses,
        int currentUses,
        CountryCode country,
        Instant createdAt
) {

    public Coupon {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(createdAt, "createdAt");
        if (maxUses <= 0) {
            throw new IllegalArgumentException("maxUses must be positive, was " + maxUses);
        }
        if (currentUses < 0 || currentUses > maxUses) {
            throw new IllegalArgumentException(
                    "currentUses must be between 0 and maxUses (" + maxUses + "), was " + currentUses);
        }
    }

    public boolean isAvailableIn(CountryCode clientCountry) {
        return country.equals(clientCountry);
    }
}

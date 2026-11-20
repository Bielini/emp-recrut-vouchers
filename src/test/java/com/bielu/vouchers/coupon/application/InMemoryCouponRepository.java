package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test double for fast service tests. Concurrency guarantees are NOT emulated here –
 * those are verified against real PostgreSQL in the *IT tests.
 */
class InMemoryCouponRepository implements CouponRepository {

    private final Map<CouponCode, Coupon> coupons = new HashMap<>();
    private long nextId = 1;

    @Override
    public Coupon create(CouponCode code, int maxUses, CountryCode country, Instant createdAt) {
        if (coupons.containsKey(code)) {
            throw new CouponAlreadyExistsException();
        }
        Coupon coupon = new Coupon(nextId++, code, maxUses, 0, country, createdAt);
        coupons.put(code, coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return Optional.ofNullable(coupons.get(code));
    }
}

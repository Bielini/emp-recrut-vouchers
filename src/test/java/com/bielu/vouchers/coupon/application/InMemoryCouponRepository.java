package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.RedemptionOutcome;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Test double for fast service tests. Concurrency guarantees are NOT emulated here –
 * those are verified against real PostgreSQL in the *IT tests.
 */
class InMemoryCouponRepository implements CouponRepository {

    private final Map<CouponCode, Coupon> coupons = new HashMap<>();
    private final Set<String> redemptions = new HashSet<>();
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

    @Override
    public RedemptionOutcome redeem(long couponId, String userId, Instant redeemedAt) {
        Coupon coupon = coupons.values().stream()
                .filter(c -> c.id() == couponId)
                .findFirst()
                .orElseThrow();
        if (redemptions.contains(couponId + ":" + userId)) {
            return RedemptionOutcome.ALREADY_REDEEMED;
        }
        if (coupon.currentUses() >= coupon.maxUses()) {
            return RedemptionOutcome.EXHAUSTED;
        }
        redemptions.add(couponId + ":" + userId);
        coupons.put(coupon.code(), new Coupon(coupon.id(), coupon.code(), coupon.maxUses(),
                coupon.currentUses() + 1, coupon.country(), coupon.createdAt()));
        return RedemptionOutcome.REDEEMED;
    }
}

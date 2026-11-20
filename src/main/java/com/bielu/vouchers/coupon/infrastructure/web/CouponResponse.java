package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.domain.Coupon;

import java.time.Instant;

record CouponResponse(String code, int maxUses, int currentUses, String countryCode, Instant createdAt) {

    static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.code().value(),
                coupon.maxUses(),
                coupon.currentUses(),
                coupon.country().value(),
                coupon.createdAt());
    }
}

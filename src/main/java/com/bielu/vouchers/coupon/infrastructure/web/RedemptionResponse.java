package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.domain.Redemption;

import java.time.Instant;

record RedemptionResponse(String couponCode, String userId, Instant redeemedAt) {

    static RedemptionResponse from(Redemption redemption) {
        return new RedemptionResponse(redemption.couponCode().value(), redemption.userId(), redemption.redeemedAt());
    }
}

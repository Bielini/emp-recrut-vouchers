package com.bielu.vouchers.coupon.infrastructure.web;

import java.time.Instant;

record RedemptionResponse(String couponCode, String userId, Instant redeemedAt) {
}

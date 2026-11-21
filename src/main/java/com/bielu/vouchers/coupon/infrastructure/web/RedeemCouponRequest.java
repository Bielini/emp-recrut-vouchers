package com.bielu.vouchers.coupon.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code userId} is taken from the request as the task requires; in production it would come from an auth token.
 */
record RedeemCouponRequest(@NotBlank @Size(max = 64) String userId) {
}

package com.bielu.vouchers.coupon.application;

/**
 * Raw input for coupon creation; normalization and validation happen in the domain value objects.
 */
public record CreateCouponCommand(String code, int maxUses, String countryCode) {
}

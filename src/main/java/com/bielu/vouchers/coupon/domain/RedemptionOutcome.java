package com.bielu.vouchers.coupon.domain;

/**
 * Result of an attempt to redeem a coupon. Refusals are regular business outcomes, not errors,
 * so the persistence layer reports them as values instead of throwing.
 */
public enum RedemptionOutcome {
    REDEEMED,
    ALREADY_REDEEMED,
    EXHAUSTED
}

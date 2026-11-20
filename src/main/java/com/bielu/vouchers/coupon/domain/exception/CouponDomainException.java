package com.bielu.vouchers.coupon.domain.exception;

/**
 * Base type for business rule violations in the coupon domain.
 * Unchecked on purpose: callers cannot recover locally, the web layer maps them to HTTP responses.
 */
public abstract class CouponDomainException extends RuntimeException {

    protected CouponDomainException(String message) {
        super(message);
    }
}

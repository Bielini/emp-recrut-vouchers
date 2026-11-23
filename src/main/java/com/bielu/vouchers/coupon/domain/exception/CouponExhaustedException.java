package com.bielu.vouchers.coupon.domain.exception;

public final class CouponExhaustedException extends CouponDomainException {

    public CouponExhaustedException() {
        super("Coupon usage limit has been reached");
    }
}

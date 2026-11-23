package com.bielu.vouchers.coupon.domain.exception;

public final class CouponAlreadyExistsException extends CouponDomainException {

    public CouponAlreadyExistsException() {
        super("Coupon with this code already exists");
    }
}

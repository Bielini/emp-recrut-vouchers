package com.bielu.vouchers.coupon.domain.exception;

public class CouponNotFoundException extends CouponDomainException {

    public CouponNotFoundException() {
        super("Coupon with this code does not exist");
    }
}

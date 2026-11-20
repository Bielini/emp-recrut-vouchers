package com.bielu.vouchers.coupon.domain.exception;

public class InvalidCouponCodeException extends CouponDomainException {

    public InvalidCouponCodeException() {
        super("Coupon code must be 3-32 characters long and contain only letters A-Z, digits, '-' or '_'");
    }
}

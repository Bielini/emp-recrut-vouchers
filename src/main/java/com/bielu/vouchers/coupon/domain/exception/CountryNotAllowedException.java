package com.bielu.vouchers.coupon.domain.exception;

public final class CountryNotAllowedException extends CouponDomainException {

    public CountryNotAllowedException() {
        super("Coupon cannot be used from your country");
    }
}

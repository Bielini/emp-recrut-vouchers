package com.bielu.vouchers.coupon.domain.exception;

public final class InvalidCountryCodeException extends CouponDomainException {

    public InvalidCountryCodeException() {
        super("Country code must be a valid ISO 3166-1 alpha-2 code, e.g. PL");
    }
}

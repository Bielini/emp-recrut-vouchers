package com.bielu.vouchers.coupon.domain.exception;

/**
 * Base type for business rule violations in the coupon domain.
 * Unchecked on purpose: callers cannot recover locally, the web layer maps them to HTTP responses.
 * Sealed so the web layer's mapping is checked for exhaustiveness by the compiler (BR-5):
 * a new refusal cannot be added without choosing its HTTP status and errorCode.
 */
public abstract sealed class CouponDomainException extends RuntimeException permits
        CountryNotAllowedException,
        CouponAlreadyExistsException,
        CouponAlreadyRedeemedException,
        CouponExhaustedException,
        CouponNotFoundException,
        GeolocationUnavailableException,
        InvalidCountryCodeException,
        InvalidCouponCodeException,
        RedemptionNotFoundException {

    protected CouponDomainException(String message) {
        super(message);
    }

    protected CouponDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

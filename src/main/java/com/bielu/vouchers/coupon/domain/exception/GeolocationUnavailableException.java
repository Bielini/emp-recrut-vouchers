package com.bielu.vouchers.coupon.domain.exception;

public final class GeolocationUnavailableException extends CouponDomainException {

    public GeolocationUnavailableException(Throwable cause) {
        super("Client location cannot be verified right now, please try again later", cause);
    }
}

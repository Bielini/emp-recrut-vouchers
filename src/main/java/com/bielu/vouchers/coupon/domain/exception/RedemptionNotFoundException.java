package com.bielu.vouchers.coupon.domain.exception;

public final class RedemptionNotFoundException extends CouponDomainException {

    public RedemptionNotFoundException() {
        super("This user has no active redemption of the coupon");
    }
}

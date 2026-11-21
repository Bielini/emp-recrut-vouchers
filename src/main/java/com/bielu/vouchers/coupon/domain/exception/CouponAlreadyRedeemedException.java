package com.bielu.vouchers.coupon.domain.exception;

public class CouponAlreadyRedeemedException extends CouponDomainException {

    public CouponAlreadyRedeemedException() {
        super("This user has already used the coupon");
    }
}

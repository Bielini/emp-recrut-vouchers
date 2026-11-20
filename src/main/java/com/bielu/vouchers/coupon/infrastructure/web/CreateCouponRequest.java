package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.application.CreateCouponCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Shape checks only (presence, ranges). Code and country format are validated by the domain
 * value objects, so each rule lives in exactly one place.
 */
record CreateCouponRequest(
        @NotBlank String code,
        @NotNull @Min(1) @Max(1_000_000) Integer maxUses,
        @NotBlank String countryCode
) {

    CreateCouponCommand toCommand() {
        return new CreateCouponCommand(code, maxUses, countryCode);
    }
}

package com.bielu.vouchers.coupon.domain;

import com.bielu.vouchers.coupon.domain.exception.InvalidCouponCodeException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Case-insensitive coupon code. Always holds the normalized (trimmed, upper-case) form,
 * so equality and database uniqueness work on a single canonical representation.
 */
public record CouponCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9_-]{3,32}");

    public CouponCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidCouponCodeException();
        }
    }

    public static CouponCode of(String raw) {
        if (raw == null) {
            throw new InvalidCouponCodeException();
        }
        // Locale.ROOT: the default locale must not change the result (e.g. Turkish "i" -> "İ").
        return new CouponCode(raw.strip().toUpperCase(Locale.ROOT));
    }
}

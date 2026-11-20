package com.bielu.vouchers.coupon.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final CouponCode CODE = CouponCode.of("WIOSNA");
    private static final CountryCode POLAND = CountryCode.of("PL");
    private static final Instant CREATED_AT = Instant.parse("2026-11-28T10:00:00Z");

    @Test
    void rejectsNonPositiveMaxUses() {
        assertThatThrownBy(() -> coupon(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxUses");
    }

    @Test
    void rejectsNegativeCurrentUses() {
        assertThatThrownBy(() -> coupon(10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentUses");
    }

    @Test
    void rejectsCurrentUsesAboveMaxUses() {
        assertThatThrownBy(() -> coupon(10, 11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentUses");
    }

    @Test
    void acceptsFullyUsedCoupon() {
        assertThat(coupon(10, 10).currentUses()).isEqualTo(10);
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> new Coupon(1L, null, 10, 0, POLAND, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Coupon(1L, CODE, 10, 0, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Coupon(1L, CODE, 10, 0, POLAND, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isAvailableOnlyInItsCountry() {
        Coupon coupon = coupon(10, 0);

        assertThat(coupon.isAvailableIn(POLAND)).isTrue();
        assertThat(coupon.isAvailableIn(CountryCode.of("DE"))).isFalse();
    }

    private static Coupon coupon(int maxUses, int currentUses) {
        return new Coupon(1L, CODE, maxUses, currentUses, POLAND, CREATED_AT);
    }
}

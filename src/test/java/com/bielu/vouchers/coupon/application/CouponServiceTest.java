package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.exception.CouponNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    private final InMemoryCouponRepository repository = new InMemoryCouponRepository();
    private final CouponService service = new CouponService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsCouponWithCurrentTimeAndZeroUses() {
        Coupon coupon = service.create(new CreateCouponCommand(" wiosna ", 10, "pl"));

        assertThat(coupon.code()).isEqualTo(CouponCode.of("WIOSNA"));
        assertThat(coupon.maxUses()).isEqualTo(10);
        assertThat(coupon.currentUses()).isZero();
        assertThat(coupon.country()).isEqualTo(CountryCode.of("PL"));
        assertThat(coupon.createdAt()).isEqualTo(NOW);
        assertThat(repository.findByCode(CouponCode.of("WIOSNA"))).contains(coupon);
    }

    @Test
    void findsCouponRegardlessOfCodeCase() {
        Coupon created = service.create(new CreateCouponCommand("WIOSNA", 10, "PL"));

        assertThat(service.getByCode("wiosna")).isEqualTo(created);
    }

    @Test
    void throwsNotFoundForUnknownCode() {
        assertThatThrownBy(() -> service.getByCode("MISSING"))
                .isInstanceOf(CouponNotFoundException.class);
    }
}

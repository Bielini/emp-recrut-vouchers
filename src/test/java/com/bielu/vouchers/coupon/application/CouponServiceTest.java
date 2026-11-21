package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.exception.CountryNotAllowedException;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyRedeemedException;
import com.bielu.vouchers.coupon.domain.exception.CouponExhaustedException;
import com.bielu.vouchers.coupon.domain.exception.CouponNotFoundException;
import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");
    private static final InetAddress CLIENT = InetAddress.ofLiteral("83.0.0.1");

    private final InMemoryCouponRepository repository = new InMemoryCouponRepository();
    private final StubCountryResolver countryResolver = new StubCountryResolver();
    private final CouponService service =
            new CouponService(repository, countryResolver, Clock.fixed(NOW, ZoneOffset.UTC));

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

    @Test
    void redeemsCouponFromAllowedCountry() {
        service.create(new CreateCouponCommand("WIOSNA", 10, "PL"));
        countryResolver.answers("PL");

        Instant redeemedAt = service.redeem(new RedeemCouponCommand("wiosna", "user-1", CLIENT));

        assertThat(redeemedAt).isEqualTo(NOW);
        assertThat(service.getByCode("WIOSNA").currentUses()).isEqualTo(1);
    }

    @Test
    void throwsNotFoundWithoutCallingGeolocation() {
        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("MISSING", "user-1", CLIENT)))
                .isInstanceOf(CouponNotFoundException.class);
        assertThat(countryResolver.calls()).isZero();
    }

    @Test
    void throwsCountryNotAllowedForOtherCountry() {
        service.create(new CreateCouponCommand("WIOSNA", 10, "PL"));
        countryResolver.answers("DE");

        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("WIOSNA", "user-1", CLIENT)))
                .isInstanceOf(CountryNotAllowedException.class);
        assertThat(service.getByCode("WIOSNA").currentUses()).isZero();
    }

    @Test
    void throwsCountryNotAllowedWhenCountryUnknown() {
        service.create(new CreateCouponCommand("WIOSNA", 10, "PL"));
        countryResolver.answersUnknown();

        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("WIOSNA", "user-1", CLIENT)))
                .isInstanceOf(CountryNotAllowedException.class);
    }

    @Test
    void propagatesGeolocationUnavailable() {
        service.create(new CreateCouponCommand("WIOSNA", 10, "PL"));
        countryResolver.isUnavailable();

        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("WIOSNA", "user-1", CLIENT)))
                .isInstanceOf(GeolocationUnavailableException.class);
        assertThat(service.getByCode("WIOSNA").currentUses()).isZero();
    }

    @Test
    void mapsExhaustedOutcome() {
        service.create(new CreateCouponCommand("WIOSNA", 1, "PL"));
        countryResolver.answers("PL");
        service.redeem(new RedeemCouponCommand("WIOSNA", "user-1", CLIENT));

        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("WIOSNA", "user-2", CLIENT)))
                .isInstanceOf(CouponExhaustedException.class);
    }

    @Test
    void mapsAlreadyRedeemedOutcome() {
        service.create(new CreateCouponCommand("WIOSNA", 10, "PL"));
        countryResolver.answers("PL");
        service.redeem(new RedeemCouponCommand("WIOSNA", "user-1", CLIENT));

        assertThatThrownBy(() -> service.redeem(new RedeemCouponCommand("WIOSNA", "user-1", CLIENT)))
                .isInstanceOf(CouponAlreadyRedeemedException.class);
    }
}

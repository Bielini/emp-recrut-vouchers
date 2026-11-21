package com.bielu.vouchers.coupon.infrastructure.geo;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Local runs only: inside Docker the client address is private, so a real lookup would always fail.
 */
class FixedCountryResolver implements CountryResolver {

    private final CountryCode country;

    FixedCountryResolver(CountryCode country) {
        this.country = country;
    }

    @Override
    public Optional<CountryCode> resolve(InetAddress address) {
        return Optional.of(country);
    }
}

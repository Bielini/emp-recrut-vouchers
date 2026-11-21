package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;

import java.net.InetAddress;
import java.util.Optional;

/** Returns a preset answer and counts calls, so tests can assert whether geolocation was consulted at all. */
class StubCountryResolver implements CountryResolver {

    private Optional<CountryCode> answer = Optional.empty();
    private boolean unavailable;
    private int calls;

    void answers(String countryCode) {
        this.answer = Optional.of(CountryCode.of(countryCode));
        this.unavailable = false;
    }

    void answersUnknown() {
        this.answer = Optional.empty();
        this.unavailable = false;
    }

    void isUnavailable() {
        this.unavailable = true;
    }

    int calls() {
        return calls;
    }

    @Override
    public Optional<CountryCode> resolve(InetAddress address) {
        calls++;
        if (unavailable) {
            throw new GeolocationUnavailableException(new RuntimeException("provider down"));
        }
        return answer;
    }
}

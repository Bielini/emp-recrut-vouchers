package com.bielu.vouchers.coupon.domain;

import com.bielu.vouchers.coupon.domain.exception.InvalidCountryCodeException;

import java.util.Locale;
import java.util.Set;

/**
 * ISO 3166-1 alpha-2 country code in canonical upper-case form.
 */
public record CountryCode(String value) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public CountryCode {
        if (value == null || !ISO_COUNTRIES.contains(value)) {
            throw new InvalidCountryCodeException();
        }
    }

    public static CountryCode of(String raw) {
        if (raw == null) {
            throw new InvalidCountryCodeException();
        }
        return new CountryCode(raw.strip().toUpperCase(Locale.ROOT));
    }
}

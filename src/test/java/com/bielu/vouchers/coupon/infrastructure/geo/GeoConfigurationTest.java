package com.bielu.vouchers.coupon.infrastructure.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoConfigurationTest {

    private final GeoConfiguration configuration = new GeoConfiguration();

    @Test
    void fixedProviderAnswersConfiguredCountry() {
        assertThat(configuration.countryResolver(fixed("pl")).resolve(InetAddress.ofLiteral("10.0.0.1")))
                .hasValueSatisfying(country -> assertThat(country.value()).isEqualTo("PL"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void fixedProviderWithoutCountryFailsWithExplicitMessage(String fixedCountry) {
        assertThatThrownBy(() -> configuration.countryResolver(fixed(fixedCountry)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEO_FIXED_COUNTRY");
    }

    private static GeoProperties fixed(String fixedCountry) {
        return new GeoProperties(GeoProperties.Provider.FIXED, "http://unused", Duration.ofMillis(500),
                Duration.ofSeconds(1), Duration.ofHours(1), 100, fixedCountry);
    }
}

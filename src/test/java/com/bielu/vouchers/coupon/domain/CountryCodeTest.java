package com.bielu.vouchers.coupon.domain;

import com.bielu.vouchers.coupon.domain.exception.InvalidCountryCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryCodeTest {

    @Test
    void normalizesToUpperCase() {
        assertThat(CountryCode.of("pl").value()).isEqualTo("PL");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(CountryCode.of(" de ").value()).isEqualTo("DE");
    }

    @Test
    void codesDifferingOnlyInCaseAreEqual() {
        assertThat(CountryCode.of("pl")).isEqualTo(CountryCode.of("PL"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"XX", "POL", "P", "P1"})
    void rejectsCodesOutsideIso3166Alpha2(String raw) {
        assertThatThrownBy(() -> CountryCode.of(raw))
                .isInstanceOf(InvalidCountryCodeException.class);
    }
}

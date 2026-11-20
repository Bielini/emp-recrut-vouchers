package com.bielu.vouchers.coupon.domain;

import com.bielu.vouchers.coupon.domain.exception.InvalidCouponCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponCodeTest {

    @Test
    void normalizesToUpperCase() {
        assertThat(CouponCode.of("wiosna").value()).isEqualTo("WIOSNA");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(CouponCode.of("  Wiosna ").value()).isEqualTo("WIOSNA");
    }

    @Test
    void codesDifferingOnlyInCaseAreEqual() {
        assertThat(CouponCode.of("Wiosna")).isEqualTo(CouponCode.of("WIOSNA"));
    }

    @Test
    void acceptsDigitsHyphenAndUnderscore() {
        assertThat(CouponCode.of("black-friday_2026").value()).isEqualTo("BLACK-FRIDAY_2026");
    }

    @Test
    void normalizationDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            // In Turkish, "i".toUpperCase() is "İ" (dotted capital I) – a locale-sensitive bug would break equality.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(CouponCode.of("wiosna").value()).isEqualTo("WIOSNA");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "AB",                                   // too short (min 3)
            "A23456789012345678901234567890123",    // too long (max 32)
            "WIOSNA 2026",                          // inner whitespace
            "ZNIŻKA",                               // non-ASCII letter
            "PROMO!"                                // illegal character
    })
    void rejectsInvalidCodes(String raw) {
        assertThatThrownBy(() -> CouponCode.of(raw))
                .isInstanceOf(InvalidCouponCodeException.class);
    }

    @Test
    void acceptsBoundaryLengths() {
        assertThat(CouponCode.of("ABC").value()).hasSize(3);
        assertThat(CouponCode.of("A".repeat(32)).value()).hasSize(32);
    }
}

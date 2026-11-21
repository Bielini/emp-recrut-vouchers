package com.bielu.vouchers.coupon.infrastructure.geo;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingCountryResolverTest {

    private static final InetAddress ADDRESS = InetAddress.ofLiteral("83.0.0.1");

    @Test
    void cachesSuccessfulLookups() {
        CountingResolver delegate = new CountingResolver(Optional.of(CountryCode.of("PL")));
        CachingCountryResolver resolver = new CachingCountryResolver(delegate, Duration.ofHours(1), 100);

        assertThat(resolver.resolve(ADDRESS)).contains(CountryCode.of("PL"));
        assertThat(resolver.resolve(ADDRESS)).contains(CountryCode.of("PL"));
        assertThat(delegate.calls.get()).isEqualTo(1);
    }

    @Test
    void cachesUnknownCountry() {
        CountingResolver delegate = new CountingResolver(Optional.empty());
        CachingCountryResolver resolver = new CachingCountryResolver(delegate, Duration.ofHours(1), 100);

        assertThat(resolver.resolve(ADDRESS)).isEmpty();
        assertThat(resolver.resolve(ADDRESS)).isEmpty();
        assertThat(delegate.calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotCacheFailures() {
        CountingResolver delegate = new CountingResolver(null);
        CachingCountryResolver resolver = new CachingCountryResolver(delegate, Duration.ofHours(1), 100);

        assertThatThrownBy(() -> resolver.resolve(ADDRESS)).isInstanceOf(GeolocationUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolve(ADDRESS)).isInstanceOf(GeolocationUnavailableException.class);
        assertThat(delegate.calls.get()).isEqualTo(2);
    }

    /** Returns the given result, or throws when it is {@code null}. */
    private static final class CountingResolver implements CountryResolver {

        private final Optional<CountryCode> result;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingResolver(Optional<CountryCode> result) {
            this.result = result;
        }

        @Override
        public Optional<CountryCode> resolve(InetAddress address) {
            calls.incrementAndGet();
            if (result.isEmpty()) {
                throw new GeolocationUnavailableException(new RuntimeException("provider down"));
            }
            return result;
        }
    }
}

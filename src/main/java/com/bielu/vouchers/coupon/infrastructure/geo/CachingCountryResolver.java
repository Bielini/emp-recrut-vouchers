package com.bielu.vouchers.coupon.infrastructure.geo;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;

/**
 * Caches lookups in memory: during a peak the same clients retry, and the external API has rate limits.
 * Failures are not cached, so the next request tries the provider again.
 */
class CachingCountryResolver implements CountryResolver {

    private final CountryResolver delegate;
    private final Cache<InetAddress, Optional<CountryCode>> cache;

    CachingCountryResolver(CountryResolver delegate, Duration ttl, long maxSize) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public Optional<CountryCode> resolve(InetAddress address) {
        // An exception thrown by the delegate propagates and leaves no entry behind.
        return cache.get(address, delegate::resolve);
    }
}

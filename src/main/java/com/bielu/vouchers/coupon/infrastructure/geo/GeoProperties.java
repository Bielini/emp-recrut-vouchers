package com.bielu.vouchers.coupon.infrastructure.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("geo")
record GeoProperties(
        @DefaultValue("IP_API") Provider provider,
        @DefaultValue("http://ip-api.com") String baseUrl,
        @DefaultValue("500ms") Duration connectTimeout,
        @DefaultValue("1s") Duration readTimeout,
        @DefaultValue("1h") Duration cacheTtl,
        @DefaultValue("100000") long cacheMaxSize,
        String fixedCountry
) {

    enum Provider {
        IP_API,
        FIXED
    }
}

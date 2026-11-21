package com.bielu.vouchers.coupon.infrastructure.geo;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeoProperties.class)
class GeoConfiguration {

    @Bean
    CountryResolver countryResolver(GeoProperties properties) {
        return switch (properties.provider()) {
            case IP_API -> new CachingCountryResolver(
                    new IpApiCountryResolver(IpApiCountryResolver.restClient(
                            RestClient.builder(), properties.baseUrl(),
                            properties.connectTimeout(), properties.readTimeout())),
                    properties.cacheTtl(),
                    properties.cacheMaxSize());
            case FIXED -> new FixedCountryResolver(CountryCode.of(properties.fixedCountry()));
        };
    }
}

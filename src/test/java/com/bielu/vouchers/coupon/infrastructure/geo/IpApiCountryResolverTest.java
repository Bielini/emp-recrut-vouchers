package com.bielu.vouchers.coupon.infrastructure.geo;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpApiCountryResolverTest {

    @RegisterExtension
    static WireMockExtension geoApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private IpApiCountryResolver resolver;

    @BeforeEach
    void setUp() {
        RestClient restClient = IpApiCountryResolver.restClient(
                RestClient.builder(), geoApi.baseUrl(), Duration.ofMillis(500), Duration.ofSeconds(1));
        resolver = new IpApiCountryResolver(restClient);
    }

    @Test
    void resolvesCountryCode() {
        geoApi.stubFor(get(urlPathEqualTo("/json/83.0.0.1"))
                .willReturn(okJson("{\"status\":\"success\",\"countryCode\":\"PL\"}")));

        assertThat(resolver.resolve(InetAddress.ofLiteral("83.0.0.1"))).contains(CountryCode.of("PL"));
    }

    @Test
    void resolvesIpv6Address() {
        InetAddress address = InetAddress.ofLiteral("2a02:a311::1");
        geoApi.stubFor(get(urlPathEqualTo("/json/" + address.getHostAddress()))
                .willReturn(okJson("{\"status\":\"success\",\"countryCode\":\"DE\"}")));

        assertThat(resolver.resolve(address)).contains(CountryCode.of("DE"));
    }

    @Test
    void returnsEmptyForPrivateRange() {
        geoApi.stubFor(get(urlPathEqualTo("/json/10.0.0.1"))
                .willReturn(okJson("{\"status\":\"fail\",\"message\":\"private range\"}")));

        assertThat(resolver.resolve(InetAddress.ofLiteral("10.0.0.1"))).isEmpty();
    }

    @Test
    void returnsEmptyForCountryOutsideIso3166() {
        // e.g. Kosovo "XK" is user-assigned, not part of ISO 3166-1: no coupon can be issued for it.
        geoApi.stubFor(get(urlPathEqualTo("/json/83.0.0.1"))
                .willReturn(okJson("{\"status\":\"success\",\"countryCode\":\"XK\"}")));

        assertThat(resolver.resolve(InetAddress.ofLiteral("83.0.0.1"))).isEmpty();
    }

    @Test
    void throwsUnavailableOnServerError() {
        geoApi.stubFor(get(urlPathEqualTo("/json/83.0.0.1")).willReturn(aResponse().withStatus(503)));

        assertUnavailable();
    }

    @Test
    void throwsUnavailableOnRateLimit() {
        geoApi.stubFor(get(urlPathEqualTo("/json/83.0.0.1")).willReturn(aResponse().withStatus(429)));

        assertUnavailable();
    }

    @Test
    void throwsUnavailableOnMalformedBody() {
        geoApi.stubFor(get(urlPathEqualTo("/json/83.0.0.1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("<html>")));

        assertUnavailable();
    }

    @Test
    void throwsUnavailableOnTimeout() {
        geoApi.stubFor(get(urlPathEqualTo("/json/83.0.0.1"))
                .willReturn(okJson("{\"status\":\"success\",\"countryCode\":\"PL\"}").withFixedDelay(2000)));

        long start = System.nanoTime();
        assertUnavailable();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1500));
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> resolver.resolve(InetAddress.ofLiteral("83.0.0.1")))
                .isInstanceOf(GeolocationUnavailableException.class)
                .hasMessageNotContaining("83.0.0.1");
    }
}

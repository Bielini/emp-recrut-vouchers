package com.bielu.vouchers.coupon.infrastructure.geo;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;
import com.bielu.vouchers.coupon.domain.exception.InvalidCountryCodeException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

/**
 * Adapter for the ip-api.com JSON endpoint. Fail-closed: any transport, HTTP or parsing problem
 * becomes {@link GeolocationUnavailableException}, so a discount is never granted on an unverified location.
 */
class IpApiCountryResolver implements CountryResolver {

    private static final String SUCCESS = "success";

    private final RestClient restClient;

    IpApiCountryResolver(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient restClient(RestClient.Builder builder, String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<CountryCode> resolve(InetAddress address) {
        IpApiResponse response;
        try {
            response = restClient.get()
                    // Path built literally: template variables would encode IPv6 colons as %3A.
                    .uri(uri -> uri.path("/json/" + address.getHostAddress())
                            .queryParam("fields", "status,countryCode,message")
                            .build())
                    .retrieve()
                    .body(IpApiResponse.class);
        } catch (RestClientException e) {
            throw new GeolocationUnavailableException(e);
        }
        if (response == null || response.status() == null) {
            throw new GeolocationUnavailableException(new IllegalStateException("Empty geolocation response"));
        }
        if (!SUCCESS.equals(response.status())) {
            // e.g. "private range", "reserved range": the provider answered, the country is just unknown.
            return Optional.empty();
        }
        try {
            return Optional.of(CountryCode.of(response.countryCode()));
        } catch (InvalidCountryCodeException e) {
            return Optional.empty();
        }
    }

    record IpApiResponse(String status, String countryCode, String message) {
    }
}

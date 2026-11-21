package com.bielu.vouchers.coupon.domain;

import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Port for IP geolocation (BR-3). Adapters can be swapped (HTTP API, offline database) without touching the domain.
 */
public interface CountryResolver {

    /**
     * @return the client's country, or empty when it cannot be determined (e.g. private or reserved address)
     * @throws GeolocationUnavailableException when the provider cannot answer right now
     */
    Optional<CountryCode> resolve(InetAddress address);
}

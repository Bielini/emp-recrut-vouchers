package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.exception.CountryNotAllowedException;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyRedeemedException;
import com.bielu.vouchers.coupon.domain.exception.CouponExhaustedException;
import com.bielu.vouchers.coupon.domain.exception.CouponNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class CouponService {

    private final CouponRepository repository;
    private final CountryResolver countryResolver;
    private final Clock clock;

    public CouponService(CouponRepository repository, CountryResolver countryResolver, Clock clock) {
        this.repository = repository;
        this.countryResolver = countryResolver;
        this.clock = clock;
    }

    /**
     * No "does it exist?" pre-check: a duplicate is detected by the database unique constraint
     * and surfaces as {@link com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException}.
     */
    public Coupon create(CreateCouponCommand command) {
        return repository.create(
                CouponCode.of(command.code()),
                command.maxUses(),
                CountryCode.of(command.countryCode()),
                Instant.now(clock));
    }

    public Coupon getByCode(String code) {
        return repository.findByCode(CouponCode.of(code))
                .orElseThrow(CouponNotFoundException::new);
    }

    /**
     * Deliberately not {@code @Transactional}: the geolocation call is network I/O and must not hold
     * a pooled DB connection or row locks. Only {@link CouponRepository#redeem} runs in a (short) transaction.
     */
    public Instant redeem(RedeemCouponCommand command) {
        Coupon coupon = getByCode(command.code());

        // BR-3, fail-closed: an unknown country is a refusal, an unavailable provider propagates as 503.
        Optional<CountryCode> clientCountry = countryResolver.resolve(command.clientAddress());
        if (clientCountry.isEmpty() || !coupon.isAvailableIn(clientCountry.get())) {
            throw new CountryNotAllowedException();
        }

        Instant redeemedAt = Instant.now(clock);
        return switch (repository.redeem(coupon.id(), command.userId(), redeemedAt)) {
            case REDEEMED -> redeemedAt;
            case ALREADY_REDEEMED -> throw new CouponAlreadyRedeemedException();
            case EXHAUSTED -> throw new CouponExhaustedException();
        };
    }
}

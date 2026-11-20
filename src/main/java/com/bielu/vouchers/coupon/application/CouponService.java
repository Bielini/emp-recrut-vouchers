package com.bielu.vouchers.coupon.application;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.exception.CouponNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class CouponService {

    private final CouponRepository repository;
    private final Clock clock;

    public CouponService(CouponRepository repository, Clock clock) {
        this.repository = repository;
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
}

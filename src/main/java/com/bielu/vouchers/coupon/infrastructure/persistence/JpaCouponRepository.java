package com.bielu.vouchers.coupon.infrastructure.persistence;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
class JpaCouponRepository implements CouponRepository {

    static final String UNIQUE_CODE_CONSTRAINT = "uq_coupon_code";

    private final CouponJpaRepository jpa;

    JpaCouponRepository(CouponJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Coupon create(CouponCode code, int maxUses, CountryCode country, Instant createdAt) {
        try {
            // No "exists?" pre-check: two parallel requests would both pass it.
            // The unique constraint decides atomically; we only translate its verdict.
            return jpa.saveAndFlush(new CouponEntity(code, maxUses, country, createdAt)).toDomain();
        } catch (DataIntegrityViolationException e) {
            if (violates(e, UNIQUE_CODE_CONSTRAINT)) {
                throw new CouponAlreadyExistsException();
            }
            throw e;
        }
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return jpa.findByCode(code.value()).map(CouponEntity::toDomain);
    }

    private static boolean violates(Throwable e, String constraintName) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException cve
                    && constraintName.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}

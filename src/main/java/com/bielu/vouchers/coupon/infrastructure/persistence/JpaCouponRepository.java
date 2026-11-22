package com.bielu.vouchers.coupon.infrastructure.persistence;

import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.RedemptionOutcome;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Repository
class JpaCouponRepository implements CouponRepository {

    static final String UNIQUE_CODE_CONSTRAINT = "uq_coupon_code";

    private final CouponJpaRepository jpa;
    private final TransactionTemplate transaction;

    JpaCouponRepository(CouponJpaRepository jpa, TransactionTemplate transaction) {
        this.jpa = jpa;
        this.transaction = transaction;
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

    @Override
    public RedemptionOutcome redeem(long couponId, String userId, Instant redeemedAt) {
        // Order matters: the per-user row first, the hot coupon row last, so its lock is held
        // for the shortest possible time and every transaction locks in the same order (no deadlocks).
        return transaction.execute(status -> {
            if (jpa.insertRedemptionIfAbsent(couponId, userId, redeemedAt) == 0) {
                return RedemptionOutcome.ALREADY_REDEEMED;
            }
            if (jpa.incrementUsageIfBelowLimit(couponId) == 0) {
                // Limit reached: undo the redemption row inserted above.
                status.setRollbackOnly();
                return RedemptionOutcome.EXHAUSTED;
            }
            return RedemptionOutcome.REDEEMED;
        });
    }

    @Override
    public boolean reverseRedemption(long couponId, String userId, Instant reversedAt) {
        // Same lock order as redeem(): the per-user row first, the hot coupon row last (no deadlocks).
        return Boolean.TRUE.equals(transaction.execute(status -> {
            if (jpa.markRedemptionReversed(couponId, userId, reversedAt) == 0) {
                return false;
            }
            if (jpa.decrementUsage(couponId) == 0) {
                // Unreachable while counter == active redemptions; roll back rather than hide corruption.
                throw new IllegalStateException("Usage counter of coupon " + couponId + " is already zero");
            }
            return true;
        }));
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

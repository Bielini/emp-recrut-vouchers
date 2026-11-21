package com.bielu.vouchers.coupon.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {

    Optional<CouponEntity> findByCode(String code);

    /**
     * BR-4: the unique constraint decides whether this user already redeemed the coupon.
     * ON CONFLICT instead of catching the violation keeps the transaction usable.
     *
     * @return 1 when inserted, 0 when the user already redeemed the coupon
     */
    @Modifying
    @Query(value = """
            INSERT INTO coupon_redemption (coupon_id, user_id, redeemed_at)
            VALUES (:couponId, :userId, :redeemedAt)
            ON CONFLICT (coupon_id, user_id) DO NOTHING""", nativeQuery = true)
    int insertRedemptionIfAbsent(long couponId, String userId, Instant redeemedAt);

    /**
     * BR-2: check and increment in one statement. Under READ COMMITTED a waiting transaction
     * re-evaluates the WHERE clause on the newest row version once the lock is released,
     * so the limit cannot be exceeded without any retry logic.
     *
     * @return 1 when incremented, 0 when the limit is already reached
     */
    @Modifying
    @Query(value = """
            UPDATE coupon SET current_uses = current_uses + 1
            WHERE id = :couponId AND current_uses < max_uses""", nativeQuery = true)
    int incrementUsageIfBelowLimit(long couponId);
}

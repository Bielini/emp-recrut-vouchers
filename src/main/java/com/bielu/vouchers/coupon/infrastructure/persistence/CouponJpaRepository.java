package com.bielu.vouchers.coupon.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {

    Optional<CouponEntity> findByCode(String code);
}

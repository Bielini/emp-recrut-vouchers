package com.bielu.vouchers.coupon.infrastructure.persistence;

import com.bielu.vouchers.TestcontainersConfiguration;
import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaCouponRepositoryIT {

    // Microsecond precision: PostgreSQL TIMESTAMPTZ does not store nanoseconds.
    private static final Instant CREATED_AT = Instant.parse("2026-11-20T10:15:30.123456Z");
    private static final CountryCode PL = CountryCode.of("PL");

    @Autowired
    private CouponRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM coupon_redemption").update();
        jdbc.sql("DELETE FROM coupon").update();
    }

    @Test
    void createsAndFindsCoupon() {
        Coupon created = repository.create(CouponCode.of("WIOSNA"), 10, PL, CREATED_AT);

        assertThat(created.id()).isNotNull();
        assertThat(repository.findByCode(CouponCode.of("WIOSNA"))).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(created.id());
            assertThat(found.code()).isEqualTo(CouponCode.of("WIOSNA"));
            assertThat(found.maxUses()).isEqualTo(10);
            assertThat(found.currentUses()).isZero();
            assertThat(found.country()).isEqualTo(PL);
            assertThat(found.createdAt()).isEqualTo(CREATED_AT);
        });
    }

    @Test
    void returnsEmptyWhenCouponDoesNotExist() {
        assertThat(repository.findByCode(CouponCode.of("MISSING"))).isEmpty();
    }

    @Test
    void findsCouponRegardlessOfInputCase() {
        repository.create(CouponCode.of("WIOSNA"), 10, PL, CREATED_AT);

        assertThat(repository.findByCode(CouponCode.of("wiosna"))).isPresent();
    }

    @Test
    void rejectsDuplicateCodeIgnoringCase() {
        repository.create(CouponCode.of("WIOSNA"), 10, PL, CREATED_AT);

        assertThatThrownBy(() -> repository.create(CouponCode.of("Wiosna"), 5, PL, CREATED_AT))
                .isInstanceOf(CouponAlreadyExistsException.class);
    }

    @Test
    void databaseRejectsUsesAboveLimit() {
        repository.create(CouponCode.of("WIOSNA"), 1, PL, CREATED_AT);

        // Last line of defence for BR-2: even a buggy UPDATE cannot push the counter past max_uses.
        assertThatThrownBy(() -> jdbc.sql("UPDATE coupon SET current_uses = max_uses + 1").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonNormalizedCode() {
        // BR-1 enforced by the schema as well: a lower-case code inserted around the domain model is refused.
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO coupon (code, max_uses, country_code, created_at)
                        VALUES ('wiosna', 1, 'PL', now())""").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.bielu.vouchers.coupon.infrastructure.persistence;

import com.bielu.vouchers.TestcontainersConfiguration;
import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.CouponRepository;
import com.bielu.vouchers.coupon.domain.RedemptionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static com.bielu.vouchers.coupon.domain.RedemptionOutcome.ALREADY_REDEEMED;
import static com.bielu.vouchers.coupon.domain.RedemptionOutcome.EXHAUSTED;
import static com.bielu.vouchers.coupon.domain.RedemptionOutcome.REDEEMED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-2 under load: many parallel transactions race for the last uses of a coupon,
 * also while cancelled orders release uses at the same time (BR-6).
 * Runs against real PostgreSQL because the guarantee comes from its row locking semantics.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrentRedemptionIT {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
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
    void concurrentRedemptionsNeverExceedMaxUses() throws Exception {
        Coupon coupon = repository.create(CouponCode.of("BLACKFRIDAY"), 10, PL, NOW);

        List<RedemptionOutcome> outcomes = runConcurrently(200, i -> repository.redeem(coupon.id(), "user-" + i, NOW));

        assertThat(outcomes).filteredOn(o -> o == REDEEMED).hasSize(10);
        assertThat(outcomes).filteredOn(o -> o == EXHAUSTED).hasSize(190);
        assertThat(currentUses(coupon)).isEqualTo(10);
        assertThat(redemptionRows(coupon)).isEqualTo(10);
    }

    @Test
    void sameUserRedeemingConcurrentlySucceedsOnlyOnce() throws Exception {
        Coupon coupon = repository.create(CouponCode.of("BLACKFRIDAY"), 100, PL, NOW);

        List<RedemptionOutcome> outcomes = runConcurrently(50, _ -> repository.redeem(coupon.id(), "user-1", NOW));

        assertThat(outcomes).filteredOn(o -> o == REDEEMED).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o == ALREADY_REDEEMED).hasSize(49);
        assertThat(currentUses(coupon)).isEqualTo(1);
        assertThat(redemptionRows(coupon)).isEqualTo(1);
    }

    @Test
    void concurrentReversalsOfSameRedemptionDecrementOnce() throws Exception {
        Coupon coupon = repository.create(CouponCode.of("BLACKFRIDAY"), 10, PL, NOW);
        repository.redeem(coupon.id(), "user-1", NOW);
        repository.redeem(coupon.id(), "user-2", NOW);

        List<Boolean> outcomes = runConcurrently(50, _ -> repository.reverseRedemption(coupon.id(), "user-1", NOW));

        assertThat(outcomes).filteredOn(reversed -> reversed).hasSize(1);
        assertThat(currentUses(coupon)).isEqualTo(1);
        assertThat(activeRedemptionRows(coupon)).isEqualTo(1);
    }

    @Test
    void reversalsRacingWithRedemptionsNeverExceedMaxUses() throws Exception {
        Coupon coupon = repository.create(CouponCode.of("BLACKFRIDAY"), 10, PL, NOW);
        for (int i = 0; i < 10; i++) {
            repository.redeem(coupon.id(), "early-" + i, NOW);
        }

        // 10 cancelled orders release their uses while 100 new customers race for them.
        List<Object> outcomes = runConcurrently(110, i -> i < 10
                ? repository.reverseRedemption(coupon.id(), "early-" + i, NOW)
                : repository.redeem(coupon.id(), "late-" + i, NOW));

        assertThat(outcomes.subList(0, 10)).containsOnly(true);
        assertThat(currentUses(coupon)).isLessThanOrEqualTo(10).isEqualTo(activeRedemptionRows(coupon));
        assertThat(outcomes).filteredOn(o -> o == REDEEMED).hasSize(currentUses(coupon));
    }

    /** Starts all tasks at once behind a gate, so they really compete for the same row. */
    private static <T> List<T> runConcurrently(int n, IntFunction<T> task) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<T>> callables = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int index = i;
            callables.add(() -> {
                startGate.await();
                return task.apply(index);
            });
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = callables.stream().map(executor::submit).toList();
            startGate.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private int currentUses(Coupon coupon) {
        return jdbc.sql("SELECT current_uses FROM coupon WHERE id = ?").param(coupon.id()).query(Integer.class).single();
    }

    private int activeRedemptionRows(Coupon coupon) {
        return jdbc.sql("SELECT count(*) FROM coupon_redemption WHERE coupon_id = ? AND reversed_at IS NULL")
                .param(coupon.id()).query(Integer.class).single();
    }

    private int redemptionRows(Coupon coupon) {
        return jdbc.sql("SELECT count(*) FROM coupon_redemption WHERE coupon_id = ?").param(coupon.id())
                .query(Integer.class).single();
    }
}

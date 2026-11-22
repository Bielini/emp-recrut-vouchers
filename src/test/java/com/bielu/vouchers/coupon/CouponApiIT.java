package com.bielu.vouchers.coupon;

import com.bielu.vouchers.TestcontainersConfiguration;
import com.bielu.vouchers.coupon.domain.CountryResolver;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

/**
 * End-to-end: HTTP API + real PostgreSQL + geolocation provider simulated by WireMock.
 * Each test uses its own client address, because successful lookups are cached.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CouponApiIT {

    @RegisterExtension
    static WireMockExtension geoApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void geoProperties(DynamicPropertyRegistry registry) {
        registry.add("geo.base-url", geoApi::baseUrl);
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @MockitoSpyBean
    private CountryResolver countryResolver;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM coupon_redemption").update();
        jdbc.sql("DELETE FROM coupon").update();
    }

    @Test
    void fullRedemptionFlow() {
        String client = "83.0.0.1";
        geoReturns(client, "PL");
        createCoupon("WIOSNA", 2, "PL");

        assertThat(redeem("WIOSNA", "user-1", client)).hasStatus(HttpStatus.CREATED);
        assertRefused(redeem("WIOSNA", "user-1", client), HttpStatus.CONFLICT, "COUPON_ALREADY_REDEEMED");
        assertThat(redeem("WIOSNA", "user-2", client)).hasStatus(HttpStatus.CREATED);
        assertRefused(redeem("WIOSNA", "user-3", client), HttpStatus.CONFLICT, "COUPON_EXHAUSTED");

        assertThat(currentUses("WIOSNA")).isEqualTo(2);
    }

    @Test
    void cancelledOrderReleasesUseForOtherCustomers() {
        String client = "83.0.0.6";
        geoReturns(client, "PL");
        createCoupon("WIOSNA", 1, "PL");
        assertThat(redeem("WIOSNA", "user-1", client)).hasStatus(HttpStatus.CREATED);
        assertRefused(redeem("WIOSNA", "user-2", client), HttpStatus.CONFLICT, "COUPON_EXHAUSTED");

        assertThat(reverse("wiosna", "user-1")).hasStatus(HttpStatus.NO_CONTENT);
        assertRefused(reverse("WIOSNA", "user-1"), HttpStatus.NOT_FOUND, "REDEMPTION_NOT_FOUND");

        assertThat(currentUses("WIOSNA")).isZero();
        assertThat(redeem("WIOSNA", "user-2", client)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void redeemsCouponRegardlessOfCodeCase() {
        String client = "83.0.0.2";
        geoReturns(client, "PL");
        createCoupon("WIOSNA", 10, "PL");

        assertThat(redeem("wiosna", "user-1", client)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void rejectsRedemptionFromOtherCountry() {
        String client = "83.0.0.3";
        geoReturns(client, "DE");
        createCoupon("WIOSNA", 10, "PL");

        assertRefused(redeem("WIOSNA", "user-1", client), HttpStatus.FORBIDDEN, "COUNTRY_NOT_ALLOWED");
        assertThat(currentUses("WIOSNA")).isZero();
    }

    @Test
    void returns503WhenGeolocationDown() {
        String client = "83.0.0.4";
        geoApi.stubFor(get(urlPathEqualTo("/json/" + client)).willReturn(aResponse().withStatus(500)));
        createCoupon("WIOSNA", 10, "PL");

        assertRefused(redeem("WIOSNA", "user-1", client), HttpStatus.SERVICE_UNAVAILABLE, "GEOLOCATION_UNAVAILABLE");
        assertThat(currentUses("WIOSNA")).isZero();
    }

    @Test
    void geolocationIsCalledOutsideTransaction() throws Throwable {
        // No DB connection may be held while waiting for a slow external call during a traffic peak.
        String client = "83.0.0.5";
        geoReturns(client, "PL");
        createCoupon("WIOSNA", 10, "PL");
        AtomicReference<Boolean> transactionActive = new AtomicReference<>();
        willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).given(countryResolver).resolve(any());

        assertThat(redeem("WIOSNA", "user-1", client)).hasStatus(HttpStatus.CREATED);
        assertThat(transactionActive.get()).isFalse();
    }

    private void geoReturns(String ip, String countryCode) {
        geoApi.stubFor(get(urlPathEqualTo("/json/" + ip))
                .willReturn(okJson("{\"status\":\"success\",\"countryCode\":\"" + countryCode + "\"}")));
    }

    private void createCoupon(String code, int maxUses, String countryCode) {
        MvcTestResult result = mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"maxUses\":%d,\"countryCode\":\"%s\"}".formatted(code, maxUses, countryCode))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult redeem(String code, String userId, String clientAddress) {
        return mvc.post().uri("/api/v1/coupons/{code}/redemptions", code)
                .with(request -> {
                    request.setRemoteAddr(clientAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\"}".formatted(userId))
                .exchange();
    }

    private MvcTestResult reverse(String code, String userId) {
        return mvc.delete().uri("/api/v1/coupons/{code}/redemptions/{userId}", code, userId).exchange();
    }

    private static void assertRefused(MvcTestResult result, HttpStatus status, String errorCode) {
        assertThat(result).hasStatus(status).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
    }

    private int currentUses(String code) {
        MvcTestResult result = mvc.get().uri("/api/v1/coupons/{code}", code).exchange();
        assertThat(result).hasStatusOk();
        return jdbc.sql("SELECT current_uses FROM coupon WHERE code = ?").param(code).query(Integer.class).single();
    }
}

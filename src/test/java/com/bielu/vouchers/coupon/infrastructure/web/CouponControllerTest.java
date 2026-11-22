package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.application.CouponService;
import com.bielu.vouchers.coupon.application.CreateCouponCommand;
import com.bielu.vouchers.coupon.application.RedeemCouponCommand;
import com.bielu.vouchers.coupon.domain.CountryCode;
import com.bielu.vouchers.coupon.domain.Coupon;
import com.bielu.vouchers.coupon.domain.CouponCode;
import com.bielu.vouchers.coupon.domain.exception.CountryNotAllowedException;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;
import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyRedeemedException;
import com.bielu.vouchers.coupon.domain.exception.CouponDomainException;
import com.bielu.vouchers.coupon.domain.exception.CouponExhaustedException;
import com.bielu.vouchers.coupon.domain.exception.CouponNotFoundException;
import com.bielu.vouchers.coupon.domain.exception.GeolocationUnavailableException;
import com.bielu.vouchers.coupon.domain.exception.InvalidCountryCodeException;
import com.bielu.vouchers.coupon.domain.exception.RedemptionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    private static final Coupon WIOSNA = new Coupon(
            1L, CouponCode.of("WIOSNA"), 10, 0, CountryCode.of("PL"), Instant.parse("2026-11-28T10:00:00Z"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CouponService service;

    @Test
    void createReturns201WithLocation() {
        given(service.create(any())).willReturn(WIOSNA);

        MvcTestResult result = postCoupon("""
                {"code": "wiosna", "maxUses": 10, "countryCode": "pl"}""");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/v1/coupons/WIOSNA");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("WIOSNA");
        assertThat(result).bodyJson().extractingPath("$.currentUses").isEqualTo(0);
        then(service).should().create(new CreateCouponCommand("wiosna", 10, "pl"));
    }

    @Test
    void createReturns409WhenCodeExists() {
        given(service.create(any())).willThrow(new CouponAlreadyExistsException());

        MvcTestResult result = postCoupon("""
                {"code": "WIOSNA", "maxUses": 10, "countryCode": "PL"}""");

        assertThat(result).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_ALREADY_EXISTS");
    }

    @Test
    void createReturns400ForInvalidPayload() {
        MvcTestResult result = postCoupon("""
                {"maxUses": 0, "countryCode": "PL"}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("code", "maxUses");
        then(service).shouldHaveNoInteractions();
    }

    @Test
    void createReturns400ForMalformedJson() {
        MvcTestResult result = postCoupon("{\"code\": ");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void createReturns400ForUnknownCountry() {
        given(service.create(any())).willThrow(new InvalidCountryCodeException());

        MvcTestResult result = postCoupon("""
                {"code": "WIOSNA", "maxUses": 10, "countryCode": "XX"}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void getReturnsCoupon() {
        given(service.getByCode("wiosna")).willReturn(WIOSNA);

        MvcTestResult result = mvc.get().uri("/api/v1/coupons/wiosna").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("WIOSNA");
        assertThat(result).bodyJson().extractingPath("$.countryCode").isEqualTo("PL");
        assertThat(result).bodyJson().extractingPath("$.maxUses").isEqualTo(10);
    }

    @Test
    void getReturns404ForUnknownCode() {
        given(service.getByCode("MISSING")).willThrow(new CouponNotFoundException());

        MvcTestResult result = mvc.get().uri("/api/v1/coupons/MISSING").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() {
        given(service.getByCode("WIOSNA")).willThrow(new IllegalStateException("connection to db-prod-01 refused"));

        MvcTestResult result = mvc.get().uri("/api/v1/coupons/WIOSNA").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyText().doesNotContain("db-prod-01");
    }

    @Test
    void redeemReturns201() {
        given(service.redeem(any())).willReturn(Instant.parse("2026-11-28T10:00:00Z"));

        MvcTestResult result = redeem("wiosna", "83.0.0.1", """
                {"userId": "user-1"}""");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.couponCode").isEqualTo("WIOSNA");
        assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo("user-1");
        assertThat(result).bodyJson().extractingPath("$.redeemedAt").isEqualTo("2026-11-28T10:00:00Z");
    }

    @Test
    void passesRemoteAddressToService() {
        given(service.redeem(any())).willReturn(Instant.parse("2026-11-28T10:00:00Z"));

        redeem("wiosna", "83.0.0.1", """
                {"userId": "user-1"}""");

        then(service).should().redeem(new RedeemCouponCommand("wiosna", "user-1", InetAddress.ofLiteral("83.0.0.1")));
    }

    @Test
    void ignoresForwardedHeaderWithoutTrustedProxy() {
        given(service.redeem(any())).willReturn(Instant.parse("2026-11-28T10:00:00Z"));

        // A client could put any "Polish" address here; without a trusted proxy the header must be ignored.
        mvc.post().uri("/api/v1/coupons/WIOSNA/redemptions")
                .with(request -> {
                    request.setRemoteAddr("83.0.0.1");
                    return request;
                })
                .header("X-Forwarded-For", "5.6.7.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId": "user-1"}""")
                .exchange();

        ArgumentCaptor<RedeemCouponCommand> command = ArgumentCaptor.forClass(RedeemCouponCommand.class);
        then(service).should().redeem(command.capture());
        assertThat(command.getValue().clientAddress()).isEqualTo(InetAddress.ofLiteral("83.0.0.1"));
    }

    @Test
    void redeemReturns400ForBlankUserId() {
        MvcTestResult result = redeem("WIOSNA", "83.0.0.1", """
                {"userId": "  "}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        then(service).shouldHaveNoInteractions();
    }

    @Test
    void redeemReturns400ForTooLongUserId() {
        MvcTestResult result = redeem("WIOSNA", "83.0.0.1", "{\"userId\": \"" + "u".repeat(65) + "\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void redeemReturns404ForUnknownCoupon() {
        assertRedeemRefused(new CouponNotFoundException(), HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND");
    }

    @Test
    void redeemReturns403ForCountryNotAllowed() {
        assertRedeemRefused(new CountryNotAllowedException(), HttpStatus.FORBIDDEN, "COUNTRY_NOT_ALLOWED");
    }

    @Test
    void redeemReturns409WhenAlreadyRedeemed() {
        assertRedeemRefused(new CouponAlreadyRedeemedException(), HttpStatus.CONFLICT, "COUPON_ALREADY_REDEEMED");
    }

    @Test
    void redeemReturns409WhenExhausted() {
        assertRedeemRefused(new CouponExhaustedException(), HttpStatus.CONFLICT, "COUPON_EXHAUSTED");
    }

    @Test
    void redeemReturns503WithRetryAfterWhenGeolocationUnavailable() {
        MvcTestResult result = assertRedeemRefused(
                new GeolocationUnavailableException(new RuntimeException("timeout")),
                HttpStatus.SERVICE_UNAVAILABLE, "GEOLOCATION_UNAVAILABLE");

        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    }

    @Test
    void reverseReturns204() {
        MvcTestResult result = reverse("wiosna", "user-1");

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().reverseRedemption("wiosna", "user-1");
    }

    @Test
    void reverseReturns404WhenNoActiveRedemption() {
        willThrow(new RedemptionNotFoundException()).given(service).reverseRedemption("WIOSNA", "user-1");

        MvcTestResult result = reverse("WIOSNA", "user-1");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("REDEMPTION_NOT_FOUND");
    }

    @Test
    void reverseReturns404ForUnknownCoupon() {
        willThrow(new CouponNotFoundException()).given(service).reverseRedemption("MISSING", "user-1");

        MvcTestResult result = reverse("MISSING", "user-1");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void reverseReturns400ForTooLongUserId() {
        MvcTestResult result = reverse("WIOSNA", "u".repeat(65));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        then(service).shouldHaveNoInteractions();
    }

    private MvcTestResult reverse(String code, String userId) {
        return mvc.delete().uri("/api/v1/coupons/{code}/redemptions/{userId}", code, userId).exchange();
    }

    private MvcTestResult assertRedeemRefused(CouponDomainException exception, HttpStatus status, String errorCode) {
        given(service.redeem(any())).willThrow(exception);

        MvcTestResult result = redeem("WIOSNA", "83.0.0.1", """
                {"userId": "user-1"}""");

        assertThat(result).hasStatus(status).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
        return result;
    }

    private MvcTestResult redeem(String code, String remoteAddr, String json) {
        return mvc.post().uri("/api/v1/coupons/{code}/redemptions", code)
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult postCoupon(String json) {
        return mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}

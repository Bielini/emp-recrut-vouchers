package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.application.CouponService;
import com.bielu.vouchers.coupon.application.RedeemCouponCommand;
import com.bielu.vouchers.coupon.domain.Coupon;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController {

    private final CouponService service;

    CouponController(CouponService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        Coupon coupon = service.create(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(coupon.code().value())
                .toUri();
        return ResponseEntity.created(location).body(CouponResponse.from(coupon));
    }

    @GetMapping("/{code}")
    CouponResponse get(@PathVariable String code) {
        return CouponResponse.from(service.getByCode(code));
    }

    @PostMapping("/{code}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    RedemptionResponse redeem(@PathVariable String code,
                              @Valid @RequestBody RedeemCouponRequest request,
                              HttpServletRequest httpRequest) {
        // BR-3: the address of the TCP peer, never X-Forwarded-For read by hand (trivial to spoof).
        // Behind a load balancer, server.forward-headers-strategy=native lets Tomcat trust only internal proxies.
        InetAddress clientAddress = InetAddress.ofLiteral(httpRequest.getRemoteAddr());
        return RedemptionResponse.from(service.redeem(new RedeemCouponCommand(code, request.userId(), clientAddress)));
    }

    /** BR-6: called by the order system when the order paid with the coupon is cancelled or returned. */
    @DeleteMapping("/{code}/redemptions/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reverseRedemption(@PathVariable String code, @PathVariable @Size(max = 64) String userId) {
        service.reverseRedemption(code, userId);
    }
}

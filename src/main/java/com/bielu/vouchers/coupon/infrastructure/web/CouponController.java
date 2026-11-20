package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.application.CouponService;
import com.bielu.vouchers.coupon.domain.Coupon;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
}

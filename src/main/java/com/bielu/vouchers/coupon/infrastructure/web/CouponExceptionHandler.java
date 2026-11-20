package com.bielu.vouchers.coupon.infrastructure.web;

import com.bielu.vouchers.coupon.domain.exception.CouponAlreadyExistsException;
import com.bielu.vouchers.coupon.domain.exception.CouponDomainException;
import com.bielu.vouchers.coupon.domain.exception.CouponNotFoundException;
import com.bielu.vouchers.coupon.domain.exception.InvalidCountryCodeException;
import com.bielu.vouchers.coupon.domain.exception.InvalidCouponCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * BR-5: every refusal is an RFC 9457 ProblemDetail with a stable, machine-readable {@code errorCode}.
 */
@RestControllerAdvice
class CouponExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CouponExceptionHandler.class);

    static final String ERROR_CODE = "errorCode";
    static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(CouponDomainException.class)
    ProblemDetail handleDomain(CouponDomainException e) {
        return switch (e) {
            case InvalidCouponCodeException _, InvalidCountryCodeException _ ->
                    problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, e.getMessage());
            case CouponNotFoundException _ ->
                    problem(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", e.getMessage());
            case CouponAlreadyExistsException _ ->
                    problem(HttpStatus.CONFLICT, "COUPON_ALREADY_EXISTS", e.getMessage());
            default -> handleUnexpected(e);
        };
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        // Full details go to the log only; the client never sees internals (hosts, SQL, stack traces).
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error, please try again later");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Rejected values are deliberately not echoed back.
        ex.getBody().setProperty("errors", ex.getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList());
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /** Spring MVC built-in 400 responses (malformed JSON, missing body) get the same errorCode as validation errors. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Some handlers pass body == null and the ProblemDetail is taken from the exception itself later on.
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd
                : ex instanceof ErrorResponse errorResponse ? errorResponse.getBody()
                : null;
        if (status.value() == HttpStatus.BAD_REQUEST.value() && problem != null) {
            problem.setProperty(ERROR_CODE, VALIDATION_FAILED);
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(ERROR_CODE, errorCode);
        return problem;
    }
}

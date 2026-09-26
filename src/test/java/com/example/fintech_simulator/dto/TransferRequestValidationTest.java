package com.example.fintech_simulator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TransferRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private TransferRequest valid() {
        TransferRequest r = new TransferRequest();
        r.setFromCard("1111222233334444");
        r.setToCard("1111222233335555");
        r.setAmount(new BigDecimal("10.00"));
        return r;
    }

    @Test
    void aWellFormedRequestHasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void rejectsShortAndNonNumericCardNumbers() {
        TransferRequest r = valid();
        r.setFromCard("1234");
        r.setToCard("abcdabcdabcdabcd");

        Set<ConstraintViolation<TransferRequest>> violations = validator.validate(r);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("fromCard", "toCard");
    }

    @Test
    void rejectsZeroAndMissingAmounts() {
        TransferRequest zero = valid();
        zero.setAmount(new BigDecimal("0.00"));
        TransferRequest missing = valid();
        missing.setAmount(null);

        assertThat(validator.validate(zero)).hasSize(1);
        assertThat(validator.validate(missing)).hasSize(1);
    }

    @Test
    void idempotencyKeyIsOptionalButCappedAt100Characters() {
        TransferRequest ok = valid();
        ok.setIdempotencyKey("x".repeat(100));
        TransferRequest tooLong = valid();
        tooLong.setIdempotencyKey("x".repeat(101));

        assertThat(validator.validate(valid())).isEmpty();
        assertThat(validator.validate(ok)).isEmpty();
        assertThat(validator.validate(tooLong)).hasSize(1);
    }

    @Test
    void exchangeRequestFollowsTheSameRules() {
        ExchangeRequest r = new ExchangeRequest();
        r.setFromCard("bad");
        r.setToCard("1111222233335555");
        r.setAmount(new BigDecimal("-1"));

        assertThat(validator.validate(r)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("fromCard", "amount");
    }
}

package com.example.fintech_simulator.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TransferRequest {
    @NotNull
    @Pattern(regexp = "\\d{16}", message = "fromCard must be a 16-digit card number")
    private String fromCard;

    @NotNull
    @Pattern(regexp = "\\d{16}", message = "toCard must be a 16-digit card number")
    private String toCard;

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    // Optional: lets a client safely retry the same request (e.g. after a
    // timeout) without risking a duplicate transfer.
    private String idempotencyKey;
}

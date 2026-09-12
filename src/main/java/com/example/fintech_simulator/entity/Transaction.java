package com.example.fintech_simulator.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Data;

/**
 * Immutable audit record of a transfer/exchange attempt, successful or not.
 */
@Entity
@Data
public class Transaction {
    @Id
    private String id;
    private String fromCard;
    private String toCard;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private BigDecimal requestedAmount;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal settledAmount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String failureReason;
    private Instant createdAt;

    // Lets a client safely retry a request (e.g. after a network timeout) without
    // risking a duplicate transfer — see TransferService for how it's enforced.
    @Column(unique = true)
    private String idempotencyKey;
}

package com.example.fintech_simulator.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;
import com.example.fintech_simulator.entity.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * What the API returns for a ledger entry — kept separate from the JPA
 * entity for the same reason as {@link CardResponse}.
 */
@Data
@AllArgsConstructor
public class TransactionResponse {
    private String id;
    private String fromCard;
    private String toCard;
    private TransactionType type;
    private BigDecimal requestedAmount;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal settledAmount;
    private TransactionStatus status;
    private String failureReason;
    private Instant createdAt;

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(), tx.getFromCard(), tx.getToCard(), tx.getType(),
                tx.getRequestedAmount(), tx.getFromCurrency(), tx.getToCurrency(),
                tx.getSettledAmount(), tx.getStatus(), tx.getFailureReason(), tx.getCreatedAt());
    }
}

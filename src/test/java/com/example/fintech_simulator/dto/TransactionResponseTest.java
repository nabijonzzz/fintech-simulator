package com.example.fintech_simulator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;
import com.example.fintech_simulator.entity.TransactionType;

class TransactionResponseTest {

    @Test
    void copiesEveryFieldFromTheEntityExceptTheIdempotencyKey() {
        Transaction tx = new Transaction();
        tx.setId("tx-1");
        tx.setFromCard("1111111111111111");
        tx.setToCard("2222222222222222");
        tx.setType(TransactionType.TRANSFER);
        tx.setRequestedAmount(new BigDecimal("10.00"));
        tx.setFromCurrency("USD");
        tx.setToCurrency("EUR");
        tx.setSettledAmount(new BigDecimal("9.00"));
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setFailureReason(null);
        Instant now = Instant.now();
        tx.setCreatedAt(now);
        tx.setIdempotencyKey("secret-client-key");

        TransactionResponse response = TransactionResponse.from(tx);

        assertThat(response.getId()).isEqualTo("tx-1");
        assertThat(response.getFromCard()).isEqualTo("1111111111111111");
        assertThat(response.getToCard()).isEqualTo("2222222222222222");
        assertThat(response.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(response.getRequestedAmount()).isEqualByComparingTo("10.00");
        assertThat(response.getFromCurrency()).isEqualTo("USD");
        assertThat(response.getToCurrency()).isEqualTo("EUR");
        assertThat(response.getSettledAmount()).isEqualByComparingTo("9.00");
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        // idempotencyKey is deliberately not part of the response shape.
    }
}

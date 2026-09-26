package com.example.fintech_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;
import com.example.fintech_simulator.entity.TransactionType;
import com.example.fintech_simulator.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionAuditLoggerTest {

    @Mock
    TransactionRepository transactionRepository;

    @Test
    void logFailurePersistsAFailedTransactionWithTheReason() {
        TransactionAuditLogger logger = new TransactionAuditLogger(transactionRepository);

        logger.logFailure("1111111111111111", "2222222222222222",
                new BigDecimal("50.00"), TransactionType.TRANSFER, "Not enough money on sender card");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getFromCard()).isEqualTo("1111111111111111");
        assertThat(saved.getToCard()).isEqualTo("2222222222222222");
        assertThat(saved.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(saved.getRequestedAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("Not enough money on sender card");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void logFailureStoresTheIdempotencyKeySoRetriesReplayTheSameFailure() {
        TransactionAuditLogger logger = new TransactionAuditLogger(transactionRepository);

        logger.logFailure("1111111111111111", "2222222222222222",
                new BigDecimal("50.00"), TransactionType.EXCHANGE, "Daily transfer limit exceeded", "retry-key");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("retry-key");
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.EXCHANGE);
    }

    @Test
    void logFailureWithoutAKeyLeavesItNull() {
        TransactionAuditLogger logger = new TransactionAuditLogger(transactionRepository);

        logger.logFailure("1111111111111111", "2222222222222222",
                new BigDecimal("5.00"), TransactionType.TRANSFER, "Amount must be more than 0");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isNull();
    }
}

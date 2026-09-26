package com.example.fintech_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyServiceTest {

    @Mock
    TransactionRepository transactionRepository;

    @Test
    void reserveReturnsTrueWhenTheRowIsSaved() {
        IdempotencyKeyService service = new IdempotencyKeyService(transactionRepository);
        Transaction tx = new Transaction();
        when(transactionRepository.saveAndFlush(tx)).thenReturn(tx);

        assertThat(service.reserve(tx)).isTrue();
    }

    @Test
    void reserveReturnsFalseWhenTheKeyIsAlreadyTaken() {
        IdempotencyKeyService service = new IdempotencyKeyService(transactionRepository);
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThat(service.reserve(new Transaction())).isFalse();
    }

    @Test
    void findExistingLooksUpByKey() {
        IdempotencyKeyService service = new IdempotencyKeyService(transactionRepository);
        Transaction tx = new Transaction();
        tx.setId("t1");
        when(transactionRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(tx));

        assertThat(service.findExisting("k")).containsSame(tx);
        assertThat(service.findExisting("other")).isEmpty();
    }
}

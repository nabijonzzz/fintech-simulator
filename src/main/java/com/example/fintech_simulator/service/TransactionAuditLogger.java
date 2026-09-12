package com.example.fintech_simulator.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;
import com.example.fintech_simulator.entity.TransactionType;
import com.example.fintech_simulator.repository.TransactionRepository;

@Service
public class TransactionAuditLogger {

    private final TransactionRepository transactionRepository;

    public TransactionAuditLogger(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // REQUIRES_NEW so this commits in its own transaction even though the
    // caller's transaction (the failed transfer) is about to roll back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String fromCard, String toCard, BigDecimal amount, TransactionType type, String reason) {
        logFailure(fromCard, toCard, amount, type, reason, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String fromCard, String toCard, BigDecimal amount, TransactionType type,
                            String reason, String idempotencyKey) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setFromCard(fromCard);
        tx.setToCard(toCard);
        tx.setType(type);
        tx.setRequestedAmount(amount);
        tx.setStatus(TransactionStatus.FAILED);
        tx.setFailureReason(reason);
        tx.setCreatedAt(Instant.now());
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);
    }
}

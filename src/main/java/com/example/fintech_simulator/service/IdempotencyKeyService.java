package com.example.fintech_simulator.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.repository.TransactionRepository;

/**
 * Reserves an idempotency key in its own transaction, separate from the
 * caller's. A failed reservation (someone else already won the race) leaves
 * the Hibernate session in a broken state — running the recovery read in the
 * same transaction would fail too, so both steps get their own REQUIRES_NEW
 * transaction instead, same idea as {@link TransactionAuditLogger}.
 */
@Service
public class IdempotencyKeyService {

    private final TransactionRepository transactionRepository;

    public IdempotencyKeyService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(Transaction tx) {
        try {
            transactionRepository.saveAndFlush(tx);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Transaction> findExisting(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }
}

package com.example.fintech_simulator.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByFromCardOrToCardOrderByCreatedAtDesc(String fromCard, String toCard);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByFromCardAndStatusAndCreatedAtAfter(
            String fromCard, TransactionStatus status, Instant after);
}

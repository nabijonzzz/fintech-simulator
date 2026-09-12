package com.example.fintech_simulator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.example.fintech_simulator.entity.Transaction;

public interface TransactionRepository extends CrudRepository<Transaction, String> {
    List<Transaction> findByFromCardOrToCardOrderByCreatedAtDesc(String fromCard, String toCard);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}

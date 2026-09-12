package com.example.fintech_simulator.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.fintech_simulator.entity.Card;
import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;
import com.example.fintech_simulator.entity.TransactionType;
import com.example.fintech_simulator.repository.CardRepository;
import com.example.fintech_simulator.repository.TransactionRepository;

@Service
public class TransferService {

    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionAuditLogger auditLogger;

    public TransferService(CardRepository cardRepository,
                            TransactionRepository transactionRepository,
                            TransactionAuditLogger auditLogger) {
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogger = auditLogger;
    }

    public Card getCardDetails(String cardNumber) {
        return cardRepository.findById(cardNumber)
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
    }

    public List<Card> getAllCards() {
        List<Card> cards = new ArrayList<>();
        cardRepository.findAll().forEach(cards::add);
        return cards;
    }

    public List<Transaction> getHistory(String cardNumber) {
        return transactionRepository.findByFromCardOrToCardOrderByCreatedAtDesc(cardNumber, cardNumber);
    }

    private BigDecimal getRateToUsd(String currency) {
        switch (currency) {
            case "USD": return new BigDecimal("1.00");
            case "EUR": return new BigDecimal("1.10");
            case "GBP": return new BigDecimal("1.30");
            default: throw new IllegalArgumentException("Unknown currency: " + currency);
        }
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal amountInUsd = amount.multiply(getRateToUsd(fromCurrency));
        return amountInUsd.divide(getRateToUsd(toCurrency), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Transaction transferMoney(String fromCardNumber, String toCardNumber, BigDecimal amount, TransactionType type) {
        return transferMoney(fromCardNumber, toCardNumber, amount, type, null);
    }

    @Transactional
    public Transaction transferMoney(String fromCardNumber, String toCardNumber, BigDecimal amount,
                                      TransactionType type, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                // Same request seen before (e.g. a client retry after a timeout) — replay
                // its outcome instead of moving money a second time.
                Transaction tx = existing.get();
                if (tx.getStatus() == TransactionStatus.FAILED) {
                    throw new IllegalArgumentException(tx.getFailureReason());
                }
                return tx;
            }
        }

        try {
            if (fromCardNumber.equals(toCardNumber)) {
                throw new IllegalArgumentException("Cannot transfer to the same card");
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be more than 0");
            }

            Card fromCard = cardRepository.findById(fromCardNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Sender card not found"));
            Card toCard = cardRepository.findById(toCardNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Receiver card not found"));

            if (fromCard.getBalance().compareTo(amount) < 0) {
                throw new IllegalArgumentException("Not enough money on sender card");
            }

            BigDecimal settledAmount = convert(amount, fromCard.getCurrency(), toCard.getCurrency());

            fromCard.setBalance(fromCard.getBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP));
            toCard.setBalance(toCard.getBalance().add(settledAmount).setScale(2, RoundingMode.HALF_UP));
            cardRepository.save(fromCard);
            cardRepository.save(toCard);

            Transaction tx = new Transaction();
            tx.setId(UUID.randomUUID().toString());
            tx.setFromCard(fromCardNumber);
            tx.setToCard(toCardNumber);
            tx.setType(type);
            tx.setRequestedAmount(amount);
            tx.setFromCurrency(fromCard.getCurrency());
            tx.setToCurrency(toCard.getCurrency());
            tx.setSettledAmount(settledAmount);
            tx.setStatus(TransactionStatus.COMPLETED);
            tx.setCreatedAt(Instant.now());
            tx.setIdempotencyKey(idempotencyKey);
            return transactionRepository.save(tx);

        } catch (IllegalArgumentException ex) {
            auditLogger.logFailure(fromCardNumber, toCardNumber, amount, type, ex.getMessage());
            throw ex;
        }
    }
}

package com.example.fintech_simulator.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.fintech_simulator.dto.ExchangeRequest;
import com.example.fintech_simulator.dto.TransferRequest;
import com.example.fintech_simulator.dto.TransferResponse;
import com.example.fintech_simulator.entity.Card;
import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionType;
import com.example.fintech_simulator.service.TransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/rate/convert")
    public BigDecimal getLiveConversion(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        return transferService.convert(amount, from, to);
    }

    @GetMapping("/cards")
    public List<Card> getAllCards() {
        return transferService.getAllCards();
    }

    @GetMapping("/card/{cardNumber}")
    public Card getCardInfo(@PathVariable String cardNumber) {
        return transferService.getCardDetails(cardNumber);
    }

    @GetMapping("/transactions/{cardNumber}")
    public List<Transaction> getHistory(@PathVariable String cardNumber) {
        return transferService.getHistory(cardNumber);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> makeTransfer(@Valid @RequestBody TransferRequest request) {
        Transaction tx = transferService.transferMoney(request.getFromCard(), request.getToCard(),
                request.getAmount(), TransactionType.TRANSFER, request.getIdempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TransferResponse(tx.getId(), tx.getStatus().name(), "Transfer Successful"));
    }

    @PostMapping("/exchange")
    public ResponseEntity<TransferResponse> makeExchange(@Valid @RequestBody ExchangeRequest request) {
        Transaction tx = transferService.transferMoney(request.getFromCard(), request.getToCard(),
                request.getAmount(), TransactionType.EXCHANGE, request.getIdempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TransferResponse(tx.getId(), tx.getStatus().name(), "Exchange Successful"));
    }
}

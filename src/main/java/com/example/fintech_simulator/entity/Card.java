package com.example.fintech_simulator.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Card {
    @Id
    private String cardNumber;
    private String ownerName;
    private BigDecimal balance;
    private String currency;

    // Max total (in the card's own currency) that can go out as
    // transfers/exchanges within a rolling calendar day — a basic
    // fraud/risk control, same idea real card issuers use.
    private BigDecimal dailyLimit;
}

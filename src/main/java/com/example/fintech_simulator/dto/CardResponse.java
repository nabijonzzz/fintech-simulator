package com.example.fintech_simulator.dto;

import java.math.BigDecimal;

import com.example.fintech_simulator.entity.Card;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * What the API actually returns for a card — kept separate from the JPA
 * entity so the response shape doesn't change just because the table does.
 */
@Data
@AllArgsConstructor
public class CardResponse {
    private String cardNumber;
    private String ownerName;
    private BigDecimal balance;
    private String currency;
    private BigDecimal dailyLimit;

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getCardNumber(), card.getOwnerName(), card.getBalance(),
                card.getCurrency(), card.getDailyLimit());
    }
}

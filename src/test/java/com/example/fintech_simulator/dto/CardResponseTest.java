package com.example.fintech_simulator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.fintech_simulator.entity.Card;

class CardResponseTest {

    @Test
    void copiesEveryFieldFromTheEntity() {
        Card card = new Card();
        card.setCardNumber("1111222233334444");
        card.setOwnerName("Test Owner");
        card.setBalance(new BigDecimal("42.50"));
        card.setCurrency("USD");
        card.setDailyLimit(new BigDecimal("500.00"));

        CardResponse response = CardResponse.from(card);

        assertThat(response.getCardNumber()).isEqualTo("1111222233334444");
        assertThat(response.getOwnerName()).isEqualTo("Test Owner");
        assertThat(response.getBalance()).isEqualByComparingTo("42.50");
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getDailyLimit()).isEqualByComparingTo("500.00");
    }

    @Test
    void toleratesANullDailyLimit() {
        Card card = new Card();
        card.setCardNumber("1111222233334444");
        card.setOwnerName("Test Owner");
        card.setBalance(new BigDecimal("42.50"));
        card.setCurrency("USD");

        assertThat(CardResponse.from(card).getDailyLimit()).isNull();
    }
}

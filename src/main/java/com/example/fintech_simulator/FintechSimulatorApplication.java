package com.example.fintech_simulator;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.example.fintech_simulator.entity.Card;
import com.example.fintech_simulator.repository.CardRepository;

@SpringBootApplication
public class FintechSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FintechSimulatorApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(CardRepository repository) {
        return (args) -> {
            // Seed demo cards only on first run; balances persist across restarts otherwise.
            if (repository.count() == 0) {
                Card card1 = new Card();
                card1.setCardNumber("1111222233334444");
                card1.setOwnerName("Nabijon Nazarov");
                card1.setBalance(new BigDecimal("1170.00"));
                card1.setCurrency("USD");
                card1.setDailyLimit(new BigDecimal("500.00"));

                Card card2 = new Card();
                card2.setCardNumber("1111222233335555");
                card2.setOwnerName("Nabijon Nazarov");
                card2.setBalance(new BigDecimal("500.00"));
                card2.setCurrency("EUR");
                card2.setDailyLimit(new BigDecimal("400.00"));

                Card card3 = new Card();
                card3.setCardNumber("1111222233336666");
                card3.setOwnerName("Nabijon Nazarov");
                card3.setBalance(new BigDecimal("250.00"));
                card3.setCurrency("GBP");
                card3.setDailyLimit(new BigDecimal("200.00"));

                Card card4 = new Card();
                card4.setCardNumber("5555666677778888");
                card4.setOwnerName("Yasmina Matchanova");
                card4.setBalance(new BigDecimal("150.00"));
                card4.setCurrency("EUR");
                card4.setDailyLimit(new BigDecimal("400.00"));

                Card card5 = new Card();
                card5.setCardNumber("2020202020202020");
                card5.setOwnerName("John Galt");
                card5.setBalance(new BigDecimal("200.00"));
                card5.setCurrency("USD");
                card5.setDailyLimit(new BigDecimal("500.00"));

                Card card6 = new Card();
                card6.setCardNumber("1234567812345678");
                card6.setOwnerName("Ahmad Turik");
                card6.setBalance(new BigDecimal("300.00"));
                card6.setCurrency("GBP");
                card6.setDailyLimit(new BigDecimal("200.00"));

                repository.save(card1);
                repository.save(card2);
                repository.save(card3);
                repository.save(card4);
                repository.save(card5);
                repository.save(card6);

                System.out.println("--- PERSISTENT DB INITIALIZED WITH STARTING CARDS ---");
            } else {
                System.out.println("--- PERSISTENT DB LOADED SUCCESSFULLY (BALANCES PRESERVED) ---");
            }
        };
    }
}

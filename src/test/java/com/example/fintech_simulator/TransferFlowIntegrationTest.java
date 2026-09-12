package com.example.fintech_simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.fintech_simulator.dto.ErrorResponse;
import com.example.fintech_simulator.dto.TransferRequest;
import com.example.fintech_simulator.dto.TransferResponse;
import com.example.fintech_simulator.entity.Card;
import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.repository.CardRepository;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TransferFlowIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    CardRepository cardRepository;

    @Test
    void endToEndTransferMovesMoneyBetweenCards() {
        Card from = new Card();
        from.setCardNumber("9000000000000001");
        from.setOwnerName("Test Sender");
        from.setBalance(new BigDecimal("100.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000002");
        to.setOwnerName("Test Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromCard("9000000000000001");
        request.setToCard("9000000000000002");
        request.setAmount(new BigDecimal("25.00"));

        ResponseEntity<TransferResponse> response =
                restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cardRepository.findById("9000000000000001").orElseThrow().getBalance())
                .isEqualByComparingTo("75.00");
        assertThat(cardRepository.findById("9000000000000002").orElseThrow().getBalance())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void listsAllCardsIncludingOnesJustCreated() {
        Card card = new Card();
        card.setCardNumber("9000000000000003");
        card.setOwnerName("Test Lister");
        card.setBalance(new BigDecimal("15.00"));
        card.setCurrency("USD");
        cardRepository.save(card);

        ResponseEntity<Card[]> response = restTemplate.getForEntity("/api/cards", Card[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Card> cards = Arrays.asList(response.getBody());
        assertThat(cards).extracting(Card::getCardNumber).contains("9000000000000003");
    }

    @Test
    void historyEndpointReturnsTheTransactionAfterATransfer() {
        Card from = new Card();
        from.setCardNumber("9000000000000004");
        from.setOwnerName("Test History Sender");
        from.setBalance(new BigDecimal("50.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000005");
        to.setOwnerName("Test History Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromCard("9000000000000004");
        request.setToCard("9000000000000005");
        request.setAmount(new BigDecimal("15.00"));
        restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);

        ResponseEntity<Transaction[]> response =
                restTemplate.getForEntity("/api/transactions/9000000000000004", Transaction[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Transaction> history = Arrays.asList(response.getBody());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromCard()).isEqualTo("9000000000000004");
        assertThat(history.get(0).getToCard()).isEqualTo("9000000000000005");
        assertThat(history.get(0).getRequestedAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    void retryingWithTheSameIdempotencyKeyDoesNotMoveMoneyTwice() {
        Card from = new Card();
        from.setCardNumber("9000000000000006");
        from.setOwnerName("Test Idempotent Sender");
        from.setBalance(new BigDecimal("100.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000007");
        to.setOwnerName("Test Idempotent Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromCard("9000000000000006");
        request.setToCard("9000000000000007");
        request.setAmount(new BigDecimal("30.00"));
        request.setIdempotencyKey("retry-key-1");

        ResponseEntity<TransferResponse> firstResponse =
                restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);
        ResponseEntity<TransferResponse> secondResponse =
                restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getBody().getTransactionId())
                .isEqualTo(firstResponse.getBody().getTransactionId());

        assertThat(cardRepository.findById("9000000000000006").orElseThrow().getBalance())
                .isEqualByComparingTo("70.00");
        assertThat(cardRepository.findById("9000000000000007").orElseThrow().getBalance())
                .isEqualByComparingTo("30.00");
    }

    @Test
    void rejectsTransferWithInvalidCardNumberFormat() {
        TransferRequest request = new TransferRequest();
        request.setFromCard("not-a-card");
        request.setToCard("9000000000000002");
        request.setAmount(new BigDecimal("10.00"));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/transfer", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void errorResponseBodyHasTheExpectedShape() {
        TransferRequest request = new TransferRequest();
        request.setFromCard("not-a-card");
        request.setToCard("9000000000000002");
        request.setAmount(new BigDecimal("10.00"));

        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity("/api/transfer", request, ErrorResponse.class);

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("Validation Failed");
        assertThat(body.getMessage()).isNotBlank();
        assertThat(body.getTimestamp()).isNotNull();
    }
}

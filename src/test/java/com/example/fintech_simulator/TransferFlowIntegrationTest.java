package com.example.fintech_simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.fintech_simulator.dto.ErrorResponse;
import com.example.fintech_simulator.dto.PagedResponse;
import com.example.fintech_simulator.dto.TransactionResponse;
import com.example.fintech_simulator.dto.TransferRequest;
import com.example.fintech_simulator.dto.TransferResponse;
import com.example.fintech_simulator.entity.Card;
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

        ResponseEntity<PagedResponse<TransactionResponse>> response = restTemplate.exchange(
                "/api/transactions/9000000000000004", HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TransactionResponse> history = response.getBody().getContent();
        assertThat(history).hasSize(1);
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
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
    void differentIdempotencyKeysForTheSameDetailsAreTreatedAsSeparateTransfers() {
        Card from = new Card();
        from.setCardNumber("9000000000000008");
        from.setOwnerName("Test Distinct Key Sender");
        from.setBalance(new BigDecimal("100.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000009");
        to.setOwnerName("Test Distinct Key Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest first = new TransferRequest();
        first.setFromCard("9000000000000008");
        first.setToCard("9000000000000009");
        first.setAmount(new BigDecimal("20.00"));
        first.setIdempotencyKey("distinct-key-a");

        TransferRequest second = new TransferRequest();
        second.setFromCard("9000000000000008");
        second.setToCard("9000000000000009");
        second.setAmount(new BigDecimal("20.00"));
        second.setIdempotencyKey("distinct-key-b");

        ResponseEntity<TransferResponse> firstResponse =
                restTemplate.postForEntity("/api/transfer", first, TransferResponse.class);
        ResponseEntity<TransferResponse> secondResponse =
                restTemplate.postForEntity("/api/transfer", second, TransferResponse.class);

        // Same amount/cards, but a different key each time — this is two real
        // transfers, not a retry, so both should execute.
        assertThat(secondResponse.getBody().getTransactionId())
                .isNotEqualTo(firstResponse.getBody().getTransactionId());
        assertThat(cardRepository.findById("9000000000000008").orElseThrow().getBalance())
                .isEqualByComparingTo("60.00");
        assertThat(cardRepository.findById("9000000000000009").orElseThrow().getBalance())
                .isEqualByComparingTo("40.00");
    }

    @Test
    void secondTransferThatWouldExceedTheDailyLimitIsRejected() {
        Card from = new Card();
        from.setCardNumber("9000000000000010");
        from.setOwnerName("Test Limit Sender");
        from.setBalance(new BigDecimal("1000.00"));
        from.setCurrency("USD");
        from.setDailyLimit(new BigDecimal("100.00"));
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000011");
        to.setOwnerName("Test Limit Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest first = new TransferRequest();
        first.setFromCard("9000000000000010");
        first.setToCard("9000000000000011");
        first.setAmount(new BigDecimal("70.00"));
        ResponseEntity<TransferResponse> firstResponse =
                restTemplate.postForEntity("/api/transfer", first, TransferResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TransferRequest second = new TransferRequest();
        second.setFromCard("9000000000000010");
        second.setToCard("9000000000000011");
        second.setAmount(new BigDecimal("40.00"));
        ResponseEntity<ErrorResponse> secondResponse =
                restTemplate.postForEntity("/api/transfer", second, ErrorResponse.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResponse.getBody().getMessage()).isEqualTo("Daily transfer limit exceeded");
        // Only the first (70) went through — balance reflects that, not both.
        assertThat(cardRepository.findById("9000000000000010").orElseThrow().getBalance())
                .isEqualByComparingTo("930.00");
    }

    @Test
    void historyIsSplitAcrossPagesInTheRequestedSize() {
        Card from = new Card();
        from.setCardNumber("9000000000000012");
        from.setOwnerName("Test Paging Sender");
        from.setBalance(new BigDecimal("1000.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000013");
        to.setOwnerName("Test Paging Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        // 5 separate transfers -> 5 ledger entries for the sender.
        for (int i = 0; i < 5; i++) {
            TransferRequest request = new TransferRequest();
            request.setFromCard("9000000000000012");
            request.setToCard("9000000000000013");
            request.setAmount(new BigDecimal("1.00"));
            restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);
        }

        ResponseEntity<PagedResponse<TransactionResponse>> firstPage = restTemplate.exchange(
                "/api/transactions/9000000000000012?page=0&size=2", HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() { });
        ResponseEntity<PagedResponse<TransactionResponse>> secondPage = restTemplate.exchange(
                "/api/transactions/9000000000000012?page=1&size=2", HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() { });
        ResponseEntity<PagedResponse<TransactionResponse>> lastPage = restTemplate.exchange(
                "/api/transactions/9000000000000012?page=2&size=2", HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() { });

        assertThat(firstPage.getBody().getContent()).hasSize(2);
        assertThat(secondPage.getBody().getContent()).hasSize(2);
        assertThat(lastPage.getBody().getContent()).hasSize(1);
        assertThat(firstPage.getBody().getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getBody().getTotalPages()).isEqualTo(3);

        // No overlap between pages — every id shows up exactly once across all three.
        List<String> allIds = new ArrayList<>();
        firstPage.getBody().getContent().forEach(tx -> allIds.add(tx.getId()));
        secondPage.getBody().getContent().forEach(tx -> allIds.add(tx.getId()));
        lastPage.getBody().getContent().forEach(tx -> allIds.add(tx.getId()));
        assertThat(allIds).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    void requestingAnOversizedPageGetsClampedInsteadOfReturningEverything() {
        Card from = new Card();
        from.setCardNumber("9000000000000014");
        from.setOwnerName("Test Cap Sender");
        from.setBalance(new BigDecimal("1000.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000015");
        to.setOwnerName("Test Cap Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromCard("9000000000000014");
        request.setToCard("9000000000000015");
        request.setAmount(new BigDecimal("1.00"));
        restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);

        ResponseEntity<PagedResponse<TransactionResponse>> hugePage = restTemplate.exchange(
                "/api/transactions/9000000000000014?page=0&size=1000000", HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() { });

        assertThat(hugePage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hugePage.getBody().getSize()).isEqualTo(500);
        assertThat(hugePage.getBody().getContent()).hasSize(1);
    }

    @Test
    void requestingANegativePageFallsBackToTheFirstPageInsteadOfErroring() {
        Card from = new Card();
        from.setCardNumber("9000000000000016");
        from.setOwnerName("Test Negative Page Sender");
        from.setBalance(new BigDecimal("1000.00"));
        from.setCurrency("USD");
        cardRepository.save(from);

        Card to = new Card();
        to.setCardNumber("9000000000000017");
        to.setOwnerName("Test Negative Page Receiver");
        to.setBalance(new BigDecimal("0.00"));
        to.setCurrency("USD");
        cardRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromCard("9000000000000016");
        request.setToCard("9000000000000017");
        request.setAmount(new BigDecimal("1.00"));
        restTemplate.postForEntity("/api/transfer", request, TransferResponse.class);

        ResponseEntity<PagedResponse<TransactionResponse>> response = restTemplate.exchange(
                "/api/transactions/9000000000000016?page=-1&size=10", HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getPage()).isEqualTo(0);
        assertThat(response.getBody().getContent()).hasSize(1);
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

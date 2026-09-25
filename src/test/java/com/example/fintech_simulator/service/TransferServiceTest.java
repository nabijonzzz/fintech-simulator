package com.example.fintech_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.example.fintech_simulator.entity.Card;
import com.example.fintech_simulator.entity.Transaction;
import com.example.fintech_simulator.entity.TransactionStatus;
import com.example.fintech_simulator.entity.TransactionType;
import com.example.fintech_simulator.repository.CardRepository;
import com.example.fintech_simulator.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    CardRepository cardRepository;
    @Mock
    TransactionRepository transactionRepository;
    @Mock
    TransactionAuditLogger auditLogger;
    @Mock
    IdempotencyKeyService idempotencyKeyService;

    TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(cardRepository, transactionRepository, auditLogger, idempotencyKeyService);
    }

    private Card card(String number, String owner, String balance, String currency) {
        Card c = new Card();
        c.setCardNumber(number);
        c.setOwnerName(owner);
        c.setBalance(new BigDecimal(balance));
        c.setCurrency(currency);
        return c;
    }

    @Test
    void transfersSameCurrencySuccessfully() {
        Card from = card("1111111111111111", "A", "100.00", "USD");
        Card to = card("2222222222222222", "B", "50.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("30.00"), TransactionType.TRANSFER);

        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("80.00");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(cardRepository).save(from);
        verify(cardRepository).save(to);
    }

    @Test
    void convertsCurrencyCorrectlyOnExchange() {
        // 1 EUR = 1.10 USD per the fixed simulator rate table
        Card from = card("1111111111111111", "A", "100.00", "EUR");
        Card to = card("2222222222222222", "A", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("10.00"), TransactionType.EXCHANGE);

        assertThat(from.getBalance()).isEqualByComparingTo("90.00");
        assertThat(to.getBalance()).isEqualByComparingTo("11.00");
    }

    @Test
    void rejectsInsufficientFundsAndLeavesBalancesUnchanged() {
        Card from = card("1111111111111111", "A", "10.00", "USD");
        Card to = card("2222222222222222", "B", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));

        assertThrows(IllegalArgumentException.class, () -> transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("50.00"), TransactionType.TRANSFER));

        assertThat(from.getBalance()).isEqualByComparingTo("10.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
        verify(cardRepository, never()).save(any());
        verify(auditLogger).logFailure(
                eq("1111111111111111"), eq("2222222222222222"), eq(new BigDecimal("50.00")),
                eq(TransactionType.TRANSFER), any(), eq((String) null));
    }

    @Test
    void rejectsUnknownSenderCard() {
        when(cardRepository.findById("9999999999999999")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> transferService.transferMoney(
                "9999999999999999", "2222222222222222", new BigDecimal("10.00"), TransactionType.TRANSFER));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("0.00"), TransactionType.TRANSFER));
    }

    @Test
    void replaysAnExistingCompletedTransactionInsteadOfMovingMoneyAgain() {
        Transaction existing = new Transaction();
        existing.setId("existing-tx");
        existing.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Transaction result = transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("10.00"), TransactionType.TRANSFER, "key-1");

        assertThat(result).isSameAs(existing);
        verify(cardRepository, never()).findById(any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void firstRequestWithAKeyMovesMoneyAndStoresTheKey() {
        Card from = card("1111111111111111", "A", "100.00", "USD");
        Card to = card("2222222222222222", "B", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));
        when(transactionRepository.findByIdempotencyKey("fresh-key")).thenReturn(Optional.empty());
        when(idempotencyKeyService.reserve(any(Transaction.class))).thenReturn(true);

        Transaction result = transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("30.00"), TransactionType.TRANSFER, "fresh-key");

        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("30.00");
        assertThat(result.getIdempotencyKey()).isEqualTo("fresh-key");
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(cardRepository).save(from);
        verify(cardRepository).save(to);
    }

    @Test
    void losingTheIdempotencyRaceReturnsTheWinnerWithoutMovingMoneyTwice() {
        Card from = card("1111111111111111", "A", "100.00", "USD");
        Card to = card("2222222222222222", "B", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));

        Transaction winner = new Transaction();
        winner.setId("winner-tx");
        winner.setStatus(TransactionStatus.COMPLETED);

        // First lookup finds nothing (so we proceed); by the time we try to
        // reserve the key, a concurrent request has already won and committed.
        when(transactionRepository.findByIdempotencyKey("race-key")).thenReturn(Optional.empty());
        when(idempotencyKeyService.reserve(any(Transaction.class))).thenReturn(false);
        when(idempotencyKeyService.findExisting("race-key")).thenReturn(Optional.of(winner));

        Transaction result = transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("10.00"), TransactionType.TRANSFER, "race-key");

        assertThat(result).isSameAs(winner);
        // Whoever loses the race must not touch balances at all.
        assertThat(from.getBalance()).isEqualByComparingTo("100.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
        verify(cardRepository, never()).save(any());
    }

    @Test
    void replaysTheSameFailureWhenRetriedWithTheSameKey() {
        Transaction existing = new Transaction();
        existing.setId("existing-tx");
        existing.setStatus(TransactionStatus.FAILED);
        existing.setFailureReason("Not enough money on sender card");
        when(transactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("10.00"), TransactionType.TRANSFER, "key-2"));

        assertThat(ex.getMessage()).isEqualTo("Not enough money on sender card");
        verify(cardRepository, never()).findById(any());
    }

    @Test
    void rejectsTransferThatWouldExceedTheDailyLimit() {
        Card from = card("1111111111111111", "A", "1000.00", "USD");
        from.setDailyLimit(new BigDecimal("100.00"));
        Card to = card("2222222222222222", "B", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));

        Transaction earlierToday = new Transaction();
        earlierToday.setRequestedAmount(new BigDecimal("80.00"));
        when(transactionRepository.findByFromCardAndStatusAndCreatedAtAfter(
                eq("1111111111111111"), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(List.of(earlierToday));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("30.00"), TransactionType.TRANSFER));

        assertThat(ex.getMessage()).isEqualTo("Daily transfer limit exceeded");
        assertThat(from.getBalance()).isEqualByComparingTo("1000.00");
        verify(cardRepository, never()).save(any());
    }

    @Test
    void allowsATransferThatStaysExactlyAtTheDailyLimit() {
        Card from = card("1111111111111111", "A", "1000.00", "USD");
        from.setDailyLimit(new BigDecimal("100.00"));
        Card to = card("2222222222222222", "B", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction earlierToday = new Transaction();
        earlierToday.setRequestedAmount(new BigDecimal("70.00"));
        when(transactionRepository.findByFromCardAndStatusAndCreatedAtAfter(
                eq("1111111111111111"), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(List.of(earlierToday));

        transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("30.00"), TransactionType.TRANSFER);

        assertThat(from.getBalance()).isEqualByComparingTo("970.00");
    }

    @Test
    void remainingDailyLimitIsNullWhenTheCardHasNoLimit() {
        Card c = card("1111111111111111", "A", "100.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(c));

        assertThat(transferService.getRemainingDailyLimit("1111111111111111")).isNull();
    }

    @Test
    void remainingDailyLimitSubtractsWhatWasAlreadySpentToday() {
        Card c = card("1111111111111111", "A", "1000.00", "USD");
        c.setDailyLimit(new BigDecimal("100.00"));
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(c));

        Transaction spent = new Transaction();
        spent.setRequestedAmount(new BigDecimal("35.00"));
        when(transactionRepository.findByFromCardAndStatusAndCreatedAtAfter(
                eq("1111111111111111"), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(List.of(spent));

        assertThat(transferService.getRemainingDailyLimit("1111111111111111")).isEqualByComparingTo("65.00");
    }

    @Test
    void remainingDailyLimitNeverGoesBelowZero() {
        Card c = card("1111111111111111", "A", "1000.00", "USD");
        c.setDailyLimit(new BigDecimal("50.00"));
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(c));

        Transaction spent = new Transaction();
        spent.setRequestedAmount(new BigDecimal("80.00"));
        when(transactionRepository.findByFromCardAndStatusAndCreatedAtAfter(
                eq("1111111111111111"), eq(TransactionStatus.COMPLETED), any()))
                .thenReturn(List.of(spent));

        assertThat(transferService.getRemainingDailyLimit("1111111111111111")).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsSelfTransfer() {
        assertThrows(IllegalArgumentException.class, () -> transferService.transferMoney(
                "1111111111111111", "1111111111111111", new BigDecimal("10.00"), TransactionType.TRANSFER));
    }

    @Test
    void convertReturnsSameAmountForIdenticalCurrency() {
        assertThat(transferService.convert(new BigDecimal("42.5"), "USD", "USD"))
                .isEqualByComparingTo("42.50");
    }

    @Test
    void convertAppliesTheFixedRateTable() {
        // EUR->USD at 1.10, GBP->USD at 1.30, and a cross rate EUR->GBP
        assertThat(transferService.convert(new BigDecimal("100"), "EUR", "USD")).isEqualByComparingTo("110.00");
        assertThat(transferService.convert(new BigDecimal("100"), "GBP", "USD")).isEqualByComparingTo("130.00");
        assertThat(transferService.convert(new BigDecimal("130"), "GBP", "EUR")).isEqualByComparingTo("153.64");
    }

    @Test
    void convertRejectsAnUnknownCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> transferService.convert(new BigDecimal("10"), "USD", "JPY"));
    }

    @Test
    void recordsCompletedTransactionWithAllFieldsPopulated() {
        Card from = card("1111111111111111", "A", "100.00", "EUR");
        Card to = card("2222222222222222", "A", "0.00", "USD");
        when(cardRepository.findById("1111111111111111")).thenReturn(Optional.of(from));
        when(cardRepository.findById("2222222222222222")).thenReturn(Optional.of(to));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        transferService.transferMoney(
                "1111111111111111", "2222222222222222", new BigDecimal("10.00"), TransactionType.EXCHANGE);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getFromCard()).isEqualTo("1111111111111111");
        assertThat(saved.getToCard()).isEqualTo("2222222222222222");
        assertThat(saved.getType()).isEqualTo(TransactionType.EXCHANGE);
        assertThat(saved.getRequestedAmount()).isEqualByComparingTo("10.00");
        assertThat(saved.getFromCurrency()).isEqualTo("EUR");
        assertThat(saved.getToCurrency()).isEqualTo("USD");
        assertThat(saved.getSettledAmount()).isEqualByComparingTo("11.00");
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void getAllCardsReturnsEveryCardFromTheRepository() {
        Card a = card("1111111111111111", "A", "10.00", "USD");
        Card b = card("2222222222222222", "B", "20.00", "EUR");
        when(cardRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(transferService.getAllCards()).containsExactly(a, b);
    }

    @Test
    void getHistoryPagePassesThroughThePageWindow() {
        Transaction tx = new Transaction();
        tx.setId("t1");
        Page<Transaction> page = new PageImpl<>(List.of(tx));
        when(transactionRepository.findByFromCardOrToCardOrderByCreatedAtDesc(
                eq("1111111111111111"), eq("1111111111111111"), any(PageRequest.class)))
                .thenReturn(page);

        Page<Transaction> result = transferService.getHistoryPage("1111111111111111", 2, 10);

        assertThat(result.getContent()).containsExactly(tx);
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(transactionRepository).findByFromCardOrToCardOrderByCreatedAtDesc(
                eq("1111111111111111"), eq("1111111111111111"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void getHistoryPageClampsNegativePageAndZeroSize() {
        when(transactionRepository.findByFromCardOrToCardOrderByCreatedAtDesc(
                eq("1111111111111111"), eq("1111111111111111"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transferService.getHistoryPage("1111111111111111", -5, 0);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(transactionRepository).findByFromCardOrToCardOrderByCreatedAtDesc(
                eq("1111111111111111"), eq("1111111111111111"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void getHistoryPageClampsSizeAboveTheMax() {
        when(transactionRepository.findByFromCardOrToCardOrderByCreatedAtDesc(
                eq("1111111111111111"), eq("1111111111111111"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transferService.getHistoryPage("1111111111111111", 0, 1_000_000);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(transactionRepository).findByFromCardOrToCardOrderByCreatedAtDesc(
                eq("1111111111111111"), eq("1111111111111111"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(TransferService.MAX_PAGE_SIZE);
    }
}

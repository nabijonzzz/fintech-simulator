# Echo — Fintech Transaction Simulator

[![CI](https://github.com/nabijonzzz/Fintech-SImulator/actions/workflows/ci.yml/badge.svg)](https://github.com/nabijonzzz/Fintech-SImulator/actions/workflows/ci.yml)

A simulated banking backend built with Spring Boot — card balances, transfers, and currency exchange, backed by a real transaction ledger instead of just mutating numbers in a database.

It's a simulator, not a real bank: no login system, no real money, exchange rates are a fixed table instead of a live feed. I wanted to spend the time on getting the transaction logic right rather than building out a full auth system.

## What it does

- Card accounts in USD/EUR/GBP with persistent balances
- Shows your total balance across all cards, converted to USD
- Filter your accounts by currency
- Transfers between cards, with automatic currency conversion
- Warns you before you hit send if you don't have enough money — no need to wait for the server to say no
- Exchange between your own cards — the rate you see before confirming is pulled from the same endpoint that actually settles the trade, so the preview can't lie to you
- Every transfer/exchange gets logged to a transaction ledger, successful or not, so there's an actual audit trail instead of just a balance that silently changed
- Safe to retry: if a transfer/exchange request gets sent twice with the same key (e.g. your connection drops right after you hit send and your browser retries), it won't charge you twice — it just hands back the original result
- Each card has a daily outgoing limit — a basic fraud/risk control like real card issuers use — with the remaining amount shown right on the Transfer form
- A Markets tab with a few stocks that move on their own — clearly labeled as fake data, just for looks

## Stack

Java 17, Spring Boot 3.5 (Web, Data JPA, Validation), H2, JUnit 5 + Mockito. Frontend is one HTML file with vanilla JS and Bootstrap — no build step.

## Running it

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) — it seeds a few demo cards on first run and keeps balances in `data/echodb.mv.db` between restarts.

```bash
./mvnw test
```

The suite (50+ tests) mixes fast Mockito unit tests for the service logic, the audit logger and the exception handler, DTO mapping and validation tests, and integration tests that run the real app against an in-memory H2 database — covering transfers, idempotent retries, daily limits and pagination end to end.

## API

| Method | Endpoint | What it does |
|---|---|---|
| GET | `/api/cards` | List all cards |
| GET | `/api/card/{cardNumber}` | One card's details |
| GET | `/api/transactions/{cardNumber}?page&size` | Paginated transaction history for a card (size capped at 500) |
| GET | `/api/limit/{cardNumber}` | Remaining daily transfer limit for a card |
| GET | `/api/rate/convert?amount&from&to` | Convert an amount between currencies |
| POST | `/api/transfer` | `{ fromCard, toCard, amount, idempotencyKey? }` |
| POST | `/api/exchange` | `{ fromCard, toCard, amount, idempotencyKey? }` |

## The part I'm proudest of

Getting a failed transaction to still show up in the ledger was the trickiest bit — normally when a transfer fails partway through (insufficient funds, say), everything in that request rolls back, including any log of it ever happening. But a real payment system needs to know *why* something got declined. So the failure log runs in its own transaction (`@Transactional(propagation = REQUIRES_NEW)`) on a separate service, called as a genuine call between beans rather than `this.method()` — that second part matters, because Spring's `@Transactional` gets silently ignored on self-invocation, which is a classic gotcha.

The idempotency key works the same way for both outcomes: before doing anything, `transferMoney` checks whether a transaction with that key already exists and, if so, just hands back whatever happened last time — success or failure — instead of touching a balance again.

That first check alone isn't enough under real concurrency, though — two identical requests could both pass the "not found" check before either one saves. So the actual write order is: build the transaction record and `saveAndFlush` it — under the column's unique constraint — *before* touching any balance. Whichever request loses that race gets a `DataIntegrityViolationException` right there, with nothing moved yet, and just re-reads and returns the winner's row. The one that wins proceeds to update the balances as normal. That ordering is what makes it safe — if the balance update happened first, the loser would have already moved money by the time its insert failed.

The daily limit check works off the same ledger rather than a separate running total: it sums `requestedAmount` for every `COMPLETED` transaction sent from that card since midnight, and compares against the card's `dailyLimit`. No extra counter to keep in sync, no risk of it drifting from what the ledger actually says happened — the ledger is already the source of truth for everything else, so it made sense to lean on it here too.

## API responses, not raw entities

The controllers never hand back JPA entities directly — every response goes through a DTO (`CardResponse`, `TransactionResponse`, `PagedResponse<T>`). It's a small thing but it matters: the entity is free to grow internal-only fields (like the idempotency key) without those leaking into the API, and the wire format stays stable even if the entity's shape changes later.

Transaction history is paginated for the same reason a raw entity dump would've been a problem: an account's ledger only grows, so returning the whole thing on every request doesn't scale. `GET /api/transactions/{cardNumber}` takes `page`/`size` query params and returns a `PagedResponse` (content + page/size/totalElements/totalPages), with `size` capped at 500 server-side so a bad or malicious query can't force one huge fetch. The frontend shows the 10 most recent by default with a "Load more" button, and CSV export walks every page in the background so the export still captures full history even though the on-screen list doesn't.

## License

MIT — see [LICENSE](LICENSE).

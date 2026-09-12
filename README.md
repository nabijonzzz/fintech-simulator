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

## API

| Method | Endpoint | What it does |
|---|---|---|
| GET | `/api/cards` | List all cards |
| GET | `/api/card/{cardNumber}` | One card's details |
| GET | `/api/transactions/{cardNumber}` | Transaction history for a card |
| GET | `/api/rate/convert?amount&from&to` | Convert an amount between currencies |
| POST | `/api/transfer` | `{ fromCard, toCard, amount, idempotencyKey? }` |
| POST | `/api/exchange` | `{ fromCard, toCard, amount, idempotencyKey? }` |

## The part I'm proudest of

Getting a failed transaction to still show up in the ledger was the trickiest bit — normally when a transfer fails partway through (insufficient funds, say), everything in that request rolls back, including any log of it ever happening. But a real payment system needs to know *why* something got declined. So the failure log runs in its own transaction (`@Transactional(propagation = REQUIRES_NEW)`) on a separate service, called as a genuine call between beans rather than `this.method()` — that second part matters, because Spring's `@Transactional` gets silently ignored on self-invocation, which is a classic gotcha.

The idempotency key works the same way for both outcomes: before doing anything, `transferMoney` checks whether a transaction with that key already exists and, if so, just hands back whatever happened last time — success or failure — instead of touching a balance again. The one thing I didn't build out: the check-then-insert isn't race-safe under real concurrency (two identical requests arriving at the exact same instant could both pass the "not found" check before either saves). The unique constraint on the column stops a bad row from ever being written, but a proper fix would catch that constraint violation and re-read the winning row rather than let it surface as a raw error.

## License

MIT — see [LICENSE](LICENSE).

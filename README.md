# Concurrent Ledger Service

An in-memory, double-entry ledger service: create accounts, post transfers
between them, read balances - safely under heavy concurrent load, with
idempotent retries.

## Run it

```
./mvnw spring-boot:run
```

(`mvnw.cmd spring-boot:run` on Windows.) Starts on port 8080. All state is
in memory and is lost on restart.

## API

### Create an account

```
curl -X POST localhost:8080/accounts \
  -H 'Content-Type: application/json' \
  -d '{"accountId": "alice", "initialBalance": 10000}'
```

`accountId` is optional (a UUID is generated if omitted); `initialBalance`
is optional, defaults to `0`. Amounts are integer **cents**. Returns `201`
with `{"accountId": "alice", "balance": 10000}`. A duplicate `accountId`
returns `409`.

### Post a transfer

```
curl -X POST localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6c1b1b1e-...' \
  -d '{"fromAccountId": "alice", "toAccountId": "bob", "amount": 2500}'
```

`Idempotency-Key` is an optional header. Returns `201` with the transfer
outcome. Errors: `404` unknown account, `422` insufficient funds, `400`
invalid amount (zero/negative, or `fromAccountId == toAccountId`), `409`
if the idempotency key was already used with a different request body.

Retrying the exact same request with the same `Idempotency-Key` - even
while the first attempt is still being processed - returns the same
outcome and the transfer is applied at most once.

### Read a balance

```
curl localhost:8080/accounts/alice
```

Returns `200` with `{"accountId": "alice", "balance": 7500}`, or `404` if
the account doesn't exist.

## Guarantees

- **Atomic transfers**: a transfer either fully debits one account and
  credits the other, or changes nothing.
- **Balances never go negative.**
- **Idempotent retries**: a repeated `Idempotency-Key` is applied at most
  once. A retry that arrives while the original request is still in
  flight blocks until it finishes and returns the same result, instead of
  re-running the transfer or racing it.
- **Correct under concurrency**: any number of threads can hit the same
  account(s) at once with no lost updates and no double-spends, under any
  interleaving.
- **Unrelated transfers run in parallel**: a transfer only blocks on the
  two accounts it touches, never on unrelated accounts.

## Design

The ledger core (`com.concurrent_ledger_service.ledger`) is plain Java
with no Spring dependency - it's unit tested directly, without HTTP. A
thin `web` package adapts it to a Spring MVC REST API.

**Locking.** Each `Account` has its own `ReentrantLock`. A transfer
resolves both accounts, then always acquires the two locks in a fixed
order (by account ID, regardless of debit/credit direction) before
re-checking the balance and mutating. Because every thread agrees on the
same lock order, cycles in the wait-for graph - and therefore deadlock -
are structurally impossible. Because the balance check and the mutation
happen atomically under both locks, two transfers sharing an account can
never interleave their check-then-mutate steps, which is what rules out
lost updates and double-spends. Transfers on disjoint account pairs never
contend for the same locks, so they proceed fully in parallel.

**Idempotency.** A `ConcurrentHashMap<key, (request, CompletableFuture)>`
tracks in-flight and completed transfers by idempotency key. The first
caller for a key ("claimer") runs the transfer and completes the future
with the result (or the failure, so a cached failure is replayed rather
than retried); every later caller for the same key ("joiner") blocks on
that future instead of running the transfer again. This is checked
*before* any account lock is taken and never holds an account lock while
waiting, so it can't deadlock against the account-locking layer - a
joiner only ever waits on a future, never on a lock, and the transfer
logic never touches the idempotency store.

**Storage abstraction.** `AccountStore` and `IdempotencyStore` are small
interfaces; `InMemoryAccountStore` / `InMemoryIdempotencyStore` are the
only implementations here. A real storage engine could implement the same
interfaces, though it would reasonably replace the in-JVM per-account
locking with row-level locking or transactions of its own - the
interfaces abstract *where the data lives*, not the concurrency-control
strategy built on top.

## Trade-offs and limitations

- **In-memory, single-process only.** No persistence, no clustering - the
  locks and maps are JVM-local. A multi-instance deployment would need a
  shared lock/transaction manager, which is out of scope here.
- **The idempotency store grows without bound** (no TTL/eviction). Fine
  at this scope; a real system would expire entries after some window
  (e.g. 24h).
- **Money is `long` cents**, not `BigDecimal` - exact and simple for a
  single implicit currency with only addition/subtraction; overflow is
  guarded with `Math.addExact`/`subtractExact`. Multi-currency is out of
  scope.
- **No authentication/authorization**, no transfer history/listing
  endpoint, no account closing - kept out to stay within scope.

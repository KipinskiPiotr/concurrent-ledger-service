package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Demonstrates the core concurrency guarantee: many threads hammering the
 * SAME two accounts, in both directions, with no lost updates and no
 * double-spend under any thread interleaving. A naive implementation
 * (unsynchronized, or a stale-read check-then-mutate) would fail the
 * conservation/exact-balance assertions here intermittently.
 */
class LedgerCoreConcurrentSameAccountsTest {

    private static final long INITIAL_BALANCE = 1_000_000;
    private static final long TRANSFER_AMOUNT = 1;
    private static final int THREADS = 32;
    private static final int TRANSFERS_PER_THREAD = 500;

    @Test
    void manyConcurrentTransfersOnTheSameAccountPairPreserveTotalAndNeverGoNegative() throws Exception {
        InMemoryAccountStore store = new InMemoryAccountStore();
        LedgerCore ledger = new LedgerCore(store, new InMemoryIdempotencyStore());
        ledger.createAccount("A", Money.ofCents(INITIAL_BALANCE));
        ledger.createAccount("B", Money.ofCents(INITIAL_BALANCE));

        AtomicLong aToB = new AtomicLong();
        AtomicLong bToA = new AtomicLong();

        // Balances stay far from zero for the whole run (worst-case imbalance
        // is bounded by THREADS * TRANSFERS_PER_THREAD, well under
        // INITIAL_BALANCE), so InsufficientFundsException is not expected here
        // — this test is purely about interleaving correctness.
        List<Callable<Void>> tasks = IntStream.range(0, THREADS * TRANSFERS_PER_THREAD)
                .mapToObj(i -> (Callable<Void>) () -> {
                    boolean aToBDirection = i % 2 == 0;
                    String from = aToBDirection ? "A" : "B";
                    String to = aToBDirection ? "B" : "A";
                    ledger.transfer(new TransferRequest(from, to, Money.ofCents(TRANSFER_AMOUNT)), null);
                    (aToBDirection ? aToB : bToA).incrementAndGet();
                    return null;
                })
                .collect(Collectors.toList());

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        Money balanceA = ledger.getBalance("A");
        Money balanceB = ledger.getBalance("B");

        assertThat(balanceA.plus(balanceB)).isEqualTo(Money.ofCents(2 * INITIAL_BALANCE));
        assertThat(balanceA).isEqualTo(Money.ofCents(
                INITIAL_BALANCE - aToB.get() * TRANSFER_AMOUNT + bToA.get() * TRANSFER_AMOUNT));
        assertThat(balanceB).isEqualTo(Money.ofCents(
                INITIAL_BALANCE - bToA.get() * TRANSFER_AMOUNT + aToB.get() * TRANSFER_AMOUNT));
        assertThat(balanceA.cents()).isGreaterThanOrEqualTo(0);
        assertThat(balanceB.cents()).isGreaterThanOrEqualTo(0);
    }
}

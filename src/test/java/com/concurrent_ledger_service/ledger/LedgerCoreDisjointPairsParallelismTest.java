package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Transfers on disjoint account pairs must not serialize on each other.
 * Each transfer is artificially slowed down (simulating real work done while
 * holding the account locks); if the implementation serialized all transfers
 * behind one global lock, the wall-clock time would scale with the number of
 * pairs instead of staying close to a single transfer's delay.
 */
class LedgerCoreDisjointPairsParallelismTest {

    private static final int PAIRS = 16;
    private static final long DELAY_MILLIS = 200;

    @Test
    void transfersOnDisjointAccountPairsRunConcurrentlyNotSerially() throws Exception {
        InMemoryAccountStore realStore = new InMemoryAccountStore();
        for (int i = 0; i < PAIRS; i++) {
            realStore.createAccount("from-" + i, Money.ofCents(1_000));
            realStore.createAccount("to-" + i, Money.ofCents(0));
        }
        AccountStore slowStore = new ArtificiallySlowAccountStore(realStore, DELAY_MILLIS);
        LedgerCore ledger = new LedgerCore(slowStore, new InMemoryIdempotencyStore());

        ExecutorService pool = Executors.newFixedThreadPool(PAIRS);
        CyclicBarrier barrier = new CyclicBarrier(PAIRS);
        try {
            long startNanos = System.nanoTime();

            var futures = IntStream.range(0, PAIRS)
                    .<Future<?>>mapToObj(i -> pool.submit(() -> {
                        barrier.await();
                        ledger.transfer(new TransferRequest("from-" + i, "to-" + i, Money.ofCents(100)), null);
                        return null;
                    }))
                    .toList();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            // Fully serialized would take roughly PAIRS * DELAY_MILLIS; allow
            // generous headroom above one transfer's delay to avoid flakiness
            // while still clearly distinguishing "parallel" from "serial".
            assertThat(elapsedMillis).isLessThan(PAIRS * DELAY_MILLIS / 2);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        for (int i = 0; i < PAIRS; i++) {
            assertThat(ledger.getBalance("from-" + i)).isEqualTo(Money.ofCents(900));
            assertThat(ledger.getBalance("to-" + i)).isEqualTo(Money.ofCents(100));
        }
    }

    private static final class ArtificiallySlowAccountStore implements AccountStore {
        private final AccountStore delegate;
        private final long delayMillis;

        ArtificiallySlowAccountStore(AccountStore delegate, long delayMillis) {
            this.delegate = delegate;
            this.delayMillis = delayMillis;
        }

        @Override
        public Account createAccount(String accountId, Money initialBalance) {
            return delegate.createAccount(accountId, initialBalance);
        }

        @Override
        public Optional<Account> findById(String accountId) {
            return delegate.findById(accountId);
        }

        @Override
        public Account getOrThrow(String accountId) {
            try {
                Thread.sleep(delayMillis / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return delegate.getOrThrow(accountId);
        }

        @Override
        public boolean exists(String accountId) {
            return delegate.exists(accountId);
        }
    }
}

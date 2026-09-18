package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Fires A->B and B->A transfers at each other concurrently from a large
 * thread pool, released simultaneously via a barrier to maximize lock
 * contention on the same two accounts in opposite orders. If the locking
 * strategy didn't use a fixed, consistent order, this reliably deadlocks;
 * a hang here (bounded await timing out) fails the test.
 */
class LedgerCoreDeadlockFreedomTest {

    private static final int THREADS = 64;
    private static final int TRANSFERS_PER_THREAD = 200;

    @Test
    void oppositeDirectionTransfersUnderContentionNeverDeadlock() throws Exception {
        InMemoryAccountStore store = new InMemoryAccountStore();
        LedgerCore ledger = new LedgerCore(store, new InMemoryIdempotencyStore());
        ledger.createAccount("A", Money.ofCents(1_000_000));
        ledger.createAccount("B", Money.ofCents(1_000_000));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            boolean aToBDirection = t % 2 == 0;
            String from = aToBDirection ? "A" : "B";
            String to = aToBDirection ? "B" : "A";
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                    ledger.transfer(new TransferRequest(from, to, Money.ofCents(1)), null);
                }
                return null;
            }));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        try {
            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ledger.getBalance("A").plus(ledger.getBalance("B")))
                .isEqualTo(Money.ofCents(2_000_000));
    }
}

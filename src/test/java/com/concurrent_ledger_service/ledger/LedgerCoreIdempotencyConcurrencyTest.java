package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.concurrent_ledger_service.ledger.exceptions.InsufficientFundsException;

/**
 * Proves the "retry arrives while the original request is still in flight"
 * requirement, not just "retry arrives after it's done". The account store
 * is wrapped to add an artificial delay inside a transfer attempt, giving
 * concurrently-submitted joiner calls time to arrive and block on the
 * claimer's in-flight future before it completes — a race where a naive
 * "check a completed-results map" implementation would instead let every
 * joiner re-run the transfer.
 */
class LedgerCoreIdempotencyConcurrencyTest {

    private static final int JOINERS = 20;

    @Test
    void concurrentRetriesWithSameKeyBlockAndShareOneOutcome() throws Exception {
        InMemoryAccountStore realStore = new InMemoryAccountStore();
        realStore.createAccount("alice", Money.ofCents(1_000));
        realStore.createAccount("bob", Money.ofCents(0));

        AtomicInteger accountLookups = new AtomicInteger();
        AccountStore slowStore = new DelegatingSlowAccountStore(realStore, accountLookups, 300);
        LedgerCore ledger = new LedgerCore(slowStore, new InMemoryIdempotencyStore());

        TransferRequest request = new TransferRequest("alice", "bob", Money.ofCents(100));
        String key = "retry-key-success";

        ExecutorService pool = Executors.newFixedThreadPool(JOINERS + 1);
        try {
            CountDownLatch claimerStarted = new CountDownLatch(1);
            Future<TransferResult> claimerFuture = pool.submit(() -> {
                claimerStarted.countDown();
                return ledger.transfer(request, key);
            });

            assertThat(claimerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // Let the claimer register the idempotency entry and enter the slow
            // account lookup before the joiners show up.
            Thread.sleep(50);

            List<Future<TransferResult>> joinerFutures = new ArrayList<>();
            for (int i = 0; i < JOINERS; i++) {
                joinerFutures.add(pool.submit(() -> ledger.transfer(request, key)));
            }

            Set<TransferResult> allResults = new HashSet<>();
            allResults.add(claimerFuture.get(5, TimeUnit.SECONDS));
            for (Future<TransferResult> future : joinerFutures) {
                allResults.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(allResults).hasSize(1);
            // getOrThrow is called twice per attempt (from + to); exactly 2 means
            // exactly one transfer attempt ran despite 21 concurrent callers.
            assertThat(accountLookups.get()).isEqualTo(2);
            assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(900));
            assertThat(ledger.getBalance("bob")).isEqualTo(Money.ofCents(100));
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentRetriesReplayAFailureInsteadOfRetryingIt() throws Exception {
        InMemoryAccountStore realStore = new InMemoryAccountStore();
        realStore.createAccount("alice", Money.ZERO);
        realStore.createAccount("bob", Money.ZERO);

        AtomicInteger accountLookups = new AtomicInteger();
        AccountStore slowStore = new DelegatingSlowAccountStore(realStore, accountLookups, 200);
        LedgerCore ledger = new LedgerCore(slowStore, new InMemoryIdempotencyStore());

        TransferRequest request = new TransferRequest("alice", "bob", Money.ofCents(100));
        String key = "retry-key-failure";

        ExecutorService pool = Executors.newFixedThreadPool(JOINERS + 1);
        try {
            CountDownLatch claimerStarted = new CountDownLatch(1);
            Future<Exception> claimerFuture = pool.submit(() -> {
                claimerStarted.countDown();
                return callAndCaptureException(ledger, request, key);
            });

            assertThat(claimerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(50);

            List<Future<Exception>> joinerFutures = new ArrayList<>();
            for (int i = 0; i < JOINERS; i++) {
                joinerFutures.add(pool.submit(() -> callAndCaptureException(ledger, request, key)));
            }

            assertThat(claimerFuture.get(5, TimeUnit.SECONDS)).isInstanceOf(InsufficientFundsException.class);
            for (Future<Exception> future : joinerFutures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isInstanceOf(InsufficientFundsException.class);
            }

            assertThat(accountLookups.get()).isEqualTo(2);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Exception callAndCaptureException(LedgerCore ledger, TransferRequest request, String key) {
        try {
            ledger.transfer(request, key);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private static final class DelegatingSlowAccountStore implements AccountStore {
        private final AccountStore delegate;
        private final AtomicInteger lookupCount;
        private final long delayMillis;

        DelegatingSlowAccountStore(AccountStore delegate, AtomicInteger lookupCount, long delayMillis) {
            this.delegate = delegate;
            this.lookupCount = lookupCount;
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
            lookupCount.incrementAndGet();
            try {
                Thread.sleep(delayMillis);
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

package com.concurrent_ledger_service.ledger;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import com.concurrent_ledger_service.ledger.exceptions.IdempotencyConflictException;
import com.concurrent_ledger_service.ledger.exceptions.InvalidAmountException;

/**
 * Plain-Java ledger core: no Spring, no HTTP, no framework dependencies.
 *
 * Two independent concerns compose here without risking a cross-dimension
 * deadlock: idempotency-key deduplication (see {@link #transfer}) and
 * per-account locking (see {@link #executeTransfer}). A joiner waiting on a
 * duplicate idempotency key blocks only on a {@link java.util.concurrent.CompletableFuture};
 * it never holds an account lock while doing so. The locking transfer logic
 * never touches the idempotency store. So a wait-for cycle spanning both
 * dimensions cannot form.
 */
public final class LedgerCore implements LedgerService {

    private final AccountStore accountStore;
    private final IdempotencyStore idempotencyStore;

    public LedgerCore(AccountStore accountStore, IdempotencyStore idempotencyStore) {
        this.accountStore = accountStore;
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    public Account createAccount(String accountId, Money initialBalance) {
        if (accountId == null || accountId.isBlank()) {
            throw new InvalidAmountException("accountId must not be blank");
        }
        return accountStore.createAccount(accountId, initialBalance);
    }

    @Override
    public Money getBalance(String accountId) {
        Account account = accountStore.getOrThrow(accountId);
        account.getLock().lock();
        try {
            return account.getBalance();
        } finally {
            account.getLock().unlock();
        }
    }

    @Override
    public TransferResult transfer(TransferRequest request, String idempotencyKey) {
        if (idempotencyKey == null) {
            return executeTransfer(request);
        }

        IdempotencyStore.Entry entry = idempotencyStore.claimOrJoin(idempotencyKey, request);
        if (entry.claimedByCaller()) {
            try {
                TransferResult result = executeTransfer(request);
                entry.future().complete(result);
                return result;
            } catch (RuntimeException e) {
                entry.future().completeExceptionally(e);
                throw e;
            }
        }

        if (!entry.storedRequest().equals(request)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        try {
            return entry.future().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /** No knowledge of idempotency keys — purely account resolution, locking, and mutation. */
    private TransferResult executeTransfer(TransferRequest request) {
        if (!request.amount().isPositive()) {
            throw new InvalidAmountException("Transfer amount must be positive: " + request.amount().cents());
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidAmountException("Cannot transfer to the same account: " + request.fromAccountId());
        }

        Account from = accountStore.getOrThrow(request.fromAccountId());
        Account to = accountStore.getOrThrow(request.toAccountId());

        // Fixed lock order (by account id) across ALL transfers, regardless of
        // debit/credit direction, is what makes concurrent A->B and B->A
        // transfers deadlock-free: every thread agrees on which of the two
        // locks to take first.
        Account first = from.compareTo(to) <= 0 ? from : to;
        Account second = (first == from) ? to : from;

        first.getLock().lock();
        try {
            second.getLock().lock();
            try {
                // Balance check happens under both locks, atomically with the
                // mutation — checking beforehand would be a stale-read race.
                from.debit(request.amount());
                to.credit(request.amount());
            } finally {
                second.getLock().unlock();
            }
        } finally {
            first.getLock().unlock();
        }

        return new TransferResult(
                UUID.randomUUID().toString(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                TransferResult.STATUS_COMPLETED);
    }
}

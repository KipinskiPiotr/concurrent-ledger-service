package com.concurrent_ledger_service.ledger;

import java.util.concurrent.CompletableFuture;

/**
 * Deduplicates concurrent/retried operations keyed by a client-supplied
 * idempotency key. The first caller for a given key "claims" it and is
 * responsible for completing the returned future (with either the result or
 * the failure) once it finishes executing; every other caller for that same
 * key "joins" the existing claim and gets back the same future to await,
 * so a retry that arrives while the original is still in flight blocks
 * until it completes instead of running a second time.
 */
public interface IdempotencyStore {

    Entry claimOrJoin(String idempotencyKey, TransferRequest request);

    /**
     * @param claimedByCaller whether this call is the one that must execute the
     *                        request and complete {@code future}; if false, the
     *                        caller must instead await {@code future}.
     * @param storedRequest   the request originally associated with this key,
     *                        for the joining caller to check it matches its own.
     * @param future          completes with the outcome of the original request.
     */
    record Entry(boolean claimedByCaller, TransferRequest storedRequest, CompletableFuture<TransferResult> future) {
    }
}

package com.concurrent_ledger_service.ledger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private record StoredEntry(TransferRequest request, CompletableFuture<TransferResult> future) {
    }

    private final ConcurrentHashMap<String, StoredEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Entry claimOrJoin(String idempotencyKey, TransferRequest request) {
        StoredEntry candidate = new StoredEntry(request, new CompletableFuture<>());
        StoredEntry existing = entries.putIfAbsent(idempotencyKey, candidate);
        if (existing == null) {
            return new Entry(true, candidate.request(), candidate.future());
        }
        return new Entry(false, existing.request(), existing.future());
    }
}

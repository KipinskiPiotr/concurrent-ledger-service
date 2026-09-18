package com.concurrent_ledger_service.ledger;

/**
 * A request to move money from one account to another. Deliberately has no
 * notion of idempotency keys — that is a transport/retry concern layered on
 * top by {@link LedgerCore}, not part of what a transfer *is*.
 */
public record TransferRequest(String fromAccountId, String toAccountId, Money amount) {
}

package com.concurrent_ledger_service.ledger.exceptions;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key already used with a different request: " + idempotencyKey);
    }
}

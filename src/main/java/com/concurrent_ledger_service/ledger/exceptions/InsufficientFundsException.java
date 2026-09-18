package com.concurrent_ledger_service.ledger.exceptions;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId) {
        super("Insufficient funds in account: " + accountId);
    }
}

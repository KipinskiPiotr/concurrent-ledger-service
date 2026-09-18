package com.concurrent_ledger_service.ledger.exceptions;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(String accountId) {
        super("Account already exists: " + accountId);
    }
}

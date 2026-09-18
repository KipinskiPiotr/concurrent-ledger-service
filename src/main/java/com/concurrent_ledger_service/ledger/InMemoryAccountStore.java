package com.concurrent_ledger_service.ledger;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.concurrent_ledger_service.ledger.exceptions.AccountAlreadyExistsException;
import com.concurrent_ledger_service.ledger.exceptions.AccountNotFoundException;

public final class InMemoryAccountStore implements AccountStore {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Account createAccount(String accountId, Money initialBalance) {
        Account created = new Account(accountId, initialBalance);
        Account previous = accounts.putIfAbsent(accountId, created);
        if (previous != null) {
            throw new AccountAlreadyExistsException(accountId);
        }
        return created;
    }

    @Override
    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public Account getOrThrow(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    @Override
    public boolean exists(String accountId) {
        return accounts.containsKey(accountId);
    }
}

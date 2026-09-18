package com.concurrent_ledger_service.ledger;

import java.util.concurrent.locks.ReentrantLock;

import com.concurrent_ledger_service.ledger.exceptions.InsufficientFundsException;

/**
 * A single account's mutable balance, guarded by its own lock. Callers that
 * need to move money between two accounts atomically must hold both accounts'
 * locks for the whole check-then-mutate sequence (see LedgerCore) — a single
 * account's lock only protects that account in isolation.
 *
 * Comparable by id so callers can derive a fixed, total lock-acquisition
 * order across any pair of accounts, which is what makes concurrent
 * transfers deadlock-free.
 */
public final class Account implements Comparable<Account> {

    private final String id;
    private final ReentrantLock lock = new ReentrantLock();
    private Money balance;

    public Account(String id, Money initialBalance) {
        this.id = id;
        this.balance = initialBalance;
    }

    public String getId() {
        return id;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    /** Caller must hold {@link #getLock()}. */
    public Money getBalance() {
        return balance;
    }

    /** Caller must hold {@link #getLock()}. */
    public void debit(Money amount) {
        if (!balance.isGreaterThanOrEqualTo(amount)) {
            throw new InsufficientFundsException(id);
        }
        balance = balance.minus(amount);
    }

    /** Caller must hold {@link #getLock()}. */
    public void credit(Money amount) {
        balance = balance.plus(amount);
    }

    @Override
    public int compareTo(Account other) {
        return this.id.compareTo(other.id);
    }
}

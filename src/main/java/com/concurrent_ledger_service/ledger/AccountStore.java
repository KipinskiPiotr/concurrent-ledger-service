package com.concurrent_ledger_service.ledger;

import java.util.Optional;

/**
 * Storage abstraction for accounts. The in-memory implementation backs this
 * with a concurrent map; a real storage engine could implement this same
 * interface, though it would reasonably replace the in-JVM per-account
 * locking in {@link LedgerCore} with row-level locking or transactions of
 * its own — this interface abstracts *where accounts live*, not the
 * concurrency-control strategy built on top of it.
 */
public interface AccountStore {

    Account createAccount(String accountId, Money initialBalance);

    Optional<Account> findById(String accountId);

    Account getOrThrow(String accountId);

    boolean exists(String accountId);
}

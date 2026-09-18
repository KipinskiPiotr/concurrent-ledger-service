package com.concurrent_ledger_service.ledger;

public interface LedgerService {

    Account createAccount(String accountId, Money initialBalance);

    Money getBalance(String accountId);

    /**
     * @param idempotencyKey optional; when present, a repeated call with the
     *                       same key returns the original outcome instead of
     *                       re-executing, even if the original is still running.
     */
    TransferResult transfer(TransferRequest request, String idempotencyKey);
}

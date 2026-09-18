package com.concurrent_ledger_service.ledger;

public record TransferResult(
        String transferId,
        String fromAccountId,
        String toAccountId,
        Money amount,
        String status) {

    public static final String STATUS_COMPLETED = "COMPLETED";
}

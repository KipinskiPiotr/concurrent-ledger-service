package com.concurrent_ledger_service.web.dto;

public record TransferResponse(
        String transferId,
        String fromAccountId,
        String toAccountId,
        long amount,
        String status) {
}

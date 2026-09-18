package com.concurrent_ledger_service.web.dto;

/** @param amount in cents. */
public record TransferHttpRequest(String fromAccountId, String toAccountId, long amount) {
}

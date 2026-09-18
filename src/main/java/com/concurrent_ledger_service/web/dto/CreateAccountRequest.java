package com.concurrent_ledger_service.web.dto;

/**
 * @param accountId      optional; a UUID is generated when omitted.
 * @param initialBalance optional, in cents; defaults to 0.
 */
public record CreateAccountRequest(String accountId, Long initialBalance) {
}

package com.concurrent_ledger_service.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.concurrent_ledger_service.ledger.Account;
import com.concurrent_ledger_service.ledger.LedgerService;
import com.concurrent_ledger_service.ledger.Money;
import com.concurrent_ledger_service.web.dto.AccountResponse;
import com.concurrent_ledger_service.web.dto.CreateAccountRequest;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final LedgerService ledgerService;

    public AccountController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestBody(required = false) CreateAccountRequest request) {
        String requestedId = request != null ? request.accountId() : null;
        String accountId = (requestedId == null || requestedId.isBlank())
                ? UUID.randomUUID().toString()
                : requestedId;
        long initialCents = (request != null && request.initialBalance() != null) ? request.initialBalance() : 0L;

        Account account = ledgerService.createAccount(accountId, Money.ofCents(initialCents));
        Money balance = ledgerService.getBalance(account.getId());

        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(new AccountResponse(account.getId(), balance.cents()));
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        Money balance = ledgerService.getBalance(accountId);
        return new AccountResponse(accountId, balance.cents());
    }
}

package com.concurrent_ledger_service.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.concurrent_ledger_service.ledger.AccountStore;
import com.concurrent_ledger_service.ledger.IdempotencyStore;
import com.concurrent_ledger_service.ledger.InMemoryAccountStore;
import com.concurrent_ledger_service.ledger.InMemoryIdempotencyStore;
import com.concurrent_ledger_service.ledger.LedgerCore;
import com.concurrent_ledger_service.ledger.LedgerService;

@Configuration
public class LedgerConfig {

    @Bean
    public AccountStore accountStore() {
        return new InMemoryAccountStore();
    }

    @Bean
    public IdempotencyStore idempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    public LedgerService ledgerService(AccountStore accountStore, IdempotencyStore idempotencyStore) {
        return new LedgerCore(accountStore, idempotencyStore);
    }
}

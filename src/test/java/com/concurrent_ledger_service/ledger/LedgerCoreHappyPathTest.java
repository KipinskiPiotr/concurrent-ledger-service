package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerCoreHappyPathTest {

    private LedgerCore ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerCore(new InMemoryAccountStore(), new InMemoryIdempotencyStore());
    }

    @Test
    void createsAccountWithInitialBalance() {
        ledger.createAccount("alice", Money.ofCents(10_000));

        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(10_000));
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        ledger.createAccount("alice", Money.ofCents(10_000));
        ledger.createAccount("bob", Money.ofCents(0));

        TransferResult result = ledger.transfer(
                new TransferRequest("alice", "bob", Money.ofCents(2_500)), null);

        assertThat(result.status()).isEqualTo(TransferResult.STATUS_COMPLETED);
        assertThat(result.fromAccountId()).isEqualTo("alice");
        assertThat(result.toAccountId()).isEqualTo("bob");
        assertThat(result.amount()).isEqualTo(Money.ofCents(2_500));
        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(7_500));
        assertThat(ledger.getBalance("bob")).isEqualTo(Money.ofCents(2_500));
    }

    @Test
    void transferCanDrainAnAccountToExactlyZero() {
        ledger.createAccount("alice", Money.ofCents(500));
        ledger.createAccount("bob", Money.ofCents(0));

        ledger.transfer(new TransferRequest("alice", "bob", Money.ofCents(500)), null);

        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ZERO);
        assertThat(ledger.getBalance("bob")).isEqualTo(Money.ofCents(500));
    }
}

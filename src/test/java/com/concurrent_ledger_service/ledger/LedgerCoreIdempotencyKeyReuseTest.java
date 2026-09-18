package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.concurrent_ledger_service.ledger.exceptions.IdempotencyConflictException;

class LedgerCoreIdempotencyKeyReuseTest {

    private LedgerCore ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerCore(new InMemoryAccountStore(), new InMemoryIdempotencyStore());
        ledger.createAccount("alice", Money.ofCents(1_000));
        ledger.createAccount("bob", Money.ofCents(0));
        ledger.createAccount("carol", Money.ofCents(0));
    }

    @Test
    void repeatingTheSameKeyWithTheSameRequestReturnsTheOriginalOutcomeWithoutReapplying() {
        TransferRequest request = new TransferRequest("alice", "bob", Money.ofCents(100));

        TransferResult first = ledger.transfer(request, "key-1");
        TransferResult second = ledger.transfer(request, "key-1");

        assertThat(second).isEqualTo(first);
        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(900));
        assertThat(ledger.getBalance("bob")).isEqualTo(Money.ofCents(100));
    }

    @Test
    void reusingAKeyWithADifferentRequestBodyIsRejected() {
        ledger.transfer(new TransferRequest("alice", "bob", Money.ofCents(100)), "key-2");

        assertThatThrownBy(() -> ledger.transfer(
                new TransferRequest("alice", "carol", Money.ofCents(100)), "key-2"))
                .isInstanceOf(IdempotencyConflictException.class);

        // The conflicting request must not have been applied.
        assertThat(ledger.getBalance("carol")).isEqualTo(Money.ZERO);
    }

    @Test
    void differentKeysForTheSameRequestBothApply() {
        TransferRequest request = new TransferRequest("alice", "bob", Money.ofCents(100));

        ledger.transfer(request, "key-a");
        ledger.transfer(request, "key-b");

        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(800));
        assertThat(ledger.getBalance("bob")).isEqualTo(Money.ofCents(200));
    }

    @Test
    void noIdempotencyKeyMeansEveryCallExecutes() {
        TransferRequest request = new TransferRequest("alice", "bob", Money.ofCents(100));

        ledger.transfer(request, null);
        ledger.transfer(request, null);

        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(800));
        assertThat(ledger.getBalance("bob")).isEqualTo(Money.ofCents(200));
    }
}

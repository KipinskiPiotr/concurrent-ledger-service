package com.concurrent_ledger_service.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.concurrent_ledger_service.ledger.exceptions.AccountAlreadyExistsException;
import com.concurrent_ledger_service.ledger.exceptions.AccountNotFoundException;
import com.concurrent_ledger_service.ledger.exceptions.InsufficientFundsException;
import com.concurrent_ledger_service.ledger.exceptions.InvalidAmountException;

class LedgerCoreValidationTest {

    private LedgerCore ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerCore(new InMemoryAccountStore(), new InMemoryIdempotencyStore());
        ledger.createAccount("alice", Money.ofCents(1_000));
        ledger.createAccount("bob", Money.ofCents(0));
    }

    @Test
    void rejectsTransferExceedingBalanceAndLeavesBalancesUnchanged() {
        assertThatThrownBy(() -> ledger.transfer(
                new TransferRequest("alice", "bob", Money.ofCents(1_001)), null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(1_000));
        assertThat(ledger.getBalance("bob")).isEqualTo(Money.ZERO);
    }

    @Test
    void rejectsTransferFromUnknownAccount() {
        assertThatThrownBy(() -> ledger.transfer(
                new TransferRequest("ghost", "bob", Money.ofCents(1)), null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void rejectsTransferToUnknownAccountAndLeavesSourceBalanceUnchanged() {
        assertThatThrownBy(() -> ledger.transfer(
                new TransferRequest("alice", "ghost", Money.ofCents(100)), null))
                .isInstanceOf(AccountNotFoundException.class);

        assertThat(ledger.getBalance("alice")).isEqualTo(Money.ofCents(1_000));
    }

    @Test
    void rejectsZeroAmountTransfer() {
        assertThatThrownBy(() -> ledger.transfer(
                new TransferRequest("alice", "bob", Money.ZERO), null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.ofCents(-1))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejectsSelfTransfer() {
        assertThatThrownBy(() -> ledger.transfer(
                new TransferRequest("alice", "alice", Money.ofCents(100)), null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void rejectsDuplicateAccountCreation() {
        assertThatThrownBy(() -> ledger.createAccount("alice", Money.ZERO))
                .isInstanceOf(AccountAlreadyExistsException.class);
    }

    @Test
    void rejectsBalanceLookupForUnknownAccount() {
        assertThatThrownBy(() -> ledger.getBalance("ghost"))
                .isInstanceOf(AccountNotFoundException.class);
    }
}

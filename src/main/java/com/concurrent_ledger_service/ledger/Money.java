package com.concurrent_ledger_service.ledger;

import com.concurrent_ledger_service.ledger.exceptions.InvalidAmountException;

/**
 * Amount in minor units (cents). Wrapping a bare long here gives one place
 * to enforce "no negative money" and to guard against silent overflow.
 */
public record Money(long cents) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (cents < 0) {
            throw new InvalidAmountException("Amount cannot be negative: " + cents);
        }
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return cents >= other.cents;
    }

    public boolean isPositive() {
        return cents > 0;
    }
}

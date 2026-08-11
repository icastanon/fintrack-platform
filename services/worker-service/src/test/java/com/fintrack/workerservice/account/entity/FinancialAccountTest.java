package com.fintrack.workerservice.account.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FinancialAccountTest {

    private FinancialAccount account;

    @BeforeEach
    void setUp() {
        account = new FinancialAccount();
        ReflectionTestUtils.setField(account, "currentBalance", new BigDecimal("100.00"));
    }

    @Test
    void creditAddsAmountToCurrentBalance() {
        account.credit(new BigDecimal("25.50"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("125.50");
    }

    @Test
    void debitSubtractsAmountFromCurrentBalance() {
        account.debit(new BigDecimal("25.50"));

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("74.50");
    }

    @Test
    void creditRejectsNullAmountWithoutChangingBalance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> account.credit(null))
                .withMessage("Amount must be positive");

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void creditRejectsZeroAmountWithoutChangingBalance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> account.credit(BigDecimal.ZERO))
                .withMessage("Amount must be positive");

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitRejectsNegativeAmountWithoutChangingBalance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> account.debit(new BigDecimal("-1.00")))
                .withMessage("Amount must be positive");

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("100.00");
    }
}
package com.fintrack.apiservice.account.entity;

import com.fintrack.apiservice.user.entity.FintrackUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_account")
@Getter
@Setter
@NoArgsConstructor
public class FinancialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private FintrackUser user;

    @Column(name = "account_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void credit(BigDecimal amount) {
        validatePositiveAmount(amount);
        currentBalance = currentBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        validatePositiveAmount(amount);
        currentBalance = currentBalance.subtract(amount);
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
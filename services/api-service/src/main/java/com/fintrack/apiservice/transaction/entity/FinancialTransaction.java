package com.fintrack.apiservice.transaction.entity;

import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.category.entity.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "financial_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant", length = 200)
    private String merchant;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TransactionSource source;

    @Column(name = "manual_category_override", nullable = false)
    private boolean manualCategoryOverride;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FinancialTransaction createManual(
            FinancialAccount account,
            TransactionType transactionType,
            BigDecimal amount,
            String merchant,
            String description,
            LocalDate transactionDate
    ) {
        FinancialTransaction transaction = new FinancialTransaction();

        transaction.account = account;
        transaction.transactionType = transactionType;
        transaction.amount = amount;
        transaction.merchant = merchant;
        transaction.description = description;
        transaction.transactionDate = transactionDate;

        transaction.category = null;
        transaction.processingStatus = ProcessingStatus.PENDING;
        transaction.source = TransactionSource.MANUAL;
        transaction.manualCategoryOverride = false;

        return transaction;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void assignAutomaticCategory(Category category) {
        if (!manualCategoryOverride) {
            this.category = category;
        }
    }

    public void overrideCategory(Category category) {
        this.category = category;
        this.manualCategoryOverride = true;
    }

    public void markProcessed() {
        this.processingStatus =
                ProcessingStatus.PROCESSED;
    }

    public void markFailed() {
        this.processingStatus =
                ProcessingStatus.FAILED;
    }
}
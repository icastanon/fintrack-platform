package com.fintrack.apiservice.budget.entity;

import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.user.entity.FintrackUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "budget")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private FintrackUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "budget_month", nullable = false)
    private LocalDate budgetMonth;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "warning_threshold_percentage", nullable = false)
    private Integer warningThresholdPercentage;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Budget create(FintrackUser user, Category category, LocalDate budgetMonth, BigDecimal amount, Integer warningThresholdPercentage) {
        Budget budget = new Budget();
        budget.user = user;
        budget.category = category;
        budget.budgetMonth = budgetMonth;
        budget.amount = amount;
        budget.warningThresholdPercentage = warningThresholdPercentage;
        return budget;
    }

    public void update(BigDecimal amount, Integer warningThresholdPercentage) {
        this.amount = amount;
        this.warningThresholdPercentage = warningThresholdPercentage;
    }
}
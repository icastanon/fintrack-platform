package com.fintrack.apiservice.transaction.repository;

import com.fintrack.apiservice.transaction.dto.FinancialTransactionFilterRequest;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class FinancialTransactionSpecifications {

    private FinancialTransactionSpecifications() {
    }

    public static Specification<FinancialTransaction> matches(Long userId, FinancialTransactionFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(
                    root.get("account").get("user").get("id"),
                    userId
            ));

            if (filter.getAccountId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("account").get("id"),
                        filter.getAccountId()
                ));
            }

            if (filter.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("category").get("id"),
                        filter.getCategoryId()
                ));
            }

            if (filter.getTransactionType() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("transactionType"),
                        filter.getTransactionType()
                ));
            }

            if (filter.getProcessingStatus() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("processingStatus"),
                        filter.getProcessingStatus()
                ));
            }

            if (filter.getFromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("transactionDate"),
                        filter.getFromDate()
                ));
            }

            if (filter.getToDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("transactionDate"),
                        filter.getToDate()
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
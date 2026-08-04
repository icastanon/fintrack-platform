package com.fintrack.apiservice.transaction.mapper;

import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import org.springframework.stereotype.Component;

@Component
public class FinancialTransactionMapper {

    public FinancialTransactionResponse toResponse(FinancialTransaction transaction) {
        Category category = transaction.getCategory();

        Long categoryId = category == null ? null : category.getId();
        String categoryName = category == null ? null : category.getName();

        return new FinancialTransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                categoryId,
                categoryName,
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getProcessingStatus(),
                transaction.getSource(),
                transaction.isManualCategoryOverride(),
                transaction.getVersion(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
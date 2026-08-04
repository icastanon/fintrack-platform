package com.fintrack.apiservice.transaction.dto;

import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class FinancialTransactionFilterRequest {

    @Positive(message = "Account ID must be positive")
    private Long accountId;

    @Positive(message = "Category ID must be positive")
    private Long categoryId;

    private TransactionType transactionType;

    private ProcessingStatus processingStatus;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    @Min(value = 0, message = "Page cannot be negative")
    private int page = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int size = 20;

    @AssertTrue(message = "From date cannot be after to date")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }
}
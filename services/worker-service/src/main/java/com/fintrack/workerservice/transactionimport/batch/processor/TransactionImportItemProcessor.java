package com.fintrack.workerservice.transactionimport.batch.processor;

import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.service.CategorizationService;
import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

public class TransactionImportItemProcessor implements ItemProcessor<TransactionImportCsvRow, ValidatedTransactionImportRow> {

    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("0.01");
    private static final int MAXIMUM_INTEGER_DIGITS = 17;
    private static final int MAXIMUM_FRACTION_DIGITS = 2;
    private static final int MAXIMUM_MERCHANT_LENGTH = 200;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 500;

    private final CategorizationService categorizationService;
    private final CategorizationRuleCacheSnapshot ruleSnapshot;

    public TransactionImportItemProcessor(CategorizationService categorizationService,
                                          CategorizationRuleCacheSnapshot ruleSnapshot) {
        this.categorizationService = Objects.requireNonNull(categorizationService, "Categorization service is required");
        this.ruleSnapshot = Objects.requireNonNull(ruleSnapshot, "Categorization rule snapshot is required");
    }

    @Override
    public ValidatedTransactionImportRow process(TransactionImportCsvRow row) {
        int rowNumber = row.getRowNumber();

        LocalDate transactionDate = parseTransactionDate(row.getTransactionDate(), rowNumber);
        TransactionType transactionType = parseTransactionType(row.getTransactionType(), rowNumber);
        BigDecimal amount = parseAmount(row.getAmount(), rowNumber);
        String merchant = normalizeOptionalText(
                row.getMerchant(),
                MAXIMUM_MERCHANT_LENGTH,
                "merchant",
                rowNumber);
        String description = normalizeOptionalText(
                row.getDescription(),
                MAXIMUM_DESCRIPTION_LENGTH,
                "description",
                rowNumber);

        Long categoryId = categorizationService.categorizeMerchant(merchant, ruleSnapshot);

        return new ValidatedTransactionImportRow(rowNumber,
                transactionDate,
                transactionType,
                amount,
                merchant,
                description,
                categoryId);
    }

    private LocalDate parseTransactionDate(String value, int rowNumber) {
        String normalizedValue = requireText(value, "transaction date is required", rowNumber);

        try {
            LocalDate transactionDate = LocalDate.parse(normalizedValue);

            if (transactionDate.isAfter(LocalDate.now())) {
                throw validationException(rowNumber, "transaction date cannot be in the future");
            }

            return transactionDate;
        } catch (DateTimeParseException exception) {
            throw validationException(rowNumber, "transaction date must use YYYY-MM-DD");
        }
    }

    private TransactionType parseTransactionType(String value, int rowNumber) {
        String normalizedValue = requireText(value, "transaction type is required", rowNumber).toUpperCase(Locale.ROOT);

        try {
            return TransactionType.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw validationException(rowNumber, "transaction type must be INCOME or EXPENSE");
        }
    }

    private BigDecimal parseAmount(String value, int rowNumber) {
        String normalizedValue = requireText(value, "amount is required", rowNumber);

        try {
            BigDecimal amount = new BigDecimal(normalizedValue);

            if (amount.compareTo(MINIMUM_AMOUNT) < 0) {
                throw validationException(rowNumber, "amount must be at least 0.01");
            }

            int fractionDigits = Math.max(amount.scale(), 0);
            int integerDigits = Math.max(amount.precision() - amount.scale(), 0);

            if (integerDigits > MAXIMUM_INTEGER_DIGITS || fractionDigits > MAXIMUM_FRACTION_DIGITS) {
                throw validationException(rowNumber,
                        "amount must have at most 17 integer digits and 2 decimal places");
            }

            return amount;
        } catch (NumberFormatException exception) {
            throw validationException(rowNumber, "amount must be a valid decimal number");
        }
    }

    private String normalizeOptionalText(String value, int maximumLength,
                                         String fieldName, int rowNumber) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw validationException(rowNumber,
                    fieldName + " must not exceed " + maximumLength + " characters");
        }

        return normalizedValue;
    }

    private String requireText(String value, String message, int rowNumber) {
        if (value == null || value.isBlank()) {
            throw validationException(rowNumber, message);
        }

        return value.trim();
    }

    private TransactionImportRowValidationException validationException(int rowNumber, String reason) {
        return new TransactionImportRowValidationException(rowNumber, reason);
    }
}
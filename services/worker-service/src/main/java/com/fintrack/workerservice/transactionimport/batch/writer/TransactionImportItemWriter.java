package com.fintrack.workerservice.transactionimport.batch.writer;

import com.fintrack.workerservice.account.entity.AccountStatus;
import com.fintrack.workerservice.account.entity.FinancialAccount;
import com.fintrack.workerservice.account.repository.FinancialAccountRepository;
import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.service.BudgetEvaluationService;
import com.fintrack.workerservice.notification.service.NotificationService;
import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.workerservice.transactionimport.batch.model.AffectedBudgetKey;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportAccountUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class TransactionImportItemWriter implements ItemWriter<ValidatedTransactionImportRow> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportItemWriter.class);

    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final BudgetEvaluationService budgetEvaluationService;
    private final NotificationService notificationService;
    private final Long importId;
    private final Long accountId;
    private final Long userId;

    public TransactionImportItemWriter(FinancialAccountRepository financialAccountRepository,
                                       FinancialTransactionRepository financialTransactionRepository,
                                       BudgetEvaluationService budgetEvaluationService,
                                       NotificationService notificationService,
                                       Long importId, Long accountId, Long userId) {
        this.financialAccountRepository = Objects.requireNonNull(
                financialAccountRepository, "Financial account repository is required");
        this.financialTransactionRepository = Objects.requireNonNull(
                financialTransactionRepository, "Financial transaction repository is required");
        this.budgetEvaluationService = Objects.requireNonNull(
                budgetEvaluationService, "Budget evaluation service is required");
        this.notificationService = Objects.requireNonNull(
                notificationService, "Notification service is required");
        this.importId = Objects.requireNonNull(importId, "Import ID is required");
        this.accountId = Objects.requireNonNull(accountId, "Account ID is required");
        this.userId = Objects.requireNonNull(userId, "User ID is required");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Chunk<? extends ValidatedTransactionImportRow> chunk) {
        if (chunk.isEmpty()) {
            return;
        }

        FinancialAccount account = financialAccountRepository
                .findByIdAndUserIdForUpdate(accountId, userId)
                .filter(foundAccount -> foundAccount.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new TransactionImportAccountUnavailableException(accountId, userId));

        List<FinancialTransaction> transactions = new ArrayList<>(chunk.size());

        for (ValidatedTransactionImportRow row : chunk) {
            applyBalanceChange(account, row);
            transactions.add(createTransaction(row));
        }

        financialTransactionRepository.saveAllAndFlush(transactions);
        evaluateAffectedBudgets(transactions);
    }

    private FinancialTransaction createTransaction(ValidatedTransactionImportRow row) {
        return FinancialTransaction.createImported(
                importId, row.getRowNumber(),
                accountId, row.getCategoryId(),
                row.getTransactionType(), row.getAmount(),
                row.getMerchant(), row.getDescription(),
                row.getTransactionDate());
    }

    private void applyBalanceChange(FinancialAccount account, ValidatedTransactionImportRow row) {
        if (row.getTransactionType() == TransactionType.INCOME) {
            account.credit(row.getAmount());
        } else {
            account.debit(row.getAmount());
        }
    }

    private void evaluateAffectedBudgets(List<FinancialTransaction> transactions) {
        Map<AffectedBudgetKey, FinancialTransaction> affectedBudgets = new TreeMap<>(
                Comparator.comparing(AffectedBudgetKey::getCategoryId)
                        .thenComparing(AffectedBudgetKey::getBudgetMonth));

        for (FinancialTransaction transaction : transactions) {
            if (transaction.getTransactionType() == TransactionType.EXPENSE) {
                LocalDate budgetMonth = transaction.getTransactionDate().withDayOfMonth(1);
                AffectedBudgetKey key = new AffectedBudgetKey(transaction.getCategoryId(), budgetMonth);
                affectedBudgets.put(key, transaction);
            }
        }

        for (Map.Entry<AffectedBudgetKey, FinancialTransaction> entry : affectedBudgets.entrySet()) {
            evaluateBudget(entry.getKey(), entry.getValue());
        }
    }

    private void evaluateBudget(AffectedBudgetKey key, FinancialTransaction triggerTransaction) {
        Optional<BudgetEvaluationResult> evaluation = budgetEvaluationService.evaluate(
                userId,
                key.getCategoryId(),
                key.getBudgetMonth());

        evaluation.ifPresent(result ->
                handleBudgetEvaluation(key, triggerTransaction, result));
    }

    private void handleBudgetEvaluation(AffectedBudgetKey key, FinancialTransaction triggerTransaction,
                                        BudgetEvaluationResult result) {
        boolean notificationCreated = notificationService.createIfRequired(
                userId,
                key.getCategoryId(),
                triggerTransaction.getId(),
                key.getBudgetMonth(),
                result);

        if (notificationCreated) {
            LOGGER.info(
                    "Created budget notification during transaction import: importId={}, budgetId={}, categoryId={}, budgetMonth={}, status={}",
                    importId,
                    result.getBudgetId(),
                    key.getCategoryId(),
                    key.getBudgetMonth(),
                    result.getStatus());
        }
    }
}
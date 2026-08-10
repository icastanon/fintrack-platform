package com.fintrack.workerservice.transaction.service;

import com.fintrack.eventcontracts.TransactionProcessingReason;
import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.budget.service.BudgetEvaluationService;
import com.fintrack.workerservice.category.service.CategorizationService;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.notification.service.NotificationService;
import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionProcessingRequestEventProcessorTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private CategorizationService categorizationService;

    @Mock
    private BudgetEvaluationService budgetEvaluationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private FinancialTransaction financialTransaction;

    @InjectMocks
    private TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor;

    @Test
    void processWhenCreatedRequestIsNewCategorizesProcessesEvaluatesBudgetAndCreatesNotification() {
        TransactionProcessingRequestEvent event = createEvent(TransactionProcessingReason.CREATED);
        BudgetEvaluationResult evaluation = createWarningEvaluation();

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-processing-request-processor",
                "TRANSACTION_PROCESSING_REQUESTED",
                event.getEventVersion()
        )).thenReturn(true);

        when(financialTransactionRepository.findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        )).thenReturn(Optional.of(financialTransaction));

        when(financialTransaction.isManualCategoryOverride()).thenReturn(false);
        when(financialTransaction.getMerchant()).thenReturn("STARBUCKS");
        when(categorizationService.categorizeMerchant("STARBUCKS")).thenReturn(4L);
        when(financialTransaction.getCategoryId()).thenReturn(4L);
        when(financialTransaction.getTransactionType()).thenReturn(TransactionType.EXPENSE);
        when(financialTransaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 8, 8));
        when(budgetEvaluationService.evaluate(25L, 4L, LocalDate.of(2026, 8, 8)))
                .thenReturn(Optional.of(evaluation));
        when(notificationService.createIfRequired(
                25L,
                4L,
                100L,
                LocalDate.of(2026, 8, 8),
                evaluation
        )).thenReturn(true);

        boolean firstProcessing = transactionProcessingRequestEventProcessor.process(event);

        assertThat(firstProcessing).isTrue();

        verify(categorizationService).categorizeMerchant("STARBUCKS");
        verify(financialTransaction).assignAutomaticCategory(4L);
        verify(financialTransaction).markProcessed();
        verify(budgetEvaluationService).evaluate(25L, 4L, LocalDate.of(2026, 8, 8));
        verify(notificationService).createIfRequired(
                25L,
                4L,
                100L,
                LocalDate.of(2026, 8, 8),
                evaluation
        );
    }

    @Test
    void processWhenCategoryWasOverriddenPreservesCategoryAndSkipsNotificationWithoutBudget() {
        TransactionProcessingRequestEvent event = createEvent(TransactionProcessingReason.CATEGORY_OVERRIDDEN);

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-processing-request-processor",
                "TRANSACTION_PROCESSING_REQUESTED",
                event.getEventVersion()
        )).thenReturn(true);

        when(financialTransactionRepository.findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        )).thenReturn(Optional.of(financialTransaction));

        when(financialTransaction.isManualCategoryOverride()).thenReturn(true);
        when(financialTransaction.getCategoryId()).thenReturn(7L);
        when(financialTransaction.getTransactionType()).thenReturn(TransactionType.EXPENSE);
        when(financialTransaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 8, 8));
        when(budgetEvaluationService.evaluate(25L, 7L, LocalDate.of(2026, 8, 8)))
                .thenReturn(Optional.empty());

        boolean firstProcessing = transactionProcessingRequestEventProcessor.process(event);

        assertThat(firstProcessing).isTrue();

        verifyNoInteractions(categorizationService);
        verify(financialTransaction, never()).assignAutomaticCategory(anyLong());
        verify(financialTransaction).markProcessed();
        verify(budgetEvaluationService).evaluate(25L, 7L, LocalDate.of(2026, 8, 8));
        verifyNoInteractions(notificationService);
    }

    @Test
    void processWhenTransactionIsIncomeSkipsBudgetEvaluationAndNotification() {
        TransactionProcessingRequestEvent event = createEvent(TransactionProcessingReason.CREATED);

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-processing-request-processor",
                "TRANSACTION_PROCESSING_REQUESTED",
                event.getEventVersion()
        )).thenReturn(true);

        when(financialTransactionRepository.findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        )).thenReturn(Optional.of(financialTransaction));

        when(financialTransaction.isManualCategoryOverride()).thenReturn(false);
        when(financialTransaction.getMerchant()).thenReturn("PAYROLL");
        when(categorizationService.categorizeMerchant("PAYROLL")).thenReturn(8L);
        when(financialTransaction.getTransactionType()).thenReturn(TransactionType.INCOME);

        boolean firstProcessing = transactionProcessingRequestEventProcessor.process(event);

        assertThat(firstProcessing).isTrue();

        verify(financialTransaction).assignAutomaticCategory(8L);
        verify(financialTransaction).markProcessed();
        verifyNoInteractions(budgetEvaluationService, notificationService);
    }

    @Test
    void processWhenRequestIsDuplicateDoesNotLoadOrProcessTransaction() {
        TransactionProcessingRequestEvent event = createEvent(TransactionProcessingReason.CREATED);

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-processing-request-processor",
                "TRANSACTION_PROCESSING_REQUESTED",
                event.getEventVersion()
        )).thenReturn(false);

        boolean firstProcessing = transactionProcessingRequestEventProcessor.process(event);

        assertThat(firstProcessing).isFalse();

        verifyNoInteractions(
                financialTransactionRepository,
                categorizationService,
                budgetEvaluationService,
                notificationService,
                financialTransaction
        );
    }

    @Test
    void processWhenTransactionDoesNotBelongToUserThrows() {
        TransactionProcessingRequestEvent event = createEvent(TransactionProcessingReason.CREATED);

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-processing-request-processor",
                "TRANSACTION_PROCESSING_REQUESTED",
                event.getEventVersion()
        )).thenReturn(true);

        when(financialTransactionRepository.findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionProcessingRequestEventProcessor.process(event))
                .isInstanceOf(FinancialTransactionNotFoundException.class)
                .hasMessage("Financial transaction 100 was not found for user 25");

        verifyNoInteractions(
                categorizationService,
                budgetEvaluationService,
                notificationService,
                financialTransaction
        );
    }

    private BudgetEvaluationResult createWarningEvaluation() {
        return new BudgetEvaluationResult(
                10L,
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                new BigDecimal("80.00"),
                BudgetStatus.WARNING
        );
    }

    private TransactionProcessingRequestEvent createEvent(TransactionProcessingReason reason) {
        return TransactionProcessingRequestEvent.create(
                UUID.randomUUID(),
                100L,
                25L,
                reason,
                "processor-test-correlation-id",
                Instant.parse("2026-08-06T12:00:00Z")
        );
    }
}

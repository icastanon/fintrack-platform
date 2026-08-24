package com.fintrack.apiservice.transaction.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.AccountType;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.category.exception.CategoryNotFoundException;
import com.fintrack.apiservice.category.repository.CategoryRepository;
import com.fintrack.apiservice.outbox.service.OutboxEventWriter;
import com.fintrack.apiservice.transaction.dto.*;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionSource;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionVersionConflictException;
import com.fintrack.apiservice.transaction.mapper.FinancialTransactionMapper;
import com.fintrack.apiservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.eventcontracts.TransactionProcessingReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private FinancialTransactionMapper transactionMapper;

    @Captor
    private ArgumentCaptor<FinancialTransaction> transactionCaptor;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private FinancialTransactionService transactionService;

    @Test
    void createExpenseCreatesPendingManualTransactionAndDebitsAccount() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));
        FinancialTransactionCreateRequest request = createRequest(TransactionType.EXPENSE, new BigDecimal("83.42"));

        request.setMerchant("  Publix #1472  ");
        request.setDescription("  Weekly groceries  ");

        FinancialTransactionResponse expectedResponse = org.mockito.Mockito.mock(FinancialTransactionResponse.class);

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(account));
        mockTransactionSaveWithId(41L);
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(expectedResponse);

        FinancialTransactionResponse result = transactionService.createTransaction(7L, request);

        verify(transactionRepository).save(transactionCaptor.capture());

        FinancialTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(result).isSameAs(expectedResponse);
        assertThat(savedTransaction.getId()).isEqualTo(41L);
        assertThat(savedTransaction.getAccount()).isSameAs(account);
        assertThat(savedTransaction.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo("83.42");
        assertThat(savedTransaction.getMerchant()).isEqualTo("Publix #1472");
        assertThat(savedTransaction.getDescription()).isEqualTo("Weekly groceries");
        assertThat(savedTransaction.getTransactionDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(savedTransaction.getCategory()).isNull();
        assertThat(savedTransaction.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(savedTransaction.getSource()).isEqualTo(TransactionSource.MANUAL);
        assertThat(savedTransaction.isManualCategoryOverride()).isFalse();
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("916.58");

        verify(outboxEventWriter).writeTransactionProcessingRequested(
                41L,
                7L,
                TransactionProcessingReason.CREATED
        );

        verify(transactionMapper).toResponse(savedTransaction);
    }

    private void mockTransactionSaveWithId(Long transactionId) {
        when(transactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> {
            FinancialTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", transactionId);
            return transaction;
        });
    }

    @Test
    void createIncomeCreatesPendingManualTransactionAndCreditsAccount() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));
        FinancialTransactionCreateRequest request = createRequest(TransactionType.INCOME, new BigDecimal("250.00"));

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(account));
        mockTransactionSaveWithId(41L);
        when(transactionMapper.toResponse(any(FinancialTransaction.class)))
                .thenReturn(org.mockito.Mockito.mock(FinancialTransactionResponse.class));

        transactionService.createTransaction(7L, request);

        verify(transactionRepository).save(transactionCaptor.capture());

        FinancialTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.getId()).isEqualTo(41L);
        assertThat(savedTransaction.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1250.00");

        verify(outboxEventWriter).writeTransactionProcessingRequested(
                41L,
                7L,
                TransactionProcessingReason.CREATED
        );
    }

    @Test
    void createTransactionPreservesNullOptionalFields() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));
        FinancialTransactionCreateRequest request = createRequest(TransactionType.EXPENSE, new BigDecimal("20.00"));

        request.setMerchant(null);
        request.setDescription(null);

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(account));
        mockTransactionSaveWithId(41L);
        when(transactionMapper.toResponse(any(FinancialTransaction.class)))
                .thenReturn(org.mockito.Mockito.mock(FinancialTransactionResponse.class));

        transactionService.createTransaction(7L, request);

        verify(transactionRepository).save(transactionCaptor.capture());

        FinancialTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.getId()).isEqualTo(41L);
        assertThat(savedTransaction.getMerchant()).isNull();
        assertThat(savedTransaction.getDescription()).isNull();

        verify(outboxEventWriter).writeTransactionProcessingRequested(
                41L,
                7L,
                TransactionProcessingReason.CREATED
        );
    }

    @Test
    void createTransactionRejectsMissingOrUnownedAccount() {
        FinancialTransactionCreateRequest request = createRequest(TransactionType.EXPENSE, new BigDecimal("20.00"));

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(7L, request))
                .isInstanceOf(FinancialAccountNotFoundException.class);

        verify(transactionRepository, never()).save(any(FinancialTransaction.class));
        verifyNoInteractions(transactionMapper);
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    void createTransactionRejectsClosedAccountWithoutChangingBalance() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));
        account.setStatus(AccountStatus.CLOSED);

        FinancialTransactionCreateRequest request = createRequest(TransactionType.EXPENSE, new BigDecimal("83.42"));

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> transactionService.createTransaction(7L, request))
                .isInstanceOf(FinancialAccountClosedException.class);

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1000.00");

        verify(transactionRepository, never()).save(any(FinancialTransaction.class));
        verifyNoInteractions(transactionMapper);
        verifyNoInteractions(outboxEventWriter);
    }

    private FinancialTransactionCreateRequest createRequest(TransactionType transactionType, BigDecimal amount) {
        FinancialTransactionCreateRequest request = new FinancialTransactionCreateRequest();
        request.setAccountId(15L);
        request.setTransactionType(transactionType);
        request.setAmount(amount);
        request.setMerchant("Test Merchant");
        request.setDescription("Test transaction");
        request.setTransactionDate(LocalDate.of(2026, 8, 3));
        return request;
    }

    private FinancialAccount createActiveAccount(BigDecimal currentBalance) {
        FinancialAccount account = new FinancialAccount();
        account.setId(15L);
        account.setName("Primary Checking");
        account.setAccountType(AccountType.CHECKING);
        account.setOpeningBalance(currentBalance);
        account.setCurrentBalance(currentBalance);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    @Test
    void getTransactionReturnsOwnedTransaction() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));

        FinancialTransaction transaction = FinancialTransaction.createManual(
                account,
                TransactionType.EXPENSE,
                new BigDecimal("83.42"),
                "Publix #1472",
                "Weekly groceries",
                LocalDate.of(2026, 8, 3)
        );

        FinancialTransactionResponse expectedResponse = org.mockito.Mockito.mock(FinancialTransactionResponse.class);

        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.of(transaction));
        when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

        FinancialTransactionResponse result = transactionService.getTransaction(7L, 41L);

        assertThat(result).isSameAs(expectedResponse);

        verify(transactionRepository).findByIdAndAccountUserId(41L, 7L);
        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void getTransactionRejectsMissingOrUnownedTransaction() {
        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(7L, 41L))
                .isInstanceOf(FinancialTransactionNotFoundException.class)
                .hasMessage("Financial transaction was not found");

        verify(transactionRepository).findByIdAndAccountUserId(41L, 7L);
        verifyNoInteractions(transactionMapper);
    }

    @Test
    void getTransactionsReturnsFilteredOwnedTransactionsUsingFixedNewestFirstSorting() {
        FinancialTransaction firstTransaction = org.mockito.Mockito.mock(FinancialTransaction.class);
        FinancialTransaction secondTransaction = org.mockito.Mockito.mock(FinancialTransaction.class);

        FinancialTransactionResponse firstResponse =
                org.mockito.Mockito.mock(FinancialTransactionResponse.class);
        FinancialTransactionResponse secondResponse =
                org.mockito.Mockito.mock(FinancialTransactionResponse.class);

        FinancialTransactionFilterRequest filter = new FinancialTransactionFilterRequest();
        filter.setAccountId(15L);
        filter.setCategoryId(2L);
        filter.setTransactionType(TransactionType.EXPENSE);
        filter.setProcessingStatus(ProcessingStatus.PROCESSED);
        filter.setFromDate(LocalDate.of(2026, 8, 1));
        filter.setToDate(LocalDate.of(2026, 8, 31));
        filter.setPage(1);
        filter.setSize(2);

        Page<FinancialTransaction> repositoryPage = new PageImpl<>(
                List.of(firstTransaction, secondTransaction),
                PageRequest.of(1, 2),
                5
        );

        when(transactionRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(repositoryPage);

        when(transactionMapper.toResponse(firstTransaction)).thenReturn(firstResponse);
        when(transactionMapper.toResponse(secondTransaction)).thenReturn(secondResponse);

        FinancialTransactionPageResponse result =
                transactionService.getTransactions(7L, filter);

        assertThat(result.getContent()).containsExactly(firstResponse, secondResponse);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isFalse();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );

        verify(transactionMapper).toResponse(firstTransaction);
        verify(transactionMapper).toResponse(secondTransaction);

        Pageable capturedPageable = pageableCaptor.getValue();

        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(2);
        assertThat(capturedPageable.getSort().getOrderFor("transactionDate"))
                .isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("transactionDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(capturedPageable.getSort().getOrderFor("id"))
                .isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void overrideCategoryWhenPendingAssignsManualCategoryWithoutWritingProcessingRequest() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));

        FinancialTransaction transaction = FinancialTransaction.createManual(
                account,
                TransactionType.EXPENSE,
                new BigDecimal("83.42"),
                "Publix",
                "Weekly groceries",
                LocalDate.of(2026, 8, 3)
        );

        ReflectionTestUtils.setField(transaction, "version", 0L);

        Category category = org.mockito.Mockito.mock(Category.class);

        FinancialTransactionCategoryOverrideRequest request = new FinancialTransactionCategoryOverrideRequest();
        request.setCategoryId(2L);
        request.setVersion(0L);

        FinancialTransactionResponse expectedResponse = org.mockito.Mockito.mock(FinancialTransactionResponse.class);

        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.of(transaction));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

        FinancialTransactionResponse result = transactionService.overrideCategory(7L, 41L, request);

        assertThat(result).isSameAs(expectedResponse);
        assertThat(transaction.getCategory()).isSameAs(category);
        assertThat(transaction.isManualCategoryOverride()).isTrue();

        verify(transactionRepository).findByIdAndAccountUserId(41L, 7L);
        verify(categoryRepository).findById(2L);
        verify(transactionRepository).flush();
        verifyNoInteractions(outboxEventWriter);
        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void overrideCategoryWhenProcessedWritesCategoryOverriddenProcessingRequest() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));

        FinancialTransaction transaction = FinancialTransaction.createManual(
                account,
                TransactionType.EXPENSE,
                new BigDecimal("83.42"),
                "Publix",
                "Weekly groceries",
                LocalDate.of(2026, 8, 3)
        );

        transaction.markProcessed();
        ReflectionTestUtils.setField(transaction, "version", 0L);

        Category category = org.mockito.Mockito.mock(Category.class);

        FinancialTransactionCategoryOverrideRequest request = new FinancialTransactionCategoryOverrideRequest();
        request.setCategoryId(2L);
        request.setVersion(0L);

        FinancialTransactionResponse expectedResponse = org.mockito.Mockito.mock(FinancialTransactionResponse.class);

        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.of(transaction));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

        FinancialTransactionResponse result = transactionService.overrideCategory(7L, 41L, request);

        assertThat(result).isSameAs(expectedResponse);
        assertThat(transaction.getCategory()).isSameAs(category);
        assertThat(transaction.isManualCategoryOverride()).isTrue();

        verify(transactionRepository).flush();
        verify(outboxEventWriter).writeTransactionProcessingRequested(
                41L,
                7L,
                TransactionProcessingReason.CATEGORY_OVERRIDDEN
        );
        verify(transactionMapper).toResponse(transaction);
    }

    @Test
    void overrideCategoryRejectsMissingOrUnownedTransaction() {
        FinancialTransactionCategoryOverrideRequest request = new FinancialTransactionCategoryOverrideRequest();
        request.setCategoryId(2L);
        request.setVersion(0L);

        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.overrideCategory(7L, 41L, request))
                .isInstanceOf(FinancialTransactionNotFoundException.class);

        verify(transactionRepository).findByIdAndAccountUserId(41L, 7L);
        verifyNoInteractions(categoryRepository);
        verify(transactionRepository, never()).flush();
        verifyNoInteractions(outboxEventWriter);
        verifyNoInteractions(transactionMapper);
    }

    @Test
    void overrideCategoryRejectsStaleVersion() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));

        FinancialTransaction transaction = FinancialTransaction.createManual(
                account,
                TransactionType.EXPENSE,
                new BigDecimal("83.42"),
                "Publix",
                "Weekly groceries",
                LocalDate.of(2026, 8, 3)
        );

        ReflectionTestUtils.setField(transaction, "version", 1L);

        FinancialTransactionCategoryOverrideRequest request = new FinancialTransactionCategoryOverrideRequest();
        request.setCategoryId(2L);
        request.setVersion(0L);

        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> transactionService.overrideCategory(7L, 41L, request))
                .isInstanceOf(FinancialTransactionVersionConflictException.class)
                .hasMessage("The financial transaction was modified. Reload it and try again.");

        assertThat(transaction.getCategory()).isNull();
        assertThat(transaction.isManualCategoryOverride()).isFalse();

        verify(transactionRepository).findByIdAndAccountUserId(41L, 7L);
        verifyNoInteractions(categoryRepository);
        verify(transactionRepository, never()).flush();
        verifyNoInteractions(outboxEventWriter);
        verifyNoInteractions(transactionMapper);
    }

    @Test
    void overrideCategoryRejectsMissingCategory() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));

        FinancialTransaction transaction = FinancialTransaction.createManual(
                account,
                TransactionType.EXPENSE,
                new BigDecimal("83.42"),
                "Publix",
                "Weekly groceries",
                LocalDate.of(2026, 8, 3)
        );

        ReflectionTestUtils.setField(transaction, "version", 0L);

        FinancialTransactionCategoryOverrideRequest request = new FinancialTransactionCategoryOverrideRequest();
        request.setCategoryId(999L);
        request.setVersion(0L);

        when(transactionRepository.findByIdAndAccountUserId(41L, 7L)).thenReturn(Optional.of(transaction));
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.overrideCategory(7L, 41L, request))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessage("Category was not found");

        assertThat(transaction.getCategory()).isNull();
        assertThat(transaction.isManualCategoryOverride()).isFalse();

        verify(transactionRepository).findByIdAndAccountUserId(41L, 7L);
        verify(categoryRepository).findById(999L);
        verify(transactionRepository, never()).flush();
        verifyNoInteractions(outboxEventWriter);
        verifyNoInteractions(transactionMapper);
    }
}
package com.fintrack.apiservice.transaction.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.AccountType;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionCreateRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionFilterRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionPageResponse;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionSource;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.apiservice.transaction.mapper.FinancialTransactionMapper;
import com.fintrack.apiservice.transaction.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

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
        when(transactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(expectedResponse);

        FinancialTransactionResponse result = transactionService.createTransaction(7L, request);

        verify(transactionRepository).save(transactionCaptor.capture());

        FinancialTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(result).isSameAs(expectedResponse);
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

        verify(transactionMapper).toResponse(savedTransaction);
    }

    @Test
    void createIncomeCreatesPendingManualTransactionAndCreditsAccount() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));
        FinancialTransactionCreateRequest request = createRequest(TransactionType.INCOME, new BigDecimal("250.00"));

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(org.mockito.Mockito.mock(FinancialTransactionResponse.class));

        transactionService.createTransaction(7L, request);

        verify(transactionRepository).save(transactionCaptor.capture());

        FinancialTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1250.00");
    }

    @Test
    void createTransactionPreservesNullOptionalFields() {
        FinancialAccount account = createActiveAccount(new BigDecimal("1000.00"));
        FinancialTransactionCreateRequest request = createRequest(TransactionType.EXPENSE, new BigDecimal("20.00"));

        request.setMerchant(null);
        request.setDescription(null);

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(org.mockito.Mockito.mock(FinancialTransactionResponse.class));

        transactionService.createTransaction(7L, request);

        verify(transactionRepository).save(transactionCaptor.capture());

        FinancialTransaction savedTransaction = transactionCaptor.getValue();

        assertThat(savedTransaction.getMerchant()).isNull();
        assertThat(savedTransaction.getDescription()).isNull();
    }

    @Test
    void createTransactionRejectsMissingOrUnownedAccount() {
        FinancialTransactionCreateRequest request = createRequest(TransactionType.EXPENSE, new BigDecimal("20.00"));

        when(accountRepository.findByIdAndUserId(15L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(7L, request))
                .isInstanceOf(FinancialAccountNotFoundException.class);

        verify(transactionRepository, never()).save(any(FinancialTransaction.class));
        verifyNoInteractions(transactionMapper);
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
        account.setCurrency("USD");
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

        FinancialTransactionResponse firstResponse = org.mockito.Mockito.mock(FinancialTransactionResponse.class);
        FinancialTransactionResponse secondResponse = org.mockito.Mockito.mock(FinancialTransactionResponse.class);

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

        when(transactionRepository.findAllByFilters(
                eq(7L),
                eq(15L),
                eq(2L),
                eq(TransactionType.EXPENSE),
                eq(ProcessingStatus.PROCESSED),
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(2026, 8, 31)),
                any(Pageable.class)
        )).thenReturn(repositoryPage);

        when(transactionMapper.toResponse(firstTransaction)).thenReturn(firstResponse);
        when(transactionMapper.toResponse(secondTransaction)).thenReturn(secondResponse);

        FinancialTransactionPageResponse result = transactionService.getTransactions(7L, filter);

        assertThat(result.getContent()).containsExactly(firstResponse, secondResponse);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isFalse();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository).findAllByFilters(
                eq(7L),
                eq(15L),
                eq(2L),
                eq(TransactionType.EXPENSE),
                eq(ProcessingStatus.PROCESSED),
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(2026, 8, 31)),
                pageableCaptor.capture()
        );

        verify(transactionMapper).toResponse(firstTransaction);
        verify(transactionMapper).toResponse(secondTransaction);

        Pageable capturedPageable = pageableCaptor.getValue();

        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(2);
        assertThat(capturedPageable.getSort().getOrderFor("transactionDate")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("transactionDate").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(capturedPageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
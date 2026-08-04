package com.fintrack.apiservice.transaction.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.AccountType;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionCreateRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionSource;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.mapper.FinancialTransactionMapper;
import com.fintrack.apiservice.transaction.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(expectedResponse);

        FinancialTransactionResponse result = transactionService.createTransaction(7L, request);

        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());

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
        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(org.mockito.Mockito.mock(FinancialTransactionResponse.class));

        transactionService.createTransaction(7L, request);

        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());

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
        when(transactionRepository.saveAndFlush(any(FinancialTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toResponse(any(FinancialTransaction.class))).thenReturn(org.mockito.Mockito.mock(FinancialTransactionResponse.class));

        transactionService.createTransaction(7L, request);

        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());

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

        verify(transactionRepository, never()).saveAndFlush(any(FinancialTransaction.class));
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

        verify(transactionRepository, never()).saveAndFlush(any(FinancialTransaction.class));
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
}
package com.fintrack.apiservice.account.service;

import com.fintrack.apiservice.account.dto.FinancialAccountCreateRequest;
import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.dto.FinancialAccountUpdateRequest;
import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.AccountType;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.AccountNameAlreadyExistsException;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.exception.FinancialAccountVersionConflictException;
import com.fintrack.apiservice.account.mapper.FinancialAccountMapper;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private FintrackUserRepository userRepository;

    @Spy
    private FinancialAccountMapper mapper = new FinancialAccountMapper();

    @InjectMocks
    private FinancialAccountService accountService;

    @Test
    void createAccountCreatesOwnedActiveAccount() {
        Long userId = 7L;

        FintrackUser user = new FintrackUser();
        user.setId(userId);
        user.setCurrency(SupportedCurrency.EUR);

        FinancialAccountCreateRequest request = new FinancialAccountCreateRequest();
        request.setName("  Main Checking  ");
        request.setAccountType(AccountType.CHECKING);
        request.setOpeningBalance(new BigDecimal("2500.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.existsByUserIdAndNameIgnoreCase(userId, "Main Checking")).thenReturn(false);

        when(accountRepository.saveAndFlush(any(FinancialAccount.class))).thenAnswer(invocation -> {
            FinancialAccount account = invocation.getArgument(0);
            account.setId(100L);
            account.setVersion(0L);

            return account;
        });

        FinancialAccountResponse response = accountService.createAccount(userId, request);

        ArgumentCaptor<FinancialAccount> accountCaptor = ArgumentCaptor.forClass(FinancialAccount.class);

        verify(accountRepository).saveAndFlush(accountCaptor.capture());

        FinancialAccount savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getUser()).isSameAs(user);
        assertThat(savedAccount.getName()).isEqualTo("Main Checking");
        assertThat(savedAccount.getOpeningBalance()).isEqualByComparingTo("2500.00");
        assertThat(savedAccount.getCurrentBalance()).isEqualByComparingTo("2500.00");
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getName()).isEqualTo("Main Checking");
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createAccountRejectsDuplicateNameForSameUser() {
        Long userId = 7L;

        FintrackUser user = new FintrackUser();
        user.setId(userId);
        user.setCurrency(SupportedCurrency.USD);

        FinancialAccountCreateRequest request = new FinancialAccountCreateRequest();
        request.setName("Checking");
        request.setAccountType(AccountType.CHECKING);
        request.setOpeningBalance(new BigDecimal("500.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.existsByUserIdAndNameIgnoreCase(userId, "Checking")).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(userId, request))
                .isInstanceOf(AccountNameAlreadyExistsException.class)
                .hasMessageContaining("Checking");

        verify(accountRepository, never()).saveAndFlush(any(FinancialAccount.class));
    }

    @Test
    void updateAccountUpdatesEditableFields() {
        Long userId = 7L;
        Long accountId = 100L;

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        attachOwner(account, userId, SupportedCurrency.USD);
        account.setOpeningBalance(new BigDecimal("2500.00"));
        account.setCurrentBalance(new BigDecimal("2500.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(3L);

        FinancialAccountUpdateRequest request = new FinancialAccountUpdateRequest();
        request.setName("  Primary Checking  ");
        request.setAccountType(AccountType.SAVINGS);
        request.setVersion(3L);

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));
        when(accountRepository.existsByUserIdAndNameIgnoreCase(userId, "Primary Checking")).thenReturn(false);

        FinancialAccountResponse response = accountService.updateAccount(userId, accountId, request);

        assertThat(account.getName()).isEqualTo("Primary Checking");
        assertThat(account.getAccountType()).isEqualTo(AccountType.SAVINGS);

        assertThat(response.getName()).isEqualTo("Primary Checking");
        assertThat(response.getAccountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(response.getCurrency()).isEqualTo("USD");

        verify(accountRepository).flush();
    }

    @Test
    void updateAccountRejectsStaleVersion() {
        Long userId = 7L;
        Long accountId = 100L;

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(4L);

        FinancialAccountUpdateRequest request = new FinancialAccountUpdateRequest();
        request.setName("Primary Checking");
        request.setAccountType(AccountType.CHECKING);
        request.setVersion(3L);

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.updateAccount(userId, accountId, request))
                .isInstanceOf(FinancialAccountVersionConflictException.class);

        verify(accountRepository, never()).flush();
    }

    @Test
    void updateAccountRejectsClosedAccount() {
        Long userId = 7L;
        Long accountId = 100L;

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        account.setStatus(AccountStatus.CLOSED);
        account.setVersion(2L);

        FinancialAccountUpdateRequest request = new FinancialAccountUpdateRequest();
        request.setName("Primary Checking");
        request.setAccountType(AccountType.SAVINGS);
        request.setVersion(2L);

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.updateAccount(userId, accountId, request))
                .isInstanceOf(FinancialAccountClosedException.class);

        verify(accountRepository, never()).flush();
    }

    @Test
    void getAccountsReturnsOnlyRequestedPageForUser() {
        Long userId = 7L;

        FinancialAccount account = new FinancialAccount();
        account.setId(100L);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        attachOwner(account, userId, SupportedCurrency.USD);
        account.setOpeningBalance(new BigDecimal("2500.00"));
        account.setCurrentBalance(new BigDecimal("2500.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(0L);

        when(accountRepository.findAllByUserId(eq(userId), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);

                    return new PageImpl<>(List.of(account), pageable, 1);
                });

        PageResponse<FinancialAccountResponse> response = accountService.getAccounts(userId, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getId()).isEqualTo(100L);
        assertThat(response.getContent().getFirst().getCurrency()).isEqualTo("USD");
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(accountRepository).findAllByUserId(eq(userId), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);

        Sort.Order createdAtOrder = pageable.getSort().getOrderFor("createdAt");

        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getAccountReturnsAccountOwnedByUser() {
        Long userId = 7L;
        Long accountId = 100L;

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        attachOwner(account, userId, SupportedCurrency.USD);
        account.setOpeningBalance(new BigDecimal("2500.00"));
        account.setCurrentBalance(new BigDecimal("2500.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(0L);

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        FinancialAccountResponse response = accountService.getAccount(userId, accountId);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getName()).isEqualTo("Main Checking");
        assertThat(response.getCurrency()).isEqualTo("USD");

        verify(accountRepository).findByIdAndUserId(accountId, userId);
    }

    @Test
    void getAccountRejectsAccountNotOwnedByUser() {
        Long requestingUserId = 8L;
        Long accountId = 100L;

        when(accountRepository.findByIdAndUserId(accountId, requestingUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(requestingUserId, accountId))
                .isInstanceOf(FinancialAccountNotFoundException.class);

        verify(accountRepository).findByIdAndUserId(accountId, requestingUserId);
    }

    @Test
    void closeAccountChangesActiveAccountToClosed() {
        Long userId = 7L;
        Long accountId = 100L;

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        attachOwner(account, userId, SupportedCurrency.USD);
        account.setOpeningBalance(new BigDecimal("2500.00"));
        account.setCurrentBalance(new BigDecimal("2500.00"));
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(2L);

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        FinancialAccountResponse response = accountService.closeAccount(userId, accountId);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(response.getCurrency()).isEqualTo("USD");

        verify(accountRepository).flush();
    }

    @Test
    void closeAccountDoesNothingWhenAlreadyClosed() {
        Long userId = 7L;
        Long accountId = 100L;

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Main Checking");
        account.setAccountType(AccountType.CHECKING);
        attachOwner(account, userId, SupportedCurrency.USD);
        account.setOpeningBalance(new BigDecimal("2500.00"));
        account.setCurrentBalance(new BigDecimal("2500.00"));
        account.setStatus(AccountStatus.CLOSED);
        account.setVersion(3L);

        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.of(account));

        FinancialAccountResponse response = accountService.closeAccount(userId, accountId);

        assertThat(response.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getVersion()).isEqualTo(3L);

        verify(accountRepository, never()).flush();
    }

    private void attachOwner(FinancialAccount account, Long userId, SupportedCurrency currency) {
        FintrackUser user = new FintrackUser();
        user.setId(userId);
        user.setCurrency(currency);

        account.setUser(user);
    }
}
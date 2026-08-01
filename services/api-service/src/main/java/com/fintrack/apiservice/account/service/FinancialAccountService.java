package com.fintrack.apiservice.account.service;

import com.fintrack.apiservice.account.dto.FinancialAccountCreateRequest;
import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.dto.FinancialAccountUpdateRequest;
import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.AccountNameAlreadyExistsException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.exception.FinancialAccountVersionConflictException;
import com.fintrack.apiservice.account.exception.InvalidCurrencyException;
import com.fintrack.apiservice.account.mapper.FinancialAccountMapper;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class FinancialAccountService {

    private final FinancialAccountRepository accountRepository;
    private final FintrackUserRepository userRepository;
    private final FinancialAccountMapper mapper;

    public FinancialAccountService(
            FinancialAccountRepository accountRepository,
            FintrackUserRepository userRepository,
            FinancialAccountMapper mapper
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional
    public FinancialAccountResponse createAccount(Long userId, FinancialAccountCreateRequest request) {
        FintrackUser user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new FintrackUserNotFoundException(userId)
                );

        String normalizedName = request.getName().trim();
        String normalizedCurrency = request.getCurrency().trim().toUpperCase(Locale.ROOT);

        validateCurrency(normalizedCurrency);

        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, normalizedName)) {
            throw new AccountNameAlreadyExistsException(normalizedName);
        }

        FinancialAccount account = new FinancialAccount();
        account.setUser(user);
        account.setName(normalizedName);
        account.setAccountType(request.getAccountType());
        account.setCurrency(normalizedCurrency);
        account.setOpeningBalance(request.getOpeningBalance());
        account.setCurrentBalance(request.getOpeningBalance());
        account.setStatus(AccountStatus.ACTIVE);

        FinancialAccount savedAccount = accountRepository.save(account);

        return mapper.toResponse(savedAccount);
    }

    private void validateCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCurrencyException(currencyCode);
        }
    }

    public PageResponse<FinancialAccountResponse> getAccounts(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<FinancialAccountResponse> accountPage = accountRepository
                        .findAllByUserId(userId, pageable)
                        .map(mapper::toResponse);

        return new PageResponse<>(accountPage);
    }

    public FinancialAccountResponse getAccount(Long userId, Long accountId) {
        FinancialAccount account =
                accountRepository
                        .findByIdAndUserId(
                                accountId,
                                userId
                        )
                        .orElseThrow(
                                FinancialAccountNotFoundException::new
                        );

        return mapper.toResponse(account);
    }

    @Transactional
    public FinancialAccountResponse updateAccount(Long userId, Long accountId, FinancialAccountUpdateRequest request) {
        FinancialAccount account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        if (!Objects.equals(request.getVersion(), account.getVersion())) {
            throw new FinancialAccountVersionConflictException();
        }

        String normalizedName = request.getName().trim();

        boolean nameChanged = !account.getName().equalsIgnoreCase(normalizedName);

        if (nameChanged && accountRepository.existsByUserIdAndNameIgnoreCase(userId, normalizedName)) {
            throw new AccountNameAlreadyExistsException(normalizedName);
        }

        account.setName(normalizedName);
        account.setAccountType(request.getAccountType());

        accountRepository.flush();

        return mapper.toResponse(account);
    }
}
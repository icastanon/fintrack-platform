package com.fintrack.apiservice.account.service;

import com.fintrack.apiservice.account.dto.FinancialAccountCreateRequest;
import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.dto.FinancialAccountUpdateRequest;
import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.*;
import com.fintrack.apiservice.account.mapper.FinancialAccountMapper;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new FintrackUserNotFoundException(userId));

        String normalizedName = request.getName().trim();

        if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, normalizedName)) {
            throw new AccountNameAlreadyExistsException(normalizedName);
        }

        FinancialAccount account = new FinancialAccount();
        account.setUser(user);
        account.setName(normalizedName);
        account.setAccountType(request.getAccountType());
        account.setOpeningBalance(request.getOpeningBalance());
        account.setCurrentBalance(request.getOpeningBalance());
        account.setStatus(AccountStatus.ACTIVE);

        try {
            FinancialAccount savedAccount = accountRepository.saveAndFlush(account);

            return mapper.toResponse(savedAccount);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateAccountNameViolation(exception)) {
                throw new AccountNameAlreadyExistsException(normalizedName);
            }

            throw exception;
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

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new FinancialAccountClosedException();
        }

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

        try {
            accountRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateAccountNameViolation(exception)) {
                throw new AccountNameAlreadyExistsException(normalizedName);
            }

            throw exception;
        }

        return mapper.toResponse(account);
    }

    @Transactional
    public FinancialAccountResponse closeAccount(Long userId, Long accountId) {
        FinancialAccount account = accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        if (account.getStatus() == AccountStatus.CLOSED) {
            return mapper.toResponse(account);
        }

        account.setStatus(AccountStatus.CLOSED);

        accountRepository.flush();

        return mapper.toResponse(account);
    }

    private boolean isDuplicateAccountNameViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                return "uq_financial_account_user_name_ci".equals(constraintException.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }
}
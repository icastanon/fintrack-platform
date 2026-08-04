package com.fintrack.apiservice.transaction.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionCreateRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionFilterRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionPageResponse;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.apiservice.transaction.mapper.FinancialTransactionMapper;
import com.fintrack.apiservice.transaction.repository.FinancialTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FinancialTransactionService {

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionMapper transactionMapper;

    public FinancialTransactionService(FinancialTransactionRepository transactionRepository,
                                       FinancialAccountRepository accountRepository,
                                       FinancialTransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional
    public FinancialTransactionResponse createTransaction(Long userId, FinancialTransactionCreateRequest request) {
        FinancialAccount account = accountRepository.findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new FinancialAccountClosedException();
        }

        FinancialTransaction transaction = FinancialTransaction.createManual(
                account,
                request.getTransactionType(),
                request.getAmount(),
                normalizeOptionalText(request.getMerchant()),
                normalizeOptionalText(request.getDescription()),
                request.getTransactionDate()
        );

        applyBalanceChange(account, request.getTransactionType(), request.getAmount());

        FinancialTransaction savedTransaction = transactionRepository.save(transaction);

        return transactionMapper.toResponse(savedTransaction);
    }

    private void applyBalanceChange(FinancialAccount account, TransactionType transactionType, BigDecimal amount) {
        if (transactionType == TransactionType.INCOME) {
            account.credit(amount);
        } else {
            account.debit(amount);
        }
    }

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.strip();
    }

    public FinancialTransactionResponse getTransaction(Long userId, Long transactionId) {
        FinancialTransaction transaction = transactionRepository.findByIdAndAccountUserId(transactionId, userId)
                .orElseThrow(FinancialTransactionNotFoundException::new);

        return transactionMapper.toResponse(transaction);
    }

    public FinancialTransactionPageResponse getTransactions(Long userId, FinancialTransactionFilterRequest filter) {
        Sort sort = Sort.by(Sort.Direction.DESC, "transactionDate").and(Sort.by(Sort.Direction.DESC, "id"));

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<FinancialTransaction> transactionPage = transactionRepository.findAllByFilters(
                userId,
                filter.getAccountId(),
                filter.getCategoryId(),
                filter.getTransactionType(),
                filter.getProcessingStatus(),
                filter.getFromDate(),
                filter.getToDate(),
                pageable
        );

        List<FinancialTransactionResponse> content = transactionPage.getContent()
                .stream()
                .map(transactionMapper::toResponse)
                .toList();

        return new FinancialTransactionPageResponse(
                content,
                transactionPage.getNumber(),
                transactionPage.getSize(),
                transactionPage.getTotalElements(),
                transactionPage.getTotalPages(),
                transactionPage.isFirst(),
                transactionPage.isLast()
        );
    }
}
package com.fintrack.apiservice.transaction.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionCreateRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.mapper.FinancialTransactionMapper;
import com.fintrack.apiservice.transaction.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

        FinancialTransaction savedTransaction = transactionRepository.saveAndFlush(transaction);

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
}
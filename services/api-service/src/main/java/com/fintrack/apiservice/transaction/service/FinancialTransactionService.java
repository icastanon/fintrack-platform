package com.fintrack.apiservice.transaction.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
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
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionVersionConflictException;
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
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class FinancialTransactionService {

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionMapper transactionMapper;
    private final CategoryRepository categoryRepository;
    private final OutboxEventWriter outboxEventWriter;

    public FinancialTransactionService(FinancialTransactionRepository transactionRepository,
                                       FinancialAccountRepository accountRepository,
                                       FinancialTransactionMapper transactionMapper,
                                       CategoryRepository categoryRepository,
                                       OutboxEventWriter outboxEventWriter) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionMapper = transactionMapper;
        this.categoryRepository = categoryRepository;
        this.outboxEventWriter = outboxEventWriter;
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

        outboxEventWriter.writeTransactionCreated(transaction.getId(), userId);

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

    @Transactional
    public FinancialTransactionResponse overrideCategory(Long userId, Long transactionId, FinancialTransactionCategoryOverrideRequest request) {
        FinancialTransaction transaction = transactionRepository.findByIdAndAccountUserId(transactionId, userId)
                .orElseThrow(FinancialTransactionNotFoundException::new);

        if (!Objects.equals(request.getVersion(), transaction.getVersion())) {
            throw new FinancialTransactionVersionConflictException();
        }

        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(CategoryNotFoundException::new);

        transaction.overrideCategory(category);

        transactionRepository.flush();

        return transactionMapper.toResponse(transaction);
    }
}
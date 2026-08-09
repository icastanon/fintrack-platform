package com.fintrack.apiservice.budget.service;

import com.fintrack.apiservice.budget.dto.*;
import com.fintrack.apiservice.budget.entity.Budget;
import com.fintrack.apiservice.budget.exception.BudgetAlreadyExistsException;
import com.fintrack.apiservice.budget.exception.BudgetNotFoundException;
import com.fintrack.apiservice.budget.exception.BudgetVersionConflictException;
import com.fintrack.apiservice.budget.mapper.BudgetMapper;
import com.fintrack.apiservice.budget.repository.BudgetRepository;
import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.category.exception.CategoryNotFoundException;
import com.fintrack.apiservice.category.repository.CategoryRepository;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    private static final String UNIQUE_BUDGET_CONSTRAINT = "uq_budget_user_category_month";

    private final BudgetRepository budgetRepository;
    private final FintrackUserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetMapper budgetMapper;

    public BudgetService(BudgetRepository budgetRepository, FintrackUserRepository userRepository, CategoryRepository categoryRepository, BudgetMapper budgetMapper) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.budgetMapper = budgetMapper;
    }

    @Transactional
    public BudgetResponse createBudget(Long userId, BudgetCreateRequest request) {
        FintrackUser user = userRepository.findById(userId).orElseThrow(() -> new FintrackUserNotFoundException(userId));

        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(CategoryNotFoundException::new);

        LocalDate budgetMonth = request.getBudgetMonth().atDay(1);

        if (budgetRepository.existsByUserIdAndCategoryIdAndBudgetMonth(userId, category.getId(), budgetMonth)) {
            throw new BudgetAlreadyExistsException(category.getName(), request.getBudgetMonth());
        }

        Budget budget = Budget.create(
                user,
                category,
                budgetMonth,
                request.getAmount(),
                request.getWarningThresholdPercentage()
        );

        try {
            Budget savedBudget = budgetRepository.saveAndFlush(budget);
            return budgetMapper.toResponse(savedBudget);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateBudgetViolation(exception)) {
                throw new BudgetAlreadyExistsException(category.getName(), request.getBudgetMonth());
            }

            throw exception;
        }
    }

    private boolean isDuplicateBudgetViolation(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(UNIQUE_BUDGET_CONSTRAINT)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    public BudgetResponse getBudget(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId).orElseThrow(BudgetNotFoundException::new);

        return budgetMapper.toResponse(budget);
    }

    public BudgetPageResponse getBudgets(Long userId, BudgetFilterRequest filter) {
        LocalDate budgetMonth = filter.getBudgetMonth() == null ? null : filter.getBudgetMonth().atDay(1);

        Sort sort = Sort.by(Sort.Direction.DESC, "budgetMonth").and(Sort.by(Sort.Direction.DESC, "id"));

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<Budget> budgetPage = budgetRepository.findAllByUserIdAndOptionalMonth(userId, budgetMonth, pageable);

        List<BudgetResponse> content = budgetPage.getContent()
                .stream()
                .map(budgetMapper::toResponse)
                .toList();

        return new BudgetPageResponse(
                content,
                budgetPage.getNumber(),
                budgetPage.getSize(),
                budgetPage.getTotalElements(),
                budgetPage.getTotalPages(),
                budgetPage.isFirst(),
                budgetPage.isLast()
        );
    }

    @Transactional
    public BudgetResponse updateBudget(Long userId, Long budgetId, BudgetUpdateRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId).orElseThrow(BudgetNotFoundException::new);

        if (!Objects.equals(request.getVersion(), budget.getVersion())) {
            throw new BudgetVersionConflictException();
        }

        budget.update(request.getAmount(), request.getWarningThresholdPercentage());

        budgetRepository.flush();

        return budgetMapper.toResponse(budget);
    }

    @Transactional
    public void deleteBudget(Long userId, Long budgetId, Long version) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(BudgetNotFoundException::new);

        if (!Objects.equals(version, budget.getVersion())) {
            throw new BudgetVersionConflictException();
        }

        budgetRepository.delete(budget);
    }
}
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private FintrackUserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BudgetMapper budgetMapper;

    @Captor
    private ArgumentCaptor<Budget> budgetCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void createBudgetCreatesMonthlyCategoryBudget() {
        FintrackUser user = org.mockito.Mockito.mock(FintrackUser.class);
        Category category = org.mockito.Mockito.mock(Category.class);
        BudgetResponse expectedResponse = org.mockito.Mockito.mock(BudgetResponse.class);
        BudgetCreateRequest request = createRequest();

        when(category.getId()).thenReturn(2L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByUserIdAndCategoryIdAndBudgetMonth(7L, 2L, LocalDate.of(2026, 8, 1))).thenReturn(false);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(budgetMapper.toResponse(any(Budget.class))).thenReturn(expectedResponse);

        BudgetResponse result = budgetService.createBudget(7L, request);

        verify(budgetRepository).saveAndFlush(budgetCaptor.capture());

        Budget savedBudget = budgetCaptor.getValue();

        assertThat(result).isSameAs(expectedResponse);
        assertThat(savedBudget.getUser()).isSameAs(user);
        assertThat(savedBudget.getCategory()).isSameAs(category);
        assertThat(savedBudget.getBudgetMonth()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(savedBudget.getAmount()).isEqualByComparingTo("600.00");
        assertThat(savedBudget.getWarningThresholdPercentage()).isEqualTo(80);

        verify(budgetMapper).toResponse(savedBudget);
    }

    @Test
    void createBudgetRejectsMissingUser() {
        BudgetCreateRequest request = createRequest();

        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.createBudget(7L, request)).isInstanceOf(FintrackUserNotFoundException.class);

        verifyNoInteractions(categoryRepository);
        verifyNoInteractions(budgetRepository);
        verifyNoInteractions(budgetMapper);
    }

    @Test
    void createBudgetRejectsMissingCategory() {
        FintrackUser user = org.mockito.Mockito.mock(FintrackUser.class);
        BudgetCreateRequest request = createRequest();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.createBudget(7L, request))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessage("Category was not found");

        verify(budgetRepository, never()).saveAndFlush(any(Budget.class));
        verifyNoInteractions(budgetMapper);
    }

    @Test
    void createBudgetRejectsExistingBudget() {
        FintrackUser user = org.mockito.Mockito.mock(FintrackUser.class);
        Category category = org.mockito.Mockito.mock(Category.class);
        BudgetCreateRequest request = createRequest();

        when(category.getId()).thenReturn(2L);
        when(category.getName()).thenReturn("Groceries");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByUserIdAndCategoryIdAndBudgetMonth(7L, 2L, LocalDate.of(2026, 8, 1))).thenReturn(true);

        assertThatThrownBy(() -> budgetService.createBudget(7L, request))
                .isInstanceOf(BudgetAlreadyExistsException.class)
                .hasMessage("A budget already exists for category Groceries in 2026-08");

        verify(budgetRepository, never()).saveAndFlush(any(Budget.class));
        verifyNoInteractions(budgetMapper);
    }

    @Test
    void createBudgetTranslatesConcurrentDuplicateIntoBudgetAlreadyExistsException() {
        FintrackUser user = org.mockito.Mockito.mock(FintrackUser.class);
        Category category = org.mockito.Mockito.mock(Category.class);
        BudgetCreateRequest request = createRequest();

        when(category.getId()).thenReturn(2L);
        when(category.getName()).thenReturn("Groceries");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(budgetRepository.existsByUserIdAndCategoryIdAndBudgetMonth(7L, 2L, LocalDate.of(2026, 8, 1))).thenReturn(false);
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenThrow(new DataIntegrityViolationException("Constraint uq_budget_user_category_month was violated"));

        assertThatThrownBy(() -> budgetService.createBudget(7L, request))
                .isInstanceOf(BudgetAlreadyExistsException.class)
                .hasMessage("A budget already exists for category Groceries in 2026-08");

        verifyNoInteractions(budgetMapper);
    }

    private BudgetCreateRequest createRequest() {
        BudgetCreateRequest request = new BudgetCreateRequest();
        request.setCategoryId(2L);
        request.setBudgetMonth(YearMonth.of(2026, 8));
        request.setAmount(new BigDecimal("600.00"));
        request.setWarningThresholdPercentage(80);
        return request;
    }

    @Test
    void getBudgetReturnsOwnedBudget() {
        Budget budget = org.mockito.Mockito.mock(Budget.class);
        BudgetResponse expectedResponse = org.mockito.Mockito.mock(BudgetResponse.class);

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(budget));
        when(budgetMapper.toResponse(budget)).thenReturn(expectedResponse);

        BudgetResponse result = budgetService.getBudget(7L, 31L);

        assertThat(result).isSameAs(expectedResponse);

        verify(budgetRepository).findByIdAndUserId(31L, 7L);
        verify(budgetMapper).toResponse(budget);
    }

    @Test
    void getBudgetRejectsMissingOrUnownedBudget() {
        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getBudget(7L, 31L))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessage("Budget was not found");

        verifyNoInteractions(budgetMapper);
    }

    @Test
    void getBudgetsReturnsPaginatedBudgetsWithoutMonthFilter() {
        Budget firstBudget = org.mockito.Mockito.mock(Budget.class);
        Budget secondBudget = org.mockito.Mockito.mock(Budget.class);
        BudgetResponse firstResponse = org.mockito.Mockito.mock(BudgetResponse.class);
        BudgetResponse secondResponse = org.mockito.Mockito.mock(BudgetResponse.class);

        BudgetFilterRequest filter = new BudgetFilterRequest();
        filter.setPage(1);
        filter.setSize(5);

        PageImpl<Budget> page = new PageImpl<>(List.of(firstBudget, secondBudget), PageRequest.of(1, 5), 12);

        when(budgetRepository.findAllByUserIdAndOptionalMonth(eq(7L), eq(null), any(Pageable.class))).thenReturn(page);
        when(budgetMapper.toResponse(firstBudget)).thenReturn(firstResponse);
        when(budgetMapper.toResponse(secondBudget)).thenReturn(secondResponse);

        BudgetPageResponse result = budgetService.getBudgets(7L, filter);

        verify(budgetRepository).findAllByUserIdAndOptionalMonth(eq(7L), eq(null), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("budgetMonth").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);

        assertThat(result.getContent()).containsExactly(firstResponse, secondResponse);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    void getBudgetsConvertsYearMonthFilterToFirstDayOfMonth() {
        BudgetFilterRequest filter = new BudgetFilterRequest();
        filter.setBudgetMonth(YearMonth.of(2026, 8));
        filter.setPage(0);
        filter.setSize(20);

        PageImpl<Budget> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(budgetRepository.findAllByUserIdAndOptionalMonth(eq(7L), eq(LocalDate.of(2026, 8, 1)), any(Pageable.class))).thenReturn(page);

        BudgetPageResponse result = budgetService.getBudgets(7L, filter);

        verify(budgetRepository).findAllByUserIdAndOptionalMonth(eq(7L), eq(LocalDate.of(2026, 8, 1)), any(Pageable.class));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void updateBudgetUpdatesAmountAndThresholdAndFlushesBeforeMapping() {
        Budget budget = org.mockito.Mockito.mock(Budget.class);
        BudgetResponse expectedResponse = org.mockito.Mockito.mock(BudgetResponse.class);
        BudgetUpdateRequest request = createUpdateRequest();

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(budget));
        when(budget.getVersion()).thenReturn(0L);
        when(budgetMapper.toResponse(budget)).thenReturn(expectedResponse);

        BudgetResponse result = budgetService.updateBudget(7L, 31L, request);

        assertThat(result).isSameAs(expectedResponse);

        verify(budget).update(new BigDecimal("750.00"), 85);
        verify(budgetRepository).flush();
        verify(budgetMapper).toResponse(budget);
    }

    @Test
    void updateBudgetRejectsMissingOrUnownedBudget() {
        BudgetUpdateRequest request = createUpdateRequest();

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.updateBudget(7L, 31L, request))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessage("Budget was not found");

        verify(budgetRepository, never()).flush();
        verifyNoInteractions(budgetMapper);
    }

    @Test
    void updateBudgetRejectsStaleClientVersion() {
        Budget budget = org.mockito.Mockito.mock(Budget.class);
        BudgetUpdateRequest request = createUpdateRequest();

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(budget));
        when(budget.getVersion()).thenReturn(1L);

        assertThatThrownBy(() -> budgetService.updateBudget(7L, 31L, request))
                .isInstanceOf(BudgetVersionConflictException.class)
                .hasMessage("Budget was modified by another request. Refresh and try again");

        verify(budget, never()).update(any(BigDecimal.class), any(Integer.class));
        verify(budgetRepository, never()).flush();
        verifyNoInteractions(budgetMapper);
    }

    @Test
    void updateBudgetPropagatesConcurrentOptimisticLockFailureFromFlush() {
        Budget budget = org.mockito.Mockito.mock(Budget.class);
        BudgetUpdateRequest request = createUpdateRequest();
        ObjectOptimisticLockingFailureException exception = new ObjectOptimisticLockingFailureException(Budget.class, 31L);

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(budget));
        when(budget.getVersion()).thenReturn(0L);
        doThrow(exception).when(budgetRepository).flush();

        assertThatThrownBy(() -> budgetService.updateBudget(7L, 31L, request))
                .isSameAs(exception);

        verify(budget).update(new BigDecimal("750.00"), 85);
        verify(budgetRepository).flush();
        verifyNoInteractions(budgetMapper);
    }

    private BudgetUpdateRequest createUpdateRequest() {
        BudgetUpdateRequest request = new BudgetUpdateRequest();
        request.setAmount(new BigDecimal("750.00"));
        request.setWarningThresholdPercentage(85);
        request.setVersion(0L);
        return request;
    }

    @Test
    void deleteBudgetDeletesOwnedBudgetWithMatchingVersion() {
        Budget budget = org.mockito.Mockito.mock(Budget.class);

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(budget));
        when(budget.getVersion()).thenReturn(1L);

        budgetService.deleteBudget(7L, 31L, 1L);

        verify(budgetRepository).findByIdAndUserId(31L, 7L);
        verify(budgetRepository).delete(budget);
    }

    @Test
    void deleteBudgetRejectsMissingOrUnownedBudget() {
        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.deleteBudget(7L, 31L, 1L))
                .isInstanceOf(BudgetNotFoundException.class)
                .hasMessage("Budget was not found");

        verify(budgetRepository, never()).delete(any(Budget.class));
    }

    @Test
    void deleteBudgetRejectsStaleVersion() {
        Budget budget = org.mockito.Mockito.mock(Budget.class);

        when(budgetRepository.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(budget));
        when(budget.getVersion()).thenReturn(2L);

        assertThatThrownBy(() -> budgetService.deleteBudget(7L, 31L, 1L))
                .isInstanceOf(BudgetVersionConflictException.class)
                .hasMessage("Budget was modified by another request. Refresh and try again");

        verify(budgetRepository, never()).delete(any(Budget.class));
    }




}
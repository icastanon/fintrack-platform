package com.fintrack.apiservice.budget.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.budget.dto.*;
import com.fintrack.apiservice.budget.entity.Budget;
import com.fintrack.apiservice.budget.exception.BudgetAlreadyExistsException;
import com.fintrack.apiservice.budget.exception.BudgetNotFoundException;
import com.fintrack.apiservice.budget.exception.BudgetVersionConflictException;
import com.fintrack.apiservice.budget.service.BudgetService;
import com.fintrack.apiservice.category.exception.CategoryNotFoundException;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BudgetController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetService budgetService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void createBudgetReturnsCreatedAndUsesAuthenticatedUserId() throws Exception {
        BudgetResponse response = createResponse();

        when(budgetService.createBudget(eq(7L), any(BudgetCreateRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": 2,
                                          "budgetMonth": "2026-08",
                                          "amount": 600.00,
                                          "warningThresholdPercentage": 80
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.budgetMonth").value("2026-08"))
                .andExpect(jsonPath("$.amount").value(600.00))
                .andExpect(jsonPath("$.warningThresholdPercentage").value(80))
                .andExpect(jsonPath("$.version").value(0));

        ArgumentCaptor<BudgetCreateRequest> requestCaptor = ArgumentCaptor.forClass(BudgetCreateRequest.class);

        verify(budgetService).createBudget(eq(7L), requestCaptor.capture());

        BudgetCreateRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getCategoryId()).isEqualTo(2L);
        assertThat(capturedRequest.getBudgetMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(capturedRequest.getAmount()).isEqualByComparingTo("600.00");
        assertThat(capturedRequest.getWarningThresholdPercentage()).isEqualTo(80);
    }

    @Test
    void createBudgetWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson())
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    @Test
    void createBudgetWithMissingCategoryReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "budgetMonth": "2026-08",
                                          "amount": 600.00,
                                          "warningThresholdPercentage": 80
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void createBudgetWithNonPositiveAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": 2,
                                          "budgetMonth": "2026-08",
                                          "amount": 0,
                                          "warningThresholdPercentage": 80
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void createBudgetWithInvalidWarningThresholdReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "categoryId": 2,
                                          "budgetMonth": "2026-08",
                                          "amount": 600.00,
                                          "warningThresholdPercentage": 100
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void createDuplicateBudgetReturnsConflict() throws Exception {
        when(budgetService.createBudget(eq(7L), any(BudgetCreateRequest.class)))
                .thenThrow(new BudgetAlreadyExistsException("Groceries", YearMonth.of(2026, 8)));

        mockMvc.perform(
                        post("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson())
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("A budget already exists for category Groceries in 2026-08"));
    }

    @Test
    void createBudgetWithMissingCategoryResourceReturnsNotFound() throws Exception {
        when(budgetService.createBudget(eq(7L), any(BudgetCreateRequest.class))).thenThrow(new CategoryNotFoundException());

        mockMvc.perform(
                        post("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Category was not found"));
    }

    private BudgetResponse createResponse() {
        return new BudgetResponse(
                31L,
                2L,
                "Groceries",
                YearMonth.of(2026, 8),
                new BigDecimal("600.00"),
                80,
                0L,
                Instant.parse("2026-08-04T20:00:00Z"),
                Instant.parse("2026-08-04T20:00:00Z")
        );
    }

    private String validRequestJson() {
        return """
                {
                  "categoryId": 2,
                  "budgetMonth": "2026-08",
                  "amount": 600.00,
                  "warningThresholdPercentage": 80
                }
                """;
    }

    @Test
    void getBudgetReturnsOwnedBudget() throws Exception {
        BudgetResponse response = createResponse();

        when(budgetService.getBudget(7L, 31L)).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.budgetMonth").value("2026-08"))
                .andExpect(jsonPath("$.amount").value(600.00))
                .andExpect(jsonPath("$.warningThresholdPercentage").value(80))
                .andExpect(jsonPath("$.version").value(0));

        verify(budgetService).getBudget(7L, 31L);
    }

    @Test
    void getMissingOrUnownedBudgetReturnsNotFound() throws Exception {
        when(budgetService.getBudget(7L, 31L)).thenThrow(new BudgetNotFoundException());

        mockMvc.perform(
                        get("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Budget was not found"));
    }

    @Test
    void getBudgetWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/budgets/31"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    @Test
    void getBudgetsReturnsPaginatedResultsAndBindsFilters() throws Exception {
        BudgetResponse budgetResponse = createResponse();

        BudgetPageResponse pageResponse = new BudgetPageResponse(
                List.of(budgetResponse), 1, 5, 7, 2, false, true);

        when(budgetService.getBudgets(eq(7L), any(BudgetFilterRequest.class))).thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("budgetMonth", "2026-08")
                                .queryParam("page", "1")
                                .queryParam("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(31))
                .andExpect(jsonPath("$.content[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<BudgetFilterRequest> filterCaptor = ArgumentCaptor.forClass(BudgetFilterRequest.class);

        verify(budgetService).getBudgets(eq(7L), filterCaptor.capture());

        BudgetFilterRequest capturedFilter = filterCaptor.getValue();

        assertThat(capturedFilter.getBudgetMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(capturedFilter.getPage()).isEqualTo(1);
        assertThat(capturedFilter.getSize()).isEqualTo(5);
    }

    @Test
    void getBudgetsUsesDefaultPaginationWhenParametersAreMissing() throws Exception {
        BudgetPageResponse pageResponse = new BudgetPageResponse(
                List.of(),
                0,
                20,
                0,
                0,
                true,
                true
        );

        when(budgetService.getBudgets(eq(7L), any(BudgetFilterRequest.class))).thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<BudgetFilterRequest> filterCaptor = ArgumentCaptor.forClass(BudgetFilterRequest.class);

        verify(budgetService).getBudgets(eq(7L), filterCaptor.capture());

        assertThat(filterCaptor.getValue().getBudgetMonth()).isNull();
        assertThat(filterCaptor.getValue().getPage()).isZero();
        assertThat(filterCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void getBudgetsWithNegativePageReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("page", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void getBudgetsWithPageSizeAboveMaximumReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/budgets")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("size", "101")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void getBudgetsWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/budgets"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    @Test
    void updateBudgetReturnsUpdatedBudget() throws Exception {
        BudgetResponse response = createUpdatedResponse();

        when(budgetService.updateBudget(eq(7L), eq(31L), any(BudgetUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "amount": 750.00,
                                      "warningThresholdPercentage": 85,
                                      "version": 0
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.budgetMonth").value("2026-08"))
                .andExpect(jsonPath("$.amount").value(750.00))
                .andExpect(jsonPath("$.warningThresholdPercentage").value(85))
                .andExpect(jsonPath("$.version").value(1));

        ArgumentCaptor<BudgetUpdateRequest> requestCaptor = ArgumentCaptor.forClass(BudgetUpdateRequest.class);

        verify(budgetService).updateBudget(eq(7L), eq(31L), requestCaptor.capture());

        BudgetUpdateRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getAmount()).isEqualByComparingTo("750.00");
        assertThat(capturedRequest.getWarningThresholdPercentage()).isEqualTo(85);
        assertThat(capturedRequest.getVersion()).isZero();
    }

    @Test
    void updateMissingOrUnownedBudgetReturnsNotFound() throws Exception {
        when(budgetService.updateBudget(eq(7L), eq(31L), any(BudgetUpdateRequest.class)))
                .thenThrow(new BudgetNotFoundException());

        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateRequestJson())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Budget was not found"));
    }

    @Test
    void updateBudgetWithStaleVersionReturnsConflict() throws Exception {
        when(budgetService.updateBudget(eq(7L), eq(31L), any(BudgetUpdateRequest.class)))
                .thenThrow(new BudgetVersionConflictException());

        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateRequestJson())
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Budget was modified by another request. Refresh and try again"));
    }

    @Test
    void updateBudgetWithConcurrentOptimisticLockFailureReturnsConflict() throws Exception {
        when(budgetService.updateBudget(eq(7L), eq(31L), any(BudgetUpdateRequest.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Budget.class, 31L));

        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateRequestJson())
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateBudgetWithoutAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "warningThresholdPercentage": 85,
                                      "version": 0
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void updateBudgetWithoutThresholdReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "amount": 750.00,
                                      "version": 0
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void updateBudgetWithoutVersionReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "amount": 750.00,
                                      "warningThresholdPercentage": 85
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(budgetService);
    }

    @Test
    void updateBudgetWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                        put("/api/v1/budgets/31")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validUpdateRequestJson())
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    private BudgetResponse createUpdatedResponse() {
        return new BudgetResponse(
                31L,
                2L,
                "Groceries",
                YearMonth.of(2026, 8),
                new BigDecimal("750.00"),
                85,
                1L,
                Instant.parse("2026-08-04T20:00:00Z"),
                Instant.parse("2026-08-04T22:00:00Z")
        );
    }

    private String validUpdateRequestJson() {
        return """
            {
              "amount": 750.00,
              "warningThresholdPercentage": 85,
              "version": 0
            }
            """;
    }

    @Test
    void deleteBudgetReturnsNoContent() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("version", "1")
                )
                .andExpect(status().isNoContent());

        verify(budgetService).deleteBudget(7L, 31L, 1L);
    }

    @Test
    void deleteMissingOrUnownedBudgetReturnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new BudgetNotFoundException())
                .when(budgetService)
                .deleteBudget(7L, 31L, 1L);

        mockMvc.perform(
                        delete("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("version", "1")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Budget was not found"));
    }

    @Test
    void deleteBudgetWithStaleVersionReturnsConflict() throws Exception {
        org.mockito.Mockito.doThrow(new BudgetVersionConflictException())
                .when(budgetService)
                .deleteBudget(7L, 31L, 1L);

        mockMvc.perform(
                        delete("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("version", "1")
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Budget was modified by another request. Refresh and try again"));
    }

    @Test
    void deleteBudgetWithNegativeVersionReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("version", "-1")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetService);
    }

    @Test
    void deleteBudgetWithoutVersionReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/budgets/31")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetService);
    }

    @Test
    void deleteBudgetWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/budgets/31")
                                .queryParam("version", "1")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }
}
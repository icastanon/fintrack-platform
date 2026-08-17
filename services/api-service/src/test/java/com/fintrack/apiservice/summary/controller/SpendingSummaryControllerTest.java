package com.fintrack.apiservice.summary.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.summary.dto.*;
import com.fintrack.apiservice.summary.model.BudgetStatus;
import com.fintrack.apiservice.summary.service.SpendingSummaryService;
import com.fintrack.apiservice.user.entity.Role;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpendingSummaryController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class SpendingSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingSummaryService spendingSummaryService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void getMonthlyCashFlowReturnsAuthenticatedUsersSummary() throws Exception {
        YearMonth month = YearMonth.of(2026, 8);

        MonthlyCashFlowResponse response = new MonthlyCashFlowResponse(
                month,
                new BigDecimal("1000.00"),
                new BigDecimal("250.50"),
                new BigDecimal("749.50"),
                SupportedCurrency.EUR
        );

        when(spendingSummaryService.getMonthlyCashFlow(7L, month)).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/summaries/cash-flow")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.income").value(1000.00))
                .andExpect(jsonPath("$.expenses").value(250.50))
                .andExpect(jsonPath("$.netCashFlow").value(749.50))
                .andExpect(jsonPath("$.currency").value("EUR"));

        verify(spendingSummaryService).getMonthlyCashFlow(7L, month);
    }

    @Test
    void getMonthlySpendingByCategoryReturnsAuthenticatedUsersSummary() throws Exception {
        YearMonth month = YearMonth.of(2026, 8);

        MonthlyCategorySpendingResponse response = new MonthlyCategorySpendingResponse(
                month,
                new BigDecimal("300.50"),
                SupportedCurrency.GBP,
                List.of(
                        new CategorySpendingItemResponse(10L, "Groceries", new BigDecimal("250.00")),
                        new CategorySpendingItemResponse(11L, "Restaurants", new BigDecimal("50.50"))
                )
        );

        when(spendingSummaryService.getMonthlySpendingByCategory(7L, month)).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-category")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.totalExpenses").value(300.50))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].categoryId").value(10))
                .andExpect(jsonPath("$.categories[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.categories[0].spentAmount").value(250.00))
                .andExpect(jsonPath("$.categories[1].categoryId").value(11))
                .andExpect(jsonPath("$.categories[1].categoryName").value("Restaurants"))
                .andExpect(jsonPath("$.categories[1].spentAmount").value(50.50));

        verify(spendingSummaryService).getMonthlySpendingByCategory(7L, month);
    }

    @Test
    void getMonthlySpendingByAccountReturnsAuthenticatedUsersSummary() throws Exception {
        YearMonth month = YearMonth.of(2026, 8);

        MonthlyAccountSpendingResponse response = new MonthlyAccountSpendingResponse(
                month,
                new BigDecimal("300.50"),
                SupportedCurrency.AUD,
                List.of(
                        new AccountSpendingItemResponse(21L, "Primary checking", new BigDecimal("250.00")),
                        new AccountSpendingItemResponse(23L, "Travel card", new BigDecimal("50.50"))
                )
        );

        when(spendingSummaryService.getMonthlySpendingByAccount(7L, month)).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-account")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.totalExpenses").value(300.50))
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.accounts[0].accountId").value(21))
                .andExpect(jsonPath("$.accounts[0].accountName").value("Primary checking"))
                .andExpect(jsonPath("$.accounts[0].spentAmount").value(250.00))
                .andExpect(jsonPath("$.accounts[1].accountId").value(23))
                .andExpect(jsonPath("$.accounts[1].accountName").value("Travel card"))
                .andExpect(jsonPath("$.accounts[1].spentAmount").value(50.50));

        verify(spendingSummaryService).getMonthlySpendingByAccount(7L, month);
    }

    @Test
    void getMonthlyBudgetUsageReturnsAuthenticatedUsersSummary() throws Exception {
        YearMonth month = YearMonth.of(2026, 8);

        MonthlyBudgetUsageResponse response = new MonthlyBudgetUsageResponse(
                month,
                SupportedCurrency.CAD,
                List.of(
                        new BudgetUsageItemResponse(
                                31L,
                                10L,
                                "Groceries",
                                new BigDecimal("300.00"),
                                80,
                                new BigDecimal("250.00"),
                                new BigDecimal("50.00"),
                                new BigDecimal("83.33"),
                                BudgetStatus.WARNING
                        ),
                        new BudgetUsageItemResponse(
                                32L,
                                11L,
                                "Restaurants",
                                new BigDecimal("50.00"),
                                80,
                                new BigDecimal("50.00"),
                                new BigDecimal("0.00"),
                                new BigDecimal("100.00"),
                                BudgetStatus.EXCEEDED
                        ),
                        new BudgetUsageItemResponse(
                                33L,
                                12L,
                                "Travel",
                                new BigDecimal("100.00"),
                                80,
                                new BigDecimal("125.00"),
                                new BigDecimal("-25.00"),
                                new BigDecimal("125.00"),
                                BudgetStatus.EXCEEDED
                        )
                )
        );

        when(spendingSummaryService.getMonthlyBudgetUsage(7L, month)).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/summaries/budget-usage")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.budgets.length()").value(3))
                .andExpect(jsonPath("$.budgets[0].budgetId").value(31))
                .andExpect(jsonPath("$.budgets[0].categoryId").value(10))
                .andExpect(jsonPath("$.budgets[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$.budgets[0].budgetAmount").value(300.00))
                .andExpect(jsonPath("$.budgets[0].warningThresholdPercentage").value(80))
                .andExpect(jsonPath("$.budgets[0].spentAmount").value(250.00))
                .andExpect(jsonPath("$.budgets[0].remainingAmount").value(50.00))
                .andExpect(jsonPath("$.budgets[0].usagePercentage").value(83.33))
                .andExpect(jsonPath("$.budgets[0].status").value("WARNING"))
                .andExpect(jsonPath("$.budgets[1].remainingAmount").value(0.00))
                .andExpect(jsonPath("$.budgets[1].status").value("EXCEEDED"))
                .andExpect(jsonPath("$.budgets[2].spentAmount").value(125.00))
                .andExpect(jsonPath("$.budgets[2].remainingAmount").value(-25.00))
                .andExpect(jsonPath("$.budgets[2].status").value("EXCEEDED"));

        verify(spendingSummaryService).getMonthlyBudgetUsage(7L, month);
    }

    @Test
    void summaryEndpointsWithoutJwtReturnUnauthorized() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/cash-flow")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-category")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-account")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/v1/summaries/budget-usage")
                                .queryParam("month", "2026-08")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlyCashFlowWithoutMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/cash-flow")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Parameter is required"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlyCashFlowWithInvalidMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/cash-flow")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "not-a-month")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Value has an invalid format"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlySpendingByCategoryWithoutMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-category")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Parameter is required"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlySpendingByCategoryWithInvalidMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-category")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "not-a-month")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Value has an invalid format"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlySpendingByAccountWithoutMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-account")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Parameter is required"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlySpendingByAccountWithInvalidMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/spending-by-account")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "not-a-month")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Value has an invalid format"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlyBudgetUsageWithoutMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/budget-usage")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Parameter is required"));

        verifyNoInteractions(spendingSummaryService);
    }

    @Test
    void getMonthlyBudgetUsageWithInvalidMonthReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/summaries/budget-usage")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("month", "not-a-month")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.errors.month").value("Value has an invalid format"));

        verifyNoInteractions(spendingSummaryService);
    }
}
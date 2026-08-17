package com.fintrack.apiservice.summary.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.summary.dto.MonthlyAccountSpendingResponse;
import com.fintrack.apiservice.summary.dto.MonthlyBudgetUsageResponse;
import com.fintrack.apiservice.summary.dto.MonthlyCashFlowResponse;
import com.fintrack.apiservice.summary.dto.MonthlyCategorySpendingResponse;
import com.fintrack.apiservice.summary.service.SpendingSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

import static com.fintrack.apiservice.common.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/summaries")
@Tag(name = "Spending Summaries", description = "View aggregated financial reporting for the authenticated user")
@SecurityRequirement(name = BEARER_AUTH)
public class SpendingSummaryController {

    private final SpendingSummaryService spendingSummaryService;

    public SpendingSummaryController(SpendingSummaryService spendingSummaryService) {
        this.spendingSummaryService = spendingSummaryService;
    }

    @GetMapping("/cash-flow")
    @Operation(
            summary = "Get monthly cash flow",
            description = "Returns processed income, expenses, and net cash flow for the requested month"
    )
    public ResponseEntity<MonthlyCashFlowResponse> getMonthlyCashFlow(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Parameter(description = "Month to summarize", example = "2026-08")
            @RequestParam
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month
    ) {
        MonthlyCashFlowResponse response = spendingSummaryService.getMonthlyCashFlow(
                principal.getUserId(),
                month
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/spending-by-category")
    @Operation(
            summary = "Get monthly spending by category",
            description = "Returns processed expenses grouped by category for the requested month"
    )
    public ResponseEntity<MonthlyCategorySpendingResponse> getMonthlySpendingByCategory(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Parameter(description = "Month to summarize", example = "2026-08")
            @RequestParam
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month
    ) {
        MonthlyCategorySpendingResponse response = spendingSummaryService.getMonthlySpendingByCategory(
                principal.getUserId(),
                month
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/spending-by-account")
    @Operation(
            summary = "Get monthly spending by account",
            description = "Returns processed expenses grouped by financial account for the requested month"
    )
    public ResponseEntity<MonthlyAccountSpendingResponse> getMonthlySpendingByAccount(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Parameter(description = "Month to summarize", example = "2026-08")
            @RequestParam
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month
    ) {
        MonthlyAccountSpendingResponse response = spendingSummaryService.getMonthlySpendingByAccount(
                principal.getUserId(),
                month
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/budget-usage")
    @Operation(
            summary = "Get monthly budget usage",
            description = "Returns each monthly budget with current processed spending, usage percentage, and status"
    )
    public ResponseEntity<MonthlyBudgetUsageResponse> getMonthlyBudgetUsage(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Parameter(description = "Month to summarize", example = "2026-08")
            @RequestParam
            @DateTimeFormat(pattern = "yyyy-MM")
            YearMonth month
    ) {
        MonthlyBudgetUsageResponse response = spendingSummaryService.getMonthlyBudgetUsage(
                principal.getUserId(),
                month
        );

        return ResponseEntity.ok(response);
    }
}
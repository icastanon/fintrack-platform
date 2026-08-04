package com.fintrack.apiservice.budget.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.budget.dto.*;
import com.fintrack.apiservice.budget.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.fintrack.apiservice.common.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Create and manage monthly category budgets")
@SecurityRequirement(name = BEARER_AUTH)
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @Operation(summary = "Create budget", description = "Creates a monthly budget for one category")
    public ResponseEntity<BudgetResponse> createBudget(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,

                                                       @Valid
                                                       @RequestBody
                                                       BudgetCreateRequest request) {
        BudgetResponse response = budgetService.createBudget(principal.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{budgetId}")
    @Operation(summary = "Get budget", description = "Returns one budget belonging to the authenticated user")
    public ResponseEntity<BudgetResponse> getBudget(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
                                                    @PathVariable Long budgetId) {
        BudgetResponse response = budgetService.getBudget(principal.getUserId(), budgetId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List budgets", description = "Returns the authenticated user's budgets with optional month filtering")
    public ResponseEntity<BudgetPageResponse> getBudgets(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
                                                         @Valid
                                                         @ModelAttribute
                                                         BudgetFilterRequest filter) {
        BudgetPageResponse response = budgetService.getBudgets(principal.getUserId(), filter);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{budgetId}")
    @Operation(summary = "Update budget", description = "Updates the amount and warning threshold for an owned budget")
    public ResponseEntity<BudgetResponse> updateBudget(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long budgetId,
            @Valid @RequestBody BudgetUpdateRequest request
    ) {
        BudgetResponse response = budgetService.updateBudget(principal.getUserId(), budgetId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{budgetId}")
    @Operation(summary = "Delete budget", description = "Deletes an owned monthly budget using optimistic locking")
    public ResponseEntity<Void> deleteBudget(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long budgetId,
            @RequestParam @PositiveOrZero(message = "Version cannot be negative") Long version
    ) {
        budgetService.deleteBudget(principal.getUserId(), budgetId, version);

        return ResponseEntity.noContent().build();
    }
}
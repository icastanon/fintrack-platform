package com.fintrack.apiservice.transaction.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionCreateRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionFilterRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionPageResponse;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.service.FinancialTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.fintrack.apiservice.common.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Create and manage the authenticated user's financial transactions")
@SecurityRequirement(name = BEARER_AUTH)
public class FinancialTransactionController {

    private final FinancialTransactionService transactionService;

    public FinancialTransactionController(FinancialTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(
            summary = "Create transaction",
            description = "Creates a manual transaction and immediately updates the associated account balance"
    )
    public ResponseEntity<FinancialTransactionResponse> createTransaction(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
                                                                          @Valid @RequestBody FinancialTransactionCreateRequest request) {

        FinancialTransactionResponse response = transactionService.createTransaction(principal.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction", description = "Returns one financial transaction owned by the authenticated user")
    public ResponseEntity<FinancialTransactionResponse> getTransaction(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
                                                                       @PathVariable Long transactionId) {

        FinancialTransactionResponse response = transactionService.getTransaction(principal.getUserId(), transactionId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "Returns the authenticated user's transactions using optional filters, pagination, and newest-first sorting")
    public ResponseEntity<FinancialTransactionPageResponse> getTransactions(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,

            @Valid
            @ModelAttribute
            FinancialTransactionFilterRequest filter
    ) {
        FinancialTransactionPageResponse response = transactionService.getTransactions(principal.getUserId(), filter);

        return ResponseEntity.ok(response);
    }
}
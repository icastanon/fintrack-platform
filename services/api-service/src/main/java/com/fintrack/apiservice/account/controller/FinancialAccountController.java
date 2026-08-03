package com.fintrack.apiservice.account.controller;

import com.fintrack.apiservice.account.dto.FinancialAccountCreateRequest;
import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.dto.FinancialAccountUpdateRequest;
import com.fintrack.apiservice.account.service.FinancialAccountService;
import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.common.dto.PageResponse;
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
@RequestMapping("/api/v1/accounts")
@Tag(
        name = "Financial Accounts",
        description = "Create and manage the authenticated user's accounts"
)
@SecurityRequirement(name = BEARER_AUTH)
public class FinancialAccountController {

    private final FinancialAccountService accountService;

    public FinancialAccountController(
            FinancialAccountService accountService
    ) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<FinancialAccountResponse> createAccount(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,

            @Valid @RequestBody
            FinancialAccountCreateRequest request
    ) {
        FinancialAccountResponse response = accountService.createAccount(principal.getUserId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<FinancialAccountResponse>> getAccounts(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page cannot be negative")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100")
            int size
    ) {
        PageResponse<FinancialAccountResponse> response = accountService.getAccounts(principal.getUserId(), page, size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<FinancialAccountResponse> getAccount(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,

            @PathVariable
            Long accountId
    ) {
        FinancialAccountResponse response = accountService.getAccount(principal.getUserId(), accountId);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<FinancialAccountResponse> updateAccount(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,

            @PathVariable
            Long accountId,

            @Valid @RequestBody
            FinancialAccountUpdateRequest request
    ) {
        FinancialAccountResponse response = accountService.updateAccount(principal.getUserId(), accountId, request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{accountId}/close")
    public ResponseEntity<FinancialAccountResponse> closeAccount(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,

            @PathVariable
            Long accountId
    ) {
        FinancialAccountResponse response = accountService.closeAccount(principal.getUserId(), accountId);

        return ResponseEntity.ok(response);
    }
}
package com.fintrack.apiservice.account.controller;

import com.fintrack.apiservice.account.dto.FinancialAccountCreateRequest;
import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.service.FinancialAccountService;
import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
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
}
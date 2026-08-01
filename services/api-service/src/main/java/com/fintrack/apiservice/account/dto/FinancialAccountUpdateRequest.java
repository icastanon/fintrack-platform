package com.fintrack.apiservice.account.dto;

import com.fintrack.apiservice.account.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinancialAccountUpdateRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Version is required")
    private Long version;
}
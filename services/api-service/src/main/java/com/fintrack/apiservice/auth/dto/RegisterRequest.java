package com.fintrack.apiservice.auth.dto;

import com.fintrack.apiservice.user.entity.SupportedCurrency;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 10, max = 64, message = "Password must be between 10 and 64 characters")
    private String password;

    @NotNull(message = "Currency is required")
    private SupportedCurrency currency;
}
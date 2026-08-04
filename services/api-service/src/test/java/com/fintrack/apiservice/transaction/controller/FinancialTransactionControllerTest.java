package com.fintrack.apiservice.transaction.controller;

import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionCreateRequest;
import com.fintrack.apiservice.transaction.dto.FinancialTransactionResponse;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionSource;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.transaction.service.FinancialTransactionService;
import com.fintrack.apiservice.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialTransactionController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class FinancialTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialTransactionService transactionService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void createTransactionReturnsCreatedAndUsesAuthenticatedUserId() throws Exception {
        FinancialTransactionResponse response = createResponse();

        when(transactionService.createTransaction(eq(7L), any(FinancialTransactionCreateRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": 15,
                                          "transactionType": "EXPENSE",
                                          "amount": 83.42,
                                          "merchant": "Publix #1472",
                                          "description": "Weekly groceries",
                                          "transactionDate": "2026-08-03"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.accountId").value(15))
                .andExpect(jsonPath("$.accountName").value("Primary Checking"))
                .andExpect(jsonPath("$.categoryId").doesNotExist())
                .andExpect(jsonPath("$.categoryName").doesNotExist())
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(83.42))
                .andExpect(jsonPath("$.merchant").value("Publix #1472"))
                .andExpect(jsonPath("$.description").value("Weekly groceries"))
                .andExpect(jsonPath("$.transactionDate").value("2026-08-03"))
                .andExpect(jsonPath("$.processingStatus").value("PENDING"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.manualCategoryOverride").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").value("2026-08-03T20:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-03T20:00:00Z"));

        ArgumentCaptor<FinancialTransactionCreateRequest> requestCaptor = ArgumentCaptor.forClass(FinancialTransactionCreateRequest.class);

        verify(transactionService).createTransaction(eq(7L), requestCaptor.capture());

        FinancialTransactionCreateRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getAccountId()).isEqualTo(15L);
        assertThat(capturedRequest.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(capturedRequest.getAmount()).isEqualByComparingTo("83.42");
        assertThat(capturedRequest.getMerchant()).isEqualTo("Publix #1472");
        assertThat(capturedRequest.getDescription()).isEqualTo("Weekly groceries");
        assertThat(capturedRequest.getTransactionDate()).isEqualTo(LocalDate.of(2026, 8, 3));
    }

    @Test
    void createTransactionWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson())
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transactionService);
    }

    @Test
    void createTransactionWithZeroAmountReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": 15,
                                          "transactionType": "EXPENSE",
                                          "amount": 0,
                                          "merchant": "Publix",
                                          "description": "Groceries",
                                          "transactionDate": "2026-08-03"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void createTransactionWithTooManyDecimalPlacesReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": 15,
                                          "transactionType": "EXPENSE",
                                          "amount": 83.425,
                                          "merchant": "Publix",
                                          "description": "Groceries",
                                          "transactionDate": "2026-08-03"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void createTransactionWithBlankMerchantReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": 15,
                                          "transactionType": "EXPENSE",
                                          "amount": 83.42,
                                          "merchant": "   ",
                                          "description": "Groceries",
                                          "transactionDate": "2026-08-03"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void createTransactionWithFutureDateReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": 15,
                                          "transactionType": "EXPENSE",
                                          "amount": 83.42,
                                          "merchant": "Publix",
                                          "description": "Groceries",
                                          "transactionDate": "2999-01-01"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void createTransactionForMissingOrUnownedAccountReturnsNotFound() throws Exception {
        when(transactionService.createTransaction(eq(7L), any(FinancialTransactionCreateRequest.class)))
                .thenThrow(new FinancialAccountNotFoundException());

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson())
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void createTransactionForClosedAccountReturnsConflict() throws Exception {
        when(transactionService.createTransaction(eq(7L), any(FinancialTransactionCreateRequest.class)))
                .thenThrow(new FinancialAccountClosedException());

        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequestJson())
                )
                .andExpect(status().isConflict());
    }

    private FinancialTransactionResponse createResponse() {
        return new FinancialTransactionResponse(
                41L,
                15L,
                "Primary Checking",
                null,
                null,
                TransactionType.EXPENSE,
                new BigDecimal("83.42"),
                "Publix #1472",
                "Weekly groceries",
                LocalDate.of(2026, 8, 3),
                ProcessingStatus.PENDING,
                TransactionSource.MANUAL,
                false,
                0L,
                Instant.parse("2026-08-03T20:00:00Z"),
                Instant.parse("2026-08-03T20:00:00Z")
        );
    }

    private String validRequestJson() {
        return """
                {
                  "accountId": 15,
                  "transactionType": "EXPENSE",
                  "amount": 83.42,
                  "merchant": "Publix #1472",
                  "description": "Weekly groceries",
                  "transactionDate": "2026-08-03"
                }
                """;
    }
}
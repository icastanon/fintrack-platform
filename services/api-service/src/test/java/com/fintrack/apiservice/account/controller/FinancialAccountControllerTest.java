package com.fintrack.apiservice.account.controller;

import com.fintrack.apiservice.account.dto.FinancialAccountCreateRequest;
import com.fintrack.apiservice.account.dto.FinancialAccountResponse;
import com.fintrack.apiservice.account.dto.FinancialAccountUpdateRequest;
import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.AccountType;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.exception.FinancialAccountVersionConflictException;
import com.fintrack.apiservice.account.service.FinancialAccountService;
import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

@WebMvcTest(FinancialAccountController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class FinancialAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialAccountService accountService;

    @MockitoBean
    private JwtService jwtService;

    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void createAccountReturnsCreatedAndUsesAuthenticatedUserId() throws Exception {
        FinancialAccountResponse serviceResponse = new FinancialAccountResponse();
        serviceResponse.setId(100L);
        serviceResponse.setName("Main Checking");
        serviceResponse.setAccountType(AccountType.CHECKING);
        serviceResponse.setCurrency("EUR");
        serviceResponse.setOpeningBalance(new BigDecimal("2500.00"));
        serviceResponse.setCurrentBalance(new BigDecimal("2500.00"));
        serviceResponse.setStatus(AccountStatus.ACTIVE);
        serviceResponse.setVersion(0L);

        when(accountService.createAccount(eq(7L), any(FinancialAccountCreateRequest.class))).thenReturn(serviceResponse);

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Main Checking",
                                      "accountType": "CHECKING",
                                      "openingBalance": 2500.00
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("Main Checking"))
                .andExpect(jsonPath("$.accountType").value("CHECKING"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0));

        ArgumentCaptor<FinancialAccountCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancialAccountCreateRequest.class);

        verify(accountService).createAccount(eq(7L), requestCaptor.capture());

        FinancialAccountCreateRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getName()).isEqualTo("Main Checking");
        assertThat(capturedRequest.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(capturedRequest.getOpeningBalance()).isEqualByComparingTo("2500.00");
    }

    @Test
    void createAccountWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Main Checking",
                                      "accountType": "CHECKING",
                                      "openingBalance": 2500.00
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    @Test
    void createAccountWithInvalidRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .header("Authorization", "Bearer valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": " ",
                                      "openingBalance": 100.00
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountReturnsAccountAndUsesAuthenticatedUserId() throws Exception {

        FinancialAccountResponse serviceResponse = new FinancialAccountResponse();

        serviceResponse.setId(100L);
        serviceResponse.setName("Main Checking");
        serviceResponse.setAccountType(AccountType.CHECKING);
        serviceResponse.setCurrency("USD");
        serviceResponse.setOpeningBalance(new BigDecimal("2500.00"));
        serviceResponse.setCurrentBalance(new BigDecimal("2500.00"));
        serviceResponse.setStatus(AccountStatus.ACTIVE);
        serviceResponse.setVersion(0L);

        when(accountService.getAccount(7L, 100L)).thenReturn(serviceResponse);

        mockMvc.perform(
                        get("/api/v1/accounts/100")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(
                        jsonPath("$.name")
                                .value("Main Checking")
                )
                .andExpect(
                        jsonPath("$.accountType")
                                .value("CHECKING")
                )
                .andExpect(
                        jsonPath("$.currency")
                                .value("USD")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );

        verify(accountService).getAccount(7L, 100L);
    }

    @Test
    void getAccountUnavailableToAuthenticatedUserReturnsNotFound() throws Exception {

        when(accountService.getAccount(7L, 999L)).thenThrow(new FinancialAccountNotFoundException());

        mockMvc.perform(
                        get("/api/v1/accounts/999")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message")
                                .value("Financial account not found")
                );

        verify(accountService).getAccount(7L, 999L);
    }

    @Test
    void updateAccountReturnsUpdatedAccountAndUsesAuthenticatedUserId() throws Exception {

        FinancialAccountResponse serviceResponse = new FinancialAccountResponse();

        serviceResponse.setId(100L);
        serviceResponse.setName("Primary Checking");
        serviceResponse.setAccountType(AccountType.SAVINGS);
        serviceResponse.setCurrency("USD");
        serviceResponse.setOpeningBalance(new BigDecimal("2500.00"));
        serviceResponse.setCurrentBalance(new BigDecimal("2500.00"));
        serviceResponse.setStatus(AccountStatus.ACTIVE);
        serviceResponse.setVersion(4L);

        when(accountService.updateAccount(
                eq(7L),
                eq(100L),
                any(FinancialAccountUpdateRequest.class)
        )).thenReturn(serviceResponse);

        mockMvc.perform(
                        put("/api/v1/accounts/100")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Primary Checking",
                                      "accountType": "SAVINGS",
                                      "version": 3
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(
                        jsonPath("$.name")
                                .value("Primary Checking")
                )
                .andExpect(
                        jsonPath("$.accountType")
                                .value("SAVINGS")
                )
                .andExpect(jsonPath("$.version").value(4));

        ArgumentCaptor<FinancialAccountUpdateRequest> requestCaptor = ArgumentCaptor.forClass(FinancialAccountUpdateRequest.class);

        verify(accountService).updateAccount(eq(7L), eq(100L), requestCaptor.capture());

        FinancialAccountUpdateRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getName()).isEqualTo("Primary Checking");

        assertThat(capturedRequest.getAccountType()).isEqualTo(AccountType.SAVINGS);

        assertThat(capturedRequest.getVersion()).isEqualTo(3L);
    }

    @Test
    void updateAccountWithStaleVersionReturnsConflict() throws Exception {

        when(accountService.updateAccount(
                eq(7L),
                eq(100L),
                any(FinancialAccountUpdateRequest.class)
        )).thenThrow(
                new FinancialAccountVersionConflictException()
        );

        mockMvc.perform(
                        put("/api/v1/accounts/100")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Primary Checking",
                                      "accountType": "CHECKING",
                                      "version": 2
                                    }
                                    """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "The financial account was modified by another request. " +
                                                "Reload the account and try again."
                                )
                );

        verify(accountService).updateAccount(
                eq(7L),
                eq(100L),
                any(FinancialAccountUpdateRequest.class)
        );
    }

    @Test
    void closeAccountReturnsClosedAccountAndUsesAuthenticatedUserId() throws Exception {

        FinancialAccountResponse serviceResponse = new FinancialAccountResponse();

        serviceResponse.setId(100L);
        serviceResponse.setName("Main Checking");
        serviceResponse.setAccountType(AccountType.CHECKING);
        serviceResponse.setCurrency("USD");
        serviceResponse.setOpeningBalance(new BigDecimal("2500.00"));
        serviceResponse.setCurrentBalance(new BigDecimal("2500.00"));
        serviceResponse.setStatus(AccountStatus.CLOSED);
        serviceResponse.setVersion(4L);

        when(accountService.closeAccount(7L, 100L)).thenReturn(serviceResponse);

        mockMvc.perform(
                        patch("/api/v1/accounts/100/close")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(
                        jsonPath("$.status")
                                .value("CLOSED")
                )
                .andExpect(jsonPath("$.version").value(4));

        verify(accountService).closeAccount(7L, 100L);
    }

    @Test
    void updateAccountWithInvalidRequestReturnsBadRequest() throws Exception {

        mockMvc.perform(
                        put("/api/v1/accounts/100")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": " ",
                                      "accountType": null,
                                      "version": null
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountsReturnsRequestedPageAndUsesAuthenticatedUserId() throws Exception {

        FinancialAccountResponse accountResponse = new FinancialAccountResponse();

        accountResponse.setId(100L);
        accountResponse.setName("Main Checking");
        accountResponse.setAccountType(AccountType.CHECKING);
        accountResponse.setCurrency("USD");
        accountResponse.setOpeningBalance(new BigDecimal("2500.00"));
        accountResponse.setCurrentBalance(new BigDecimal("2500.00"));
        accountResponse.setStatus(AccountStatus.ACTIVE);
        accountResponse.setVersion(0L);

        PageRequest pageRequest = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "createdAt"));

        PageResponse<FinancialAccountResponse> serviceResponse =
                new PageResponse<>(
                        new PageImpl<>(
                                List.of(accountResponse),
                                pageRequest,
                                11
                        )
                );

        when(accountService.getAccounts(7L, 2, 5)).thenReturn(serviceResponse);

        mockMvc.perform(
                        get("/api/v1/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                                .param("page", "2")
                                .param("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(
                        jsonPath("$.content[0].name")
                                .value("Main Checking")
                )
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        verify(accountService).getAccounts(7L, 2, 5);
    }

    @Test
    void getAccountsUsesDefaultPaginationValues() throws Exception {

        PageResponse<FinancialAccountResponse> serviceResponse =
                new PageResponse<>(
                        new PageImpl<>(
                                List.of(),
                                PageRequest.of(0, 20),
                                0
                        )
                );

        when(accountService.getAccounts(7L, 0, 20)).thenReturn(serviceResponse);

        mockMvc.perform(
                        get("/api/v1/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(accountService).getAccounts(7L, 0, 20);
    }

    @Test
    void getAccountsWithNegativePageReturnsBadRequest() throws Exception {

        mockMvc.perform(
                        get("/api/v1/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                                .param("page", "-1")
                                .param("size", "20")
                ).andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    void getAccountsWithExcessivePageSizeReturnsBadRequest() throws Exception {

        mockMvc.perform(
                        get("/api/v1/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer valid-token"
                                )
                                .param("page", "0")
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }
}
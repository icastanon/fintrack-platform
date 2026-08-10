package com.fintrack.apiservice.transactionimport.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.entity.TransactionImportStatus;
import com.fintrack.apiservice.transactionimport.exception.InvalidTransactionImportFileException;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportStorageException;
import com.fintrack.apiservice.transactionimport.service.TransactionImportSubmissionService;
import com.fintrack.apiservice.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionImportController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class TransactionImportControllerTest {

    private static final byte[] CSV_CONTENT = (
            "transactionDate,type,amount\n" +
                    "2026-08-10,EXPENSE,25.00"
    ).getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionImportSubmissionService submissionService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void submitImportReturnsAcceptedQueuedImportAndUsesAuthenticatedUserId() throws Exception {
        MockMultipartFile file = createFile();
        TransactionImportResponse response = createResponse();

        when(submissionService.submit(eq(7L), eq(15L), any(MultipartFile.class)))
                .thenReturn(response);

        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .file(file)
                                .param("accountId", "15")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.accountId").value(15))
                .andExpect(jsonPath("$.accountName").value("Primary Checking"))
                .andExpect(jsonPath("$.originalFileName").value("august-transactions.csv"))
                .andExpect(jsonPath("$.contentType").value("text/csv"))
                .andExpect(jsonPath("$.fileSizeBytes").value(CSV_CONTENT.length))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.totalRows").doesNotExist())
                .andExpect(jsonPath("$.processedRows").value(0))
                .andExpect(jsonPath("$.successfulRows").value(0))
                .andExpect(jsonPath("$.skippedRows").value(0))
                .andExpect(jsonPath("$.failedRows").value(0))
                .andExpect(jsonPath("$.rejectedOutputAvailable").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").value("2026-08-10T16:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-10T16:00:00Z"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);

        verify(submissionService).submit(eq(7L), eq(15L), fileCaptor.capture());

        MultipartFile capturedFile = fileCaptor.getValue();

        assertThat(capturedFile.getOriginalFilename()).isEqualTo("august-transactions.csv");
        assertThat(capturedFile.getContentType()).isEqualTo("text/csv");
        assertThat(capturedFile.getBytes()).isEqualTo(CSV_CONTENT);
    }

    @Test
    void submitImportWithInvalidCsvReturnsBadRequest() throws Exception {
        InvalidTransactionImportFileException exception =
                new InvalidTransactionImportFileException(
                        "The uploaded file must use the .csv extension"
                );

        when(submissionService.submit(eq(7L), eq(15L), any(MultipartFile.class)))
                .thenThrow(exception);

        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .file(createFile())
                                .param("accountId", "15")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("The uploaded file must use the .csv extension"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(submissionService).submit(eq(7L), eq(15L), any(MultipartFile.class));
    }

    @Test
    void submitImportWhenStorageFailsReturnsServiceUnavailableWithoutLeakingCause() throws Exception {
        TransactionImportStorageException exception =
                new TransactionImportStorageException(
                        "Failed to upload the transaction import file",
                        new IllegalStateException("Internal S3 connection details")
                );

        when(submissionService.submit(eq(7L), eq(15L), any(MultipartFile.class)))
                .thenThrow(exception);

        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .file(createFile())
                                .param("accountId", "15")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Transaction import storage is temporarily unavailable"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("S3 connection")
                        )))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(submissionService).submit(eq(7L), eq(15L), any(MultipartFile.class));
    }

    @Test
    void submitImportWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .file(createFile())
                                .param("accountId", "15")
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(submissionService);
    }

    @Test
    void submitImportWithoutAccountIdReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .file(createFile())
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionService);
    }

    @Test
    void submitImportWithNonPositiveAccountIdReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .file(createFile())
                                .param("accountId", "0")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionService);
    }

    @Test
    void submitImportWithoutFileReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/imports")
                                .param("accountId", "15")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(submissionService);
    }

    private MockMultipartFile createFile() {
        return new MockMultipartFile(
                "file",
                "august-transactions.csv",
                "text/csv",
                CSV_CONTENT
        );
    }

    private TransactionImportResponse createResponse() {
        return new TransactionImportResponse(
                41L,
                15L,
                "Primary Checking",
                "august-transactions.csv",
                "text/csv",
                (long) CSV_CONTENT.length,
                TransactionImportStatus.QUEUED,
                null,
                0L,
                0L,
                0L,
                0L,
                null,
                false,
                0L,
                null,
                null,
                Instant.parse("2026-08-10T16:00:00Z"),
                Instant.parse("2026-08-10T16:00:00Z")
        );
    }
}
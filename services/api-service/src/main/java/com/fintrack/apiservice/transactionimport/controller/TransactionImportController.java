package com.fintrack.apiservice.transactionimport.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.transactionimport.dto.TransactionImportRejectedOutput;
import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.service.TransactionImportRejectedOutputService;
import com.fintrack.apiservice.transactionimport.service.TransactionImportService;
import com.fintrack.apiservice.transactionimport.service.TransactionImportSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static com.fintrack.apiservice.common.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Transaction Imports", description = "Submit and monitor asynchronous CSV transaction imports")
@SecurityRequirement(name = BEARER_AUTH)
public class TransactionImportController {

    private final TransactionImportSubmissionService submissionService;
    private final TransactionImportService transactionImportService;
    private final TransactionImportRejectedOutputService rejectedOutputService;

    public TransactionImportController(TransactionImportSubmissionService submissionService,
                                       TransactionImportService transactionImportService,
                                       TransactionImportRejectedOutputService rejectedOutputService) {
        this.submissionService = submissionService;
        this.transactionImportService = transactionImportService;
        this.rejectedOutputService = rejectedOutputService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Submit transaction import",
            description = "Uploads a CSV for an owned account and returns a queued asynchronous import"
    )
    public ResponseEntity<TransactionImportResponse> submitImport(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam @Positive(message = "Account ID must be positive") Long accountId,
            @RequestPart("file") MultipartFile file
    ) {
        TransactionImportResponse response = submissionService.submit(
                principal.getUserId(),
                accountId,
                file
        );

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{importId}")
    @Operation(
            summary = "Get transaction import",
            description = "Returns the current status and progress of one import owned by the authenticated user"
    )
    public ResponseEntity<TransactionImportResponse> getImport(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable @Positive(message = "Import ID must be positive") Long importId
    ) {
        TransactionImportResponse response = transactionImportService.getImport(
                principal.getUserId(),
                importId
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{importId}/rejected-output", produces = "text/csv")
    @Operation(
            summary = "Download rejected import rows",
            description = "Downloads the rejected-row CSV for one import owned by the authenticated user"
    )
    public ResponseEntity<byte[]> getRejectedOutput(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable @Positive(message = "Import ID must be positive") Long importId
    ) {
        TransactionImportRejectedOutput output = rejectedOutputService.getRejectedOutput(
                principal.getUserId(),
                importId
        );

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(output.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(output.getContent().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(output.getContent());
    }
}
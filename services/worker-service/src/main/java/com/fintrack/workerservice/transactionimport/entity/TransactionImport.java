package com.fintrack.workerservice.transactionimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "transaction_import")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionImport {

    private static final int MAXIMUM_FAILURE_SUMMARY_LENGTH = 1000;
    private static final int MAXIMUM_PROCESSING_OWNER_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "original_file_name", nullable = false, updatable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private Long fileSizeBytes;

    @Column(name = "source_object_key", nullable = false, updatable = false, unique = true, length = 1024)
    private String sourceObjectKey;

    @Column(name = "rejected_object_key", length = 1024)
    private String rejectedObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionImportStatus status;

    @Column(name = "total_rows")
    private Long totalRows;

    @Column(name = "processed_rows", nullable = false)
    private Long processedRows;

    @Column(name = "successful_rows", nullable = false)
    private Long successfulRows;

    @Column(name = "skipped_rows", nullable = false)
    private Long skippedRows;

    @Column(name = "failed_rows", nullable = false)
    private Long failedRows;

    @Column(name = "failure_summary", length = 1000)
    private String failureSummary;

    @Column(name = "processing_owner", length = 100)
    private String processingOwner;

    @Column(name = "processing_lease_expires_at")
    private Instant processingLeaseExpiresAt;

    @Column(name = "processing_fencing_token", nullable = false)
    private long processingFencingToken;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean hasActiveProcessingLease(Instant now) {
        Objects.requireNonNull(now, "Current time is required");

        return processingOwner != null
                && processingLeaseExpiresAt != null
                && processingLeaseExpiresAt.isAfter(now);
    }

    public long claimProcessingLease(String processingOwner, Instant claimedAt,
                                     Instant leaseExpiresAt) {
        if (status == TransactionImportStatus.COMPLETED
                || status == TransactionImportStatus.ABANDONED) {
            throw new IllegalStateException("A terminal transaction import cannot be claimed");
        }

        Objects.requireNonNull(claimedAt, "Processing lease claim time is required");
        Objects.requireNonNull(leaseExpiresAt, "Processing lease expiration is required");

        String normalizedOwner = normalizeProcessingOwner(processingOwner);

        if (!leaseExpiresAt.isAfter(claimedAt)) {
            throw new IllegalArgumentException("Processing lease expiration must be after the claim time");
        }

        this.processingOwner = normalizedOwner;
        this.processingLeaseExpiresAt = leaseExpiresAt;
        processingFencingToken = Math.incrementExact(processingFencingToken);

        markRunning(claimedAt);

        return processingFencingToken;
    }

    public void markCompleted(long successfulRows, long skippedRows, long failedRows,
                              String rejectedObjectKey) {
        validateRowCounts(successfulRows, skippedRows, failedRows);

        if (status == TransactionImportStatus.COMPLETED) {
            return;
        }

        if (status != TransactionImportStatus.RUNNING) {
            throw new IllegalStateException("Only a running transaction import can be completed");
        }

        if (skippedRows > 0 && (rejectedObjectKey == null || rejectedObjectKey.isBlank())) {
            throw new IllegalArgumentException(
                    "A rejected output object key is required when skipped rows exist"
            );
        }

        if (skippedRows == 0 && rejectedObjectKey != null) {
            throw new IllegalArgumentException(
                    "A rejected output object key cannot exist without skipped rows"
            );
        }

        long finalProcessedRows = successfulRows + skippedRows + failedRows;

        status = TransactionImportStatus.COMPLETED;
        totalRows = finalProcessedRows;
        processedRows = finalProcessedRows;
        this.successfulRows = successfulRows;
        this.skippedRows = skippedRows;
        this.failedRows = failedRows;
        this.rejectedObjectKey = rejectedObjectKey;
        failureSummary = null;
        completedAt = Instant.now();
    }

    public void markFailed(long successfulRows, long skippedRows, long failedRows,
                           String failureSummary) {
        validateRowCounts(successfulRows, skippedRows, failedRows);

        if (status == TransactionImportStatus.COMPLETED
                || status == TransactionImportStatus.ABANDONED) {
            throw new IllegalStateException("A terminal transaction import cannot be failed");
        }

        String normalizedFailureSummary = normalizeFailureSummary(failureSummary);
        long finalProcessedRows = successfulRows + skippedRows + failedRows;

        status = TransactionImportStatus.FAILED;
        totalRows = null;
        processedRows = finalProcessedRows;
        this.successfulRows = successfulRows;
        this.skippedRows = skippedRows;
        this.failedRows = failedRows;
        this.failureSummary = normalizedFailureSummary;
        completedAt = Instant.now();
    }

    public void markAbandoned() {
        if (status != TransactionImportStatus.FAILED) {
            throw new IllegalStateException("Only a failed transaction import can be abandoned");
        }

        status = TransactionImportStatus.ABANDONED;
        processingOwner = null;
        processingLeaseExpiresAt = null;
    }

    private void markRunning(Instant startedAt) {
        if (status == TransactionImportStatus.COMPLETED
                || status == TransactionImportStatus.ABANDONED) {
            throw new IllegalStateException("A terminal transaction import cannot be restarted");
        }

        status = TransactionImportStatus.RUNNING;
        completedAt = null;
        failureSummary = null;

        if (this.startedAt == null) {
            this.startedAt = startedAt;
        }
    }

    private void validateRowCounts(long successfulRows, long skippedRows, long failedRows) {
        if (successfulRows < 0 || skippedRows < 0 || failedRows < 0) {
            throw new IllegalArgumentException("Transaction import row counts cannot be negative");
        }
    }

    private String normalizeProcessingOwner(String processingOwner) {
        if (processingOwner == null || processingOwner.isBlank()) {
            throw new IllegalArgumentException("Processing owner is required");
        }

        String normalizedOwner = processingOwner.trim();

        if (normalizedOwner.length() > MAXIMUM_PROCESSING_OWNER_LENGTH) {
            throw new IllegalArgumentException(
                    "Processing owner cannot exceed " + MAXIMUM_PROCESSING_OWNER_LENGTH + " characters"
            );
        }

        return normalizedOwner;
    }

    private String normalizeFailureSummary(String failureSummary) {
        if (failureSummary == null || failureSummary.isBlank()) {
            throw new IllegalArgumentException("Transaction import failure summary is required");
        }

        String normalizedFailureSummary = failureSummary.trim();

        if (normalizedFailureSummary.length() > MAXIMUM_FAILURE_SUMMARY_LENGTH) {
            return normalizedFailureSummary.substring(0, MAXIMUM_FAILURE_SUMMARY_LENGTH);
        }

        return normalizedFailureSummary;
    }
}
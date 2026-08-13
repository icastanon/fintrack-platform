package com.fintrack.workerservice.transactionimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "transaction_import_rejected_row_staging")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionImportRejectedRowStaging {

    private static final int MAXIMUM_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false, updatable = false)
    private Long importId;

    @Column(name = "row_number", nullable = false, updatable = false)
    private Integer rowNumber;

    @Column(name = "raw_record", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String rawRecord;

    @Column(name = "failure_reason", nullable = false, updatable = false, length = 1000)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TransactionImportRejectedRowStaging create(
            Long importId,
            int rowNumber,
            String rawRecord,
            String failureReason) {
        if (importId == null || importId <= 0) {
            throw new IllegalArgumentException("Import ID must be positive");
        }

        if (rowNumber < 2) {
            throw new IllegalArgumentException("Rejected row number must be at least 2");
        }

        TransactionImportRejectedRowStaging rejectedRow = new TransactionImportRejectedRowStaging();

        rejectedRow.importId = importId;
        rejectedRow.rowNumber = rowNumber;
        rejectedRow.rawRecord = Objects.requireNonNull(rawRecord, "Raw record is required");
        rejectedRow.failureReason = normalizeFailureReason(failureReason);

        return rejectedRow;
    }

    private static String normalizeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("Failure reason is required");
        }

        String normalizedFailureReason = failureReason.trim();

        if (normalizedFailureReason.length() > MAXIMUM_FAILURE_REASON_LENGTH) {
            return normalizedFailureReason.substring(0, MAXIMUM_FAILURE_REASON_LENGTH);
        }

        return normalizedFailureReason;
    }
}
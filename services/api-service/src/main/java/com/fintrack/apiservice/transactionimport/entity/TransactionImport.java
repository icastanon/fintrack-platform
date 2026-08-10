package com.fintrack.apiservice.transactionimport.entity;

import com.fintrack.apiservice.account.entity.FinancialAccount;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "transaction_import")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private FinancialAccount account;

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

    public static TransactionImport createQueued(FinancialAccount account,
                                                 String originalFileName,
                                                 String contentType,
                                                 Long fileSizeBytes,
                                                 String sourceObjectKey) {
        TransactionImport transactionImport = new TransactionImport();

        transactionImport.account = account;
        transactionImport.originalFileName = originalFileName;
        transactionImport.contentType = contentType;
        transactionImport.fileSizeBytes = fileSizeBytes;
        transactionImport.sourceObjectKey = sourceObjectKey;
        transactionImport.status = TransactionImportStatus.QUEUED;
        transactionImport.processedRows = 0L;
        transactionImport.successfulRows = 0L;
        transactionImport.skippedRows = 0L;
        transactionImport.failedRows = 0L;

        return transactionImport;
    }
}
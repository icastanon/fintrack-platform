package com.fintrack.workerservice.transactionimport.repository;

import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransactionImportRejectedRowStagingRepository extends JpaRepository<TransactionImportRejectedRowStaging, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO transaction_import_rejected_row_staging (
                import_id,
                row_number,
                raw_record,
                failure_reason
            )
            VALUES (
                :importId,
                :rowNumber,
                :rawRecord,
                :failureReason
            )
            ON CONFLICT ON CONSTRAINT uq_transaction_import_rejected_row_staging_import_row
            DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("importId") Long importId,
            @Param("rowNumber") int rowNumber,
            @Param("rawRecord") String rawRecord,
            @Param("failureReason") String failureReason
    );

    List<TransactionImportRejectedRowStaging> findAllByImportIdOrderByRowNumberAsc(Long importId);

    long countByImportId(Long importId);

    @Modifying
    @Query(value = """
            DELETE FROM transaction_import_rejected_row_staging
            WHERE import_id = :importId
            """, nativeQuery = true)
    int deleteAllByImportId(@Param("importId") Long importId);

    @Modifying
    @Query(value = """
        DELETE FROM transaction_import_rejected_row_staging AS rejected_row
        USING transaction_import AS ti
        WHERE ti.id = rejected_row.import_id
          AND ti.status = 'COMPLETED'
          AND ti.completed_at < :completedBefore
        """, nativeQuery = true)
    int deleteAllForCompletedImportsBefore(@Param("completedBefore") Instant completedBefore);

    @Modifying
    @Query(value = """
        DELETE FROM transaction_import_rejected_row_staging
        WHERE import_id IN (:importIds)
        """, nativeQuery = true)
    int deleteAllByImportIds(@Param("importIds") List<Long> importIds);
}
package com.fintrack.workerservice.transactionimport.repository;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionImportRepository extends JpaRepository<TransactionImport, Long> {

    @Query(value = """
            SELECT ti.*
            FROM transaction_import ti
            JOIN financial_account fa
              ON fa.id = ti.account_id
            WHERE ti.id = :importId
              AND ti.account_id = :accountId
              AND fa.user_id = :userId
            """, nativeQuery = true)
    Optional<TransactionImport> findByIdAndAccountIdAndUserId(@Param("importId") Long importId,
                                                              @Param("accountId") Long accountId,
                                                              @Param("userId") Long userId);

    @Query(value = """
            SELECT ti.*
            FROM transaction_import ti
            JOIN financial_account fa
              ON fa.id = ti.account_id
            WHERE ti.id = :importId
              AND ti.account_id = :accountId
              AND fa.user_id = :userId
            FOR UPDATE OF ti
            """, nativeQuery = true)
    Optional<TransactionImport> findByIdAndAccountIdAndUserIdForUpdate(@Param("importId") Long importId,
                                                                       @Param("accountId") Long accountId,
                                                                       @Param("userId") Long userId);

    @Query(value = "SELECT clock_timestamp()", nativeQuery = true)
    Instant getCurrentDatabaseTime();

    @Modifying
    @Query(value = """
            UPDATE transaction_import AS ti
            SET processing_lease_expires_at =
                    clock_timestamp()
                    + make_interval(
                        secs => CAST(:leaseDurationSeconds AS DOUBLE PRECISION)
                    ),
                updated_at = clock_timestamp(),
                version = ti.version + 1
            FROM financial_account AS fa
            WHERE ti.id = :importId
              AND ti.account_id = :accountId
              AND fa.id = ti.account_id
              AND fa.user_id = :userId
              AND ti.processing_owner = :processingOwner
              AND ti.processing_fencing_token = :fencingToken
              AND ti.processing_lease_expires_at > clock_timestamp()
            """, nativeQuery = true)
    int renewProcessingLease(@Param("importId") Long importId,
                             @Param("accountId") Long accountId,
                             @Param("userId") Long userId,
                             @Param("processingOwner") String processingOwner,
                             @Param("fencingToken") long fencingToken,
                             @Param("leaseDurationSeconds") long leaseDurationSeconds);

    @Modifying
    @Query(value = """
            UPDATE transaction_import AS ti
            SET processing_owner = NULL,
                processing_lease_expires_at = NULL,
                updated_at = clock_timestamp(),
                version = ti.version + 1
            FROM financial_account AS fa
            WHERE ti.id = :importId
              AND ti.account_id = :accountId
              AND fa.id = ti.account_id
              AND fa.user_id = :userId
              AND ti.processing_owner = :processingOwner
              AND ti.processing_fencing_token = :fencingToken
            """, nativeQuery = true)
    int releaseProcessingLease(@Param("importId") Long importId,
                               @Param("accountId") Long accountId,
                               @Param("userId") Long userId,
                               @Param("processingOwner") String processingOwner,
                               @Param("fencingToken") long fencingToken);

    @Query(value = """
        SELECT ti.*
        FROM transaction_import AS ti
        JOIN financial_account AS fa
          ON fa.id = ti.account_id
        WHERE ti.id = :importId
          AND ti.account_id = :accountId
          AND fa.user_id = :userId
          AND ti.processing_owner = :processingOwner
          AND ti.processing_fencing_token = :fencingToken
          AND ti.processing_lease_expires_at > clock_timestamp()
        FOR UPDATE OF ti
        """, nativeQuery = true)
    Optional<TransactionImport> findActiveProcessingLeaseForUpdate(@Param("importId") Long importId,
                                                                   @Param("accountId") Long accountId,
                                                                   @Param("userId") Long userId,
                                                                   @Param("processingOwner") String processingOwner,
                                                                   @Param("fencingToken") long fencingToken);

    @Query(value = """
        SELECT ti.*
        FROM transaction_import AS ti
        WHERE ti.status = 'FAILED'
          AND ti.completed_at < :failedBefore
          AND (
              ti.processing_lease_expires_at IS NULL
              OR ti.processing_lease_expires_at <= clock_timestamp()
          )
        ORDER BY ti.completed_at, ti.id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<TransactionImport> findStaleFailedImportsForUpdate(@Param("failedBefore") Instant failedBefore,
                                                            @Param("batchSize") int batchSize);
}
package com.fintrack.workerservice.idempotency.repository;

import com.fintrack.workerservice.idempotency.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_message (
                event_id,
                consumer_name,
                event_type,
                event_version
            )
            VALUES (
                :eventId,
                :consumerName,
                :eventType,
                :eventVersion
            )
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("consumerName") String consumerName,
            @Param("eventType") String eventType,
            @Param("eventVersion") int eventVersion
    );
}
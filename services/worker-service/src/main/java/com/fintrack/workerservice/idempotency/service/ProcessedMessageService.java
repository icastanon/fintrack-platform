package com.fintrack.workerservice.idempotency.service;

import com.fintrack.workerservice.idempotency.repository.ProcessedMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProcessedMessageService {

    private final ProcessedMessageRepository processedMessageRepository;

    public ProcessedMessageService(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordIfFirst(UUID eventId, String consumerName, String eventType, int eventVersion) {
        int insertedRows = processedMessageRepository.insertIfAbsent(
                eventId,
                consumerName,
                eventType,
                eventVersion
        );

        return insertedRows == 1;
    }
}
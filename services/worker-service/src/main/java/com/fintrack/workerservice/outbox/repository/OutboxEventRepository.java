package com.fintrack.workerservice.outbox.repository;

import com.fintrack.workerservice.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
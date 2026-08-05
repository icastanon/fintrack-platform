package com.fintrack.apiservice.outbox.repository;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
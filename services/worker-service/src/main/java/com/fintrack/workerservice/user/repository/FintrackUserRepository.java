package com.fintrack.workerservice.user.repository;

import com.fintrack.workerservice.user.entity.FintrackUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FintrackUserRepository extends JpaRepository<FintrackUser, Long> {
}
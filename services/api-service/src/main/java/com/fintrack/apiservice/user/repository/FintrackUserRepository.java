package com.fintrack.apiservice.user.repository;

import com.fintrack.apiservice.user.domain.FintrackUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FintrackUserRepository extends JpaRepository<FintrackUser, Long> {
    boolean existsByUsername(String username);

    Optional<FintrackUser> findByUsername(String username);
}
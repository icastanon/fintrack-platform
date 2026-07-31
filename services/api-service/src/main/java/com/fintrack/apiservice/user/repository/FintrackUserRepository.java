package com.fintrack.apiservice.user.repository;

import com.fintrack.apiservice.user.entity.FintrackUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FintrackUserRepository extends JpaRepository<FintrackUser, Long> {
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<FintrackUser> findByEmail(String email);

    Optional<FintrackUser> findByUsername(String username);
}
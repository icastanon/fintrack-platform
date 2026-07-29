package com.fintrack.apiservice.user.repository;

import com.fintrack.apiservice.user.domain.FintrackUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FintrackUserRepository extends JpaRepository<FintrackUser, Long> {

}
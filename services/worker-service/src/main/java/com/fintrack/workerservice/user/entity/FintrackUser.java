package com.fintrack.workerservice.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fintrack_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FintrackUser {

    @Id
    private Long id;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;
}
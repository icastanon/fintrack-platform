package com.fintrack.apiservice.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FintrackUserResponse {

    private Long id;

    private String username;

    private String email;

    private LocalDateTime createdAt;
}
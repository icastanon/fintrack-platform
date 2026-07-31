package com.fintrack.apiservice.user.exception;

public class FintrackUserNotFoundException extends RuntimeException {

    public FintrackUserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }

    public FintrackUserNotFoundException(String username) {
        super("User not found with username: " + username);
    }
}
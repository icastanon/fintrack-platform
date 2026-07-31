package com.fintrack.apiservice.user.exception;

public class EmailAlreadyExistsException extends RuntimeException{
    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email);
    }
}

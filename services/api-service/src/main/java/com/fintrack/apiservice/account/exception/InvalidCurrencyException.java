package com.fintrack.apiservice.account.exception;

public class InvalidCurrencyException extends RuntimeException {

    public InvalidCurrencyException(String currency) {
        super("Unsupported currency code: " + currency);
    }
}
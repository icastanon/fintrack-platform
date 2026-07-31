package com.fintrack.apiservice.account.exception;

public class AccountNameAlreadyExistsException
        extends RuntimeException {

    public AccountNameAlreadyExistsException(String name) {
        super("An account with that name already exists: " + name);
    }
}
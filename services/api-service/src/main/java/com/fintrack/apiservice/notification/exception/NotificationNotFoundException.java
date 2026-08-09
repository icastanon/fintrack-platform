package com.fintrack.apiservice.notification.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Notification was not found");
    }
}
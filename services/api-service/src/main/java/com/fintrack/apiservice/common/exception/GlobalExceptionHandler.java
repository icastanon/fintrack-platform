package com.fintrack.apiservice.common.exception;

import com.fintrack.apiservice.account.exception.*;
import com.fintrack.apiservice.auth.refresh.exception.InvalidRefreshTokenException;
import com.fintrack.apiservice.budget.exception.BudgetAlreadyExistsException;
import com.fintrack.apiservice.budget.exception.BudgetNotFoundException;
import com.fintrack.apiservice.budget.exception.BudgetVersionConflictException;
import com.fintrack.apiservice.category.exception.CategoryNotFoundException;
import com.fintrack.apiservice.common.dto.ErrorResponse;
import com.fintrack.apiservice.notification.exception.NotificationNotFoundException;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.apiservice.transaction.exception.FinancialTransactionVersionConflictException;
import com.fintrack.apiservice.user.exception.EmailAlreadyExistsException;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.exception.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );


        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors
        );


        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    @ExceptionHandler(FintrackUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(FintrackUserNotFoundException ex) {

        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null
        );


        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExists(UsernameAlreadyExistsException ex) {

        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException exception) {

        ErrorResponse error = new ErrorResponse(
                401,
                "Invalid username or password",
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccountNameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAccountNameAlreadyExists(AccountNameAlreadyExistsException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(FinancialAccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFinancialAccountNotFound(FinancialAccountNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FinancialAccountVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleAccountVersionConflict(FinancialAccountVersionConflictException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "The resource was modified by another request. Reload it and try again.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(FinancialAccountClosedException.class)
    public ResponseEntity<ErrorResponse> handleFinancialAccountClosed(FinancialAccountClosedException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(FinancialTransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFinancialTransactionNotFound(FinancialTransactionNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException exception) {
        Map<String, String> errors = new HashMap<>();

        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String errorKey = parameterName == null ? "parameter" : parameterName;

            result.getResolvableErrors().forEach(error -> errors.put(errorKey, error.getDefaultMessage()));
        });

        ErrorResponse response = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation failed", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(CategoryNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage(), Map.of());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FinancialTransactionVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleFinancialTransactionVersionConflict(FinancialTransactionVersionConflictException exception) {
        ErrorResponse response = new ErrorResponse(HttpStatus.CONFLICT.value(), exception.getMessage(), Map.of());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BudgetAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleBudgetAlreadyExists(BudgetAlreadyExistsException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(BudgetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBudgetNotFound(BudgetNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage(), Map.of());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BudgetVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleBudgetVersionConflict(BudgetVersionConflictException exception) {
        ErrorResponse response = new ErrorResponse(HttpStatus.CONFLICT.value(), exception.getMessage(), Map.of());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequestBody(HttpMessageNotReadableException exception) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Malformed request body",
                Map.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        Map<String, String> errors = Map.of(
                exception.getName(),
                "Value has an invalid format"
        );

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request parameter",
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
        Map<String, String> errors = Map.of(
                exception.getParameterName(),
                "Parameter is required"
        );

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Missing request parameter",
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }


}
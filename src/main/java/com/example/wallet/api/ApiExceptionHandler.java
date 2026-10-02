package com.example.wallet.api;

import com.example.wallet.domain.DomainException;
import com.example.wallet.domain.WalletNotFoundException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, String> domain(DomainException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(WalletNotFoundException e) {
        return Map.of("error", e.getMessage());
    }

    /** Hai request cùng Idempotency-Key chạy đồng thời, hoặc xung đột version. */
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(Exception e) {
        return Map.of("error", "Xung đột đồng thời, hãy thử lại với cùng Idempotency-Key");
    }
}

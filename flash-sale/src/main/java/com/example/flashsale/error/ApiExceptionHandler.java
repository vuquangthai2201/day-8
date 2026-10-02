package com.example.flashsale.error;

import java.util.Map;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SoldOutException.class)
    ResponseEntity<Map<String, String>> soldOut(SoldOutException e) {
        return body(HttpStatus.CONFLICT, "SOLD_OUT", e.getMessage());
    }

    @ExceptionHandler(NoActiveHoldException.class)
    ResponseEntity<Map<String, String>> noHold(NoActiveHoldException e) {
        return body(HttpStatus.CONFLICT, "NO_ACTIVE_HOLD", e.getMessage());
    }

    @ExceptionHandler(AlreadyPurchasedException.class)
    ResponseEntity<Map<String, String>> already(AlreadyPurchasedException e) {
        return body(HttpStatus.CONFLICT, "ALREADY_PURCHASED", e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(ProductNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    /** Redis không với tới được: đóng cổng (fail closed) thay vì bán mù, kèm Retry-After. */
    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    ResponseEntity<Map<String, String>> gateDown(Exception e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(Map.of("error", "GATE_UNAVAILABLE", "message", "Hệ thống đang quá tải, thử lại sau"));
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus s, String code, String msg) {
        return ResponseEntity.status(s).body(Map.of("error", code, "message", msg));
    }
}

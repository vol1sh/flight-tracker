package com.tracker.flight.client.exception;

/**
 * Внешний сервис AviationStack не ответил или ответил ошибкой сервера
 * (сетевой сбой, таймаут, HTTP 5xx). Это НЕ означает, что рейс не найден —
 * означает, что мы не смогли проверить его существование.
 */
public class AviationApiUnavailableException extends RuntimeException {
    public AviationApiUnavailableException(String message) {
        super(message);
    }

    public AviationApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

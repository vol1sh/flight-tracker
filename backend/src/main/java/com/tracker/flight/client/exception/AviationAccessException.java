package com.tracker.flight.client.exception;

/**
 * AviationStack вернул HTTP 401/403, либо JSON-объект error с кодом доступа
 * (например, function_access_restricted — функция недоступна на текущем тарифе).
 */
public class AviationAccessException extends RuntimeException {
    public AviationAccessException(String message) {
        super(message);
    }
}

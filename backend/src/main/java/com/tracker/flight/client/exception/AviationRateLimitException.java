package com.tracker.flight.client.exception;

/**
 * AviationStack вернул HTTP 429 — превышена квота запросов тарифа.
 */
public class AviationRateLimitException extends RuntimeException {
    public AviationRateLimitException(String message) {
        super(message);
    }
}

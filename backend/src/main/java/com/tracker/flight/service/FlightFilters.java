package com.tracker.flight.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Shared validation keeps search, subscriptions and analytics on the same flight identity. */
public final class FlightFilters {
    private FlightFilters() {}

    public static String flightNumber(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("(?:[A-Z][A-Z0-9]|[0-9][A-Z]|[A-Z]{3})-?[0-9]{1,4}[A-Z]?")) {
            throw badRequest("Неверный номер рейса: например SU100, SU-100 или ANA995");
        }
        return normalized.replace("-", "");
    }

    public static String airport(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw badRequest("Код аэропорта должен содержать три латинские буквы");
        }
        return normalized;
    }

    public static void period(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw badRequest("Нужен период from <= to, не более 366 дней включительно");
        }
    }

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

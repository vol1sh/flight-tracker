package com.tracker.flight.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AnalyticsResponseDto(
        ScopeDto scope,
        PeriodDto period,
        Long sampleSize,
        Long landedCount,
        Long cancelledCount,
        Long knownArrivalDelayCount,
        Double averageDelayMinutes,
        Double onTimeRate,
        Double cancellationRate,
        Double reliabilityScore,
        boolean dataSufficient,
        String message,
        String dataSource,
        Instant lastUpdatedAt
) {
    public record ScopeDto(String type, String flightIata, String departureIata, String arrivalIata) {}
    public record PeriodDto(LocalDate from, LocalDate to) {}
}
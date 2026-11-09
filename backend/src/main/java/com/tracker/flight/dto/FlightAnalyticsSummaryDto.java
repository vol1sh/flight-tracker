package com.tracker.flight.dto;

import java.time.LocalDate;
import java.time.Instant;

public interface FlightAnalyticsSummaryDto {
    Long getSampleSize();
    Long getLandedCount();
    Long getCancelledCount();
    Long getKnownArrivalDelayCount();
    Double getAverageDelayMinutes();
    Double getOnTimeRate();
    Double getCancellationRate();
    Double getReliabilityScore();
    Instant getLastUpdatedAt();
}
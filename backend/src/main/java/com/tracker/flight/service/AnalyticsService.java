package com.tracker.flight.service;

import com.tracker.flight.dto.AnalyticsResponseDto;
import com.tracker.flight.dto.FlightAnalyticsSummaryDto;
import com.tracker.flight.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AnalyticsService {

    private final FlightRepository flightRepository;

    public AnalyticsService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    public AnalyticsResponseDto getFlightAnalytics(String flightIata, LocalDate from, LocalDate to) {
        FlightAnalyticsSummaryDto summary = flightRepository.getAnalyticsByFlightIata(flightIata, from, to);
        var scope = new AnalyticsResponseDto.ScopeDto("FLIGHT", flightIata, null, null);
        return buildResponse(scope, from, to, summary);
    }

    public AnalyticsResponseDto getRouteAnalytics(String departure, String arrival, LocalDate from, LocalDate to) {
        FlightAnalyticsSummaryDto summary = flightRepository.getAnalyticsByRoute(departure, arrival, from, to);
        var scope = new AnalyticsResponseDto.ScopeDto("ROUTE", null, departure, arrival);
        return buildResponse(scope, from, to, summary);
    }

    private AnalyticsResponseDto buildResponse(
            AnalyticsResponseDto.ScopeDto scope,
            LocalDate from,
            LocalDate to,
            FlightAnalyticsSummaryDto summary
    ) {
        var period = new AnalyticsResponseDto.PeriodDto(from, to);

        if (summary == null || summary.getSampleSize() == null || summary.getSampleSize() == 0) {
            return new AnalyticsResponseDto(
                    scope, period, 0L, 0L, 0L, 0L,
                    null, null, null, null,
                    false, "Недостаточно данных за указанный период", "POSTGRESQL", null
            );
        }

        long landed = summary.getLandedCount() != null ? summary.getLandedCount() : 0L;
        long knownDelay = summary.getKnownArrivalDelayCount() != null ? summary.getKnownArrivalDelayCount() : 0L;
        long sampleSize = summary.getSampleSize();

        boolean isSufficient = (landed >= 10) && (landed > 0 && ((double) knownDelay / landed) >= 0.80);

        Double avgDelay = summary.getAverageDelayMinutes();
        Double cancellationRate = summary.getCancellationRate();
        Double onTimeRate = summary.getOnTimeRate();
        Double reliabilityScore = null;

        if (isSufficient && avgDelay != null && cancellationRate != null) {
            // reliabilityScore = clamp(100 - 0.5*avgDelay - 200*cancellationRate, 0, 100)
            double score = 100.0 - (0.5 * avgDelay) - (200.0 * cancellationRate);
            reliabilityScore = Math.max(0.0, Math.min(100.0, Math.round(score * 100.0) / 100.0));
        }

        return new AnalyticsResponseDto(
                scope,
                period,
                sampleSize,
                landed,
                summary.getCancelledCount(),
                knownDelay,
                isSufficient ? (avgDelay != null ? Math.round(avgDelay * 100.0) / 100.0 : null) : null,
                isSufficient ? (onTimeRate != null ? Math.round(onTimeRate * 10000.0) / 10000.0 : null) : null,
                isSufficient ? (cancellationRate != null ? Math.round(cancellationRate * 10000.0) / 10000.0 : null) : null,
                reliabilityScore,
                isSufficient,
                isSufficient ? null : "Недостаточно завершенных рейсов для формирования точной аналитики",
                "POSTGRESQL",
                summary.getLastUpdatedAt()
        );
    }
}
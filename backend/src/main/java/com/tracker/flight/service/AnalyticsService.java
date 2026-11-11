package com.tracker.flight.service;

import com.tracker.flight.dto.AnalyticsResponseDto;
import com.tracker.flight.dto.FlightAnalyticsSummaryDto;
import com.tracker.flight.dto.FlightExecutionDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.FlightRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final int MIN_COMPLETED = 5;
    private static final double MIN_DELAY_COVERAGE = 0.80;
    private final FlightRepository flights;

    public AnalyticsService(FlightRepository flights) {
        this.flights = flights;
    }

    public AnalyticsResponseDto getFlightAnalytics(String flightIata, LocalDate from, LocalDate to) {
        FlightFilters.period(from, to);
        String number = FlightFilters.flightNumber(flightIata);
        var scope = new AnalyticsResponseDto.ScopeDto("FLIGHT", number, null, null);
        return summary(scope, from, to, flights.getAnalyticsByFlightIata(number, from, to));
    }

    public AnalyticsResponseDto getRouteAnalytics(String departure, String arrival, LocalDate from, LocalDate to) {
        FlightFilters.period(from, to);
        String origin = FlightFilters.airport(departure);
        String destination = FlightFilters.airport(arrival);
        var scope = new AnalyticsResponseDto.ScopeDto("ROUTE", null, origin, destination);
        return summary(scope, from, to, flights.getAnalyticsByRoute(origin, destination, from, to));
    }

    public Page<FlightExecutionDto> getExecutions(String flightIata, String departure, String arrival,
                                                 LocalDate from, LocalDate to, int page, int size) {
        FlightFilters.period(from, to);
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw FlightFilters.badRequest("page: 0..1000000; size: 1..100");
        }
        var pagination = PageRequest.of(page, size, Sort.by(Sort.Order.desc("flightDate"), Sort.Order.desc("id")));
        Page<Flight> executions;
        if (flightIata != null && departure == null && arrival == null) {
            executions = flights.findByFlightIataAndFlightDateBetween(FlightFilters.flightNumber(flightIata), from, to, pagination);
        } else if (flightIata == null && departure != null && arrival != null) {
            executions = flights.findByDepartureIataAndArrivalIataAndFlightDateBetween(
                    FlightFilters.airport(departure), FlightFilters.airport(arrival), from, to, pagination);
        } else {
            throw FlightFilters.badRequest("Укажите flightIata ИЛИ departure + arrival");
        }
        return executions.map(FlightExecutionDto::from);
    }

    private AnalyticsResponseDto summary(AnalyticsResponseDto.ScopeDto scope, LocalDate from, LocalDate to,
                                         FlightAnalyticsSummaryDto aggregate) {
        var period = new AnalyticsResponseDto.PeriodDto(from, to);
        // COUNT returns zero for an empty selection. AVG and division by an empty sample return SQL NULL.
        long total = aggregate.getSampleSize();
        long landed = aggregate.getLandedCount();
        long cancelled = aggregate.getCancelledCount();
        long known = aggregate.getKnownArrivalDelayCount();
        String warning = null;
        if (total == 0) warning = "Нет сохранённых рейсов за указанный период";
        else if (landed + cancelled < MIN_COMPLETED) warning = "Для расчёта нужно не менее 10 завершённых рейсов";
        else if (landed == 0 || (double) known / landed < MIN_DELAY_COVERAGE) {
            warning = "Нужны известные задержки прилёта не менее чем у 80% приземлившихся рейсов";
        }
        if (warning != null) {
            return new AnalyticsResponseDto(scope, period, total, landed, cancelled, known,
                    null, null, null, null, false, warning, "POSTGRESQL", aggregate.getLastUpdatedAt());
        }
        double average = aggregate.getAverageDelayMinutes();
        double cancellation = aggregate.getCancellationRate();
        double score = Math.max(0, Math.min(100, 100 - 0.5 * average - 200 * cancellation));
        return new AnalyticsResponseDto(scope, period, total, landed, cancelled, known,
                round(average, 100), round(aggregate.getOnTimeRate(), 10_000), round(cancellation, 10_000),
                round(score, 100), true, null, "POSTGRESQL", aggregate.getLastUpdatedAt());
    }

    private static double round(double value, int scale) {
        return (double) Math.round(value * scale) / scale;
    }
}

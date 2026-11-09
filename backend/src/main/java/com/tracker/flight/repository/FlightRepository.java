package com.tracker.flight.repository;

import com.tracker.flight.dto.FlightAnalyticsSummaryDto;
import com.tracker.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightIata(String flightIata);

    Optional<Flight> findByFlightIataAndFlightDate(String flightIata, LocalDate flightDate);

    List<Flight> findByFlightIataAndFlightDateOrderByScheduledDepartureTimeAsc(
            String flightIata,
            LocalDate flightDate
    );

    List<Flight> findByStatusNotIn(List<String> statuses);

    // Фоновый опрос (US-2): только незавершённые рейсы в окне дат, чтобы не опрашивать старые рейсы вечно.
    List<Flight> findByStatusNotInAndFlightDateBetween(
            List<String> statuses,
            LocalDate from,
            LocalDate to
    );

    @Query(value = """
        SELECT 
            COUNT(*) as sampleSize,
            SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) as landedCount,
            SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelledCount,
            SUM(CASE WHEN status = 'LANDED' AND (arrival_delay_minutes IS NOT NULL OR (actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL)) THEN 1 ELSE 0 END) as knownArrivalDelayCount,
            AVG(CASE WHEN status = 'LANDED' THEN COALESCE(arrival_delay_minutes, GREATEST(0, EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60)) END) as averageDelayMinutes,
            (CAST(SUM(CASE WHEN status = 'LANDED' AND COALESCE(arrival_delay_minutes, GREATEST(0, EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60)) = 0 THEN 1 ELSE 0 END) AS DOUBLE PRECISION) / 
             NULLIF(SUM(CASE WHEN status = 'LANDED' AND (arrival_delay_minutes IS NOT NULL OR (actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL)) THEN 1 ELSE 0 END), 0)) as onTimeRate,
            (CAST(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS DOUBLE PRECISION) / 
             NULLIF(SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) + SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0)) as cancellationRate,
            MAX(last_synced_at) as lastUpdatedAt
        FROM flights
        WHERE flight_iata = :flightIata
          AND flight_date BETWEEN :fromDate AND :toDate
          AND status IN ('LANDED', 'CANCELLED')
    """, nativeQuery = true)
    FlightAnalyticsSummaryDto getAnalyticsByFlightIata(
            @Param("flightIata") String flightIata,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query(value = """
        SELECT 
            COUNT(*) as sampleSize,
            SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) as landedCount,
            SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelledCount,
            SUM(CASE WHEN status = 'LANDED' AND (arrival_delay_minutes IS NOT NULL OR (actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL)) THEN 1 ELSE 0 END) as knownArrivalDelayCount,
            AVG(CASE WHEN status = 'LANDED' THEN COALESCE(arrival_delay_minutes, GREATEST(0, EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60)) END) as averageDelayMinutes,
            (CAST(SUM(CASE WHEN status = 'LANDED' AND COALESCE(arrival_delay_minutes, GREATEST(0, EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60)) = 0 THEN 1 ELSE 0 END) AS DOUBLE PRECISION) / 
             NULLIF(SUM(CASE WHEN status = 'LANDED' AND (arrival_delay_minutes IS NOT NULL OR (actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL)) THEN 1 ELSE 0 END), 0)) as onTimeRate,
            (CAST(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS DOUBLE PRECISION) / 
             NULLIF(SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) + SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0)) as cancellationRate,
            MAX(last_synced_at) as lastUpdatedAt
        FROM flights
        WHERE departure_iata = :departureIata
          AND arrival_iata = :arrivalIata
          AND flight_date BETWEEN :fromDate AND :toDate
          AND status IN ('LANDED', 'CANCELLED')
    """, nativeQuery = true)
    FlightAnalyticsSummaryDto getAnalyticsByRoute(
            @Param("departureIata") String departureIata,
            @Param("arrivalIata") String arrivalIata,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
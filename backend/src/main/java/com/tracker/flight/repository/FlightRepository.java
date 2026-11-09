package com.tracker.flight.repository;

import com.tracker.flight.dto.FlightAnalyticsSummaryDto;
import com.tracker.flight.entity.Flight;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Flight f where f.flightIata = :iata and f.flightDate = :date")
    Optional<Flight> findForUpdate(@Param("iata") String iata, @Param("date") LocalDate date);

    List<Flight> findByFlightIataAndFlightDateOrderByScheduledDepartureTimeAsc(String flightIata, LocalDate flightDate);
    List<Flight> findByStatusNotIn(List<String> statuses);
    List<Flight> findByStatusNotInAndFlightDateBetween(List<String> statuses, LocalDate from, LocalDate to);

    Page<Flight> findByFlightIataAndFlightDateBetween(String flightIata, LocalDate from, LocalDate to, Pageable pageable);
    Page<Flight> findByDepartureIataAndArrivalIataAndFlightDateBetween(
            String departure, String arrival, LocalDate from, LocalDate to, Pageable pageable);

    // PostgreSQL GREATEST(0, NULL) is 0: guard missing timestamps BEFORE applying it.
    // No join to status logs: each execution contributes once, including to sampleSize.
    String ANALYTICS = """
        SELECT COUNT(*) AS sampleSize,
            COUNT(*) FILTER (WHERE status = 'LANDED') AS landedCount,
            COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelledCount,
            COUNT(arrival_delay) AS knownArrivalDelayCount,
            AVG(arrival_delay) AS averageDelayMinutes,
            CAST(COUNT(*) FILTER (WHERE arrival_delay = 0) AS DOUBLE PRECISION)
                / NULLIF(COUNT(arrival_delay), 0) AS onTimeRate,
            CAST(COUNT(*) FILTER (WHERE status = 'CANCELLED') AS DOUBLE PRECISION)
                / NULLIF(COUNT(*) FILTER (WHERE status IN ('LANDED', 'CANCELLED')), 0) AS cancellationRate,
            MAX(last_synced_at) AS lastUpdatedAt
        FROM (
            SELECT f.*, CASE WHEN status = 'LANDED' THEN
                CASE WHEN arrival_delay_minutes IS NOT NULL THEN GREATEST(0, arrival_delay_minutes)
                     WHEN actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL
                     THEN GREATEST(0, FLOOR(EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60))
                     ELSE NULL END
                ELSE NULL END AS arrival_delay
            FROM flights f
        ) observations
        """;

    @Query(value = ANALYTICS + " WHERE flight_iata = :flightIata AND flight_date BETWEEN :fromDate AND :toDate", nativeQuery = true)
    FlightAnalyticsSummaryDto getAnalyticsByFlightIata(@Param("flightIata") String flightIata,
            @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

    @Query(value = ANALYTICS + " WHERE departure_iata = :departureIata AND arrival_iata = :arrivalIata AND flight_date BETWEEN :fromDate AND :toDate", nativeQuery = true)
    FlightAnalyticsSummaryDto getAnalyticsByRoute(@Param("departureIata") String departure,
            @Param("arrivalIata") String arrival, @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);
}

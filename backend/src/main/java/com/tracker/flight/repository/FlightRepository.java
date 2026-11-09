package com.tracker.flight.repository;

import com.tracker.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

package com.tracker.flight.repository;

import com.tracker.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightIata(String flightIata);

    List<Flight> findByStatusNotIn(Collection<String> statuses);
}

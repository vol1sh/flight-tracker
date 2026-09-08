package com.tracker.flight.repository;

import com.tracker.flight.entity.FlightStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlightStatusLogRepository extends JpaRepository<FlightStatusLog, Long> {
    List<FlightStatusLog> findByFlightIdOrderByRecordedAtDesc(Long flightId);
}

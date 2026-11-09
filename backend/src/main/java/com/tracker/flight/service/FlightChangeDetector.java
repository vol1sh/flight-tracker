package com.tracker.flight.service;

import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import java.time.Instant;
import java.util.Optional;

/** Compare the saved state with the result of a verified observation. */
final class FlightChangeDetector {
    private FlightChangeDetector() {}

    record Snapshot(String status, Integer delay, Integer departureDelay, Integer arrivalDelay,
                    Instant estimatedArrival) {}

    static Snapshot capture(Flight flight) {
        return new Snapshot(flight.getStatus(), flight.getDelayMinutes(), flight.getDepartureDelayMinutes(),
                flight.getArrivalDelayMinutes(), flight.getEstimatedArrival());
    }

    static Optional<FlightStatusLog> detect(Snapshot previous, Flight current) {
        if (previous.equals(capture(current))) return Optional.empty();
        return Optional.of(historyEntry(current, previous.status()));
    }

    static FlightStatusLog initialState(Flight flight) {
        return historyEntry(flight, null);
    }

    private static FlightStatusLog historyEntry(Flight flight, String previousStatus) {
        FlightStatusLog entry = FlightStatusLog.builder()
                .flight(flight).previousStatus(previousStatus).newStatus(flight.getStatus())
                .delayMinutes(flight.getDelayMinutes()).build();
        entry.setDepartureDelayMinutes(flight.getDepartureDelayMinutes());
        entry.setArrivalDelayMinutes(flight.getArrivalDelayMinutes());
        entry.setEstimatedArrival(flight.getEstimatedArrival());
        return entry;
    }
}

package com.tracker.flight.dto;

import com.tracker.flight.entity.Flight;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

public record FlightExecutionDto(
        String flightIata, LocalDate flightDate, String departureIata, String arrivalIata,
        String status, Integer arrivalDelayMinutes, Instant scheduledArrivalTime,
        Instant actualArrivalTime, Instant lastSyncedAt
) {
    public static FlightExecutionDto from(Flight flight) {
        Integer delay = flight.getArrivalDelayMinutes();
        if (delay != null) {
            delay = Math.max(0, delay);
        } else if ("LANDED".equals(flight.getStatus()) && flight.getActualArrivalTime() != null
                && flight.getScheduledArrivalTime() != null) {
            delay = (int) Math.max(0, Duration.between(flight.getScheduledArrivalTime(),
                    flight.getActualArrivalTime()).toMinutes());
        }
        return new FlightExecutionDto(flight.getFlightIata(), flight.getFlightDate(),
                flight.getDepartureIata(), flight.getArrivalIata(), flight.getStatus(), delay,
                flight.getScheduledArrivalTime(), flight.getActualArrivalTime(), flight.getLastSyncedAt());
    }
}

package com.tracker.flight.dto;

import java.io.Serializable;
import java.time.Instant;

public class ExternalFlightDto implements Serializable {

    private String flightIata;
    private String departureAirport;
    private String arrivalAirport;
    private Instant scheduledDeparture;
    private Instant actualDeparture;
    private Instant scheduledArrival;
    private Instant actualArrival;
    private String status;
    private Integer delayMinutes;
    private boolean isFallbackData;

    public ExternalFlightDto() {}

    public ExternalFlightDto(String flightIata, String departureAirport, String arrivalAirport,
                             Instant scheduledDeparture, Instant actualDeparture,
                             Instant scheduledArrival, Instant actualArrival,
                             String status, Integer delayMinutes, boolean isFallbackData) {
        this.flightIata = flightIata;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.scheduledDeparture = scheduledDeparture;
        this.actualDeparture = actualDeparture;
        this.scheduledArrival = scheduledArrival;
        this.actualArrival = actualArrival;
        this.status = status;
        this.delayMinutes = delayMinutes;
        this.isFallbackData = isFallbackData;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String flightIata;
        private String departureAirport;
        private String arrivalAirport;
        private Instant scheduledDeparture;
        private Instant actualDeparture;
        private Instant scheduledArrival;
        private Instant actualArrival;
        private String status;
        private Integer delayMinutes = 0;
        private boolean isFallbackData = false;

        public Builder flightIata(String flightIata) { this.flightIata = flightIata; return this; }
        public Builder departureAirport(String departureAirport) { this.departureAirport = departureAirport; return this; }
        public Builder arrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; return this; }
        public Builder scheduledDeparture(Instant scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; return this; }
        public Builder actualDeparture(Instant actualDeparture) { this.actualDeparture = actualDeparture; return this; }
        public Builder scheduledArrival(Instant scheduledArrival) { this.scheduledArrival = scheduledArrival; return this; }
        public Builder actualArrival(Instant actualArrival) { this.actualArrival = actualArrival; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder delayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; return this; }
        public Builder isFallbackData(boolean isFallbackData) { this.isFallbackData = isFallbackData; return this; }

        public ExternalFlightDto build() {
            return new ExternalFlightDto(flightIata, departureAirport, arrivalAirport,
                    scheduledDeparture, actualDeparture, scheduledArrival, actualArrival,
                    status, delayMinutes, isFallbackData);
        }
    }

    public String getFlightIata() { return flightIata; }
    public void setFlightIata(String flightIata) { this.flightIata = flightIata; }

    public String getDepartureAirport() { return departureAirport; }
    public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }

    public String getArrivalAirport() { return arrivalAirport; }
    public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }

    public Instant getScheduledDeparture() { return scheduledDeparture; }
    public void setScheduledDeparture(Instant scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; }

    public Instant getActualDeparture() { return actualDeparture; }
    public void setActualDeparture(Instant actualDeparture) { this.actualDeparture = actualDeparture; }

    public Instant getScheduledArrival() { return scheduledArrival; }
    public void setScheduledArrival(Instant scheduledArrival) { this.scheduledArrival = scheduledArrival; }

    public Instant getActualArrival() { return actualArrival; }
    public void setActualArrival(Instant actualArrival) { this.actualArrival = actualArrival; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; }

    public boolean isFallbackData() { return isFallbackData; }
    public void setFallbackData(boolean fallbackData) { isFallbackData = fallbackData; }
}

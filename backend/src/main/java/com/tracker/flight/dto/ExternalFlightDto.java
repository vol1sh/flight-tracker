package com.tracker.flight.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalFlightDto implements Serializable {
    private String flightIata;
    private String departureAirport;
    private String departureCity;
    private String departureAirportName;
    private String arrivalAirport;
    private String arrivalCity;
    private String arrivalAirportName;
    private Instant scheduledDeparture;
    private Instant actualDeparture;
    private Instant scheduledArrival;
    private Instant actualArrival;
    private String status;
    private Integer delayMinutes;

    @JsonProperty("fallbackData")
    private boolean fallbackData;

    public ExternalFlightDto() {}

    public ExternalFlightDto(String flightIata, String departureAirport, String departureCity, String departureAirportName,
                             String arrivalAirport, String arrivalCity, String arrivalAirportName,
                             Instant scheduledDeparture, Instant actualDeparture, Instant scheduledArrival,
                             Instant actualArrival, String status, Integer delayMinutes, boolean fallbackData) {
        this.flightIata = flightIata;
        this.departureAirport = departureAirport;
        this.departureCity = departureCity;
        this.departureAirportName = departureAirportName;
        this.arrivalAirport = arrivalAirport;
        this.arrivalCity = arrivalCity;
        this.arrivalAirportName = arrivalAirportName;
        this.scheduledDeparture = scheduledDeparture;
        this.actualDeparture = actualDeparture;
        this.scheduledArrival = scheduledArrival;
        this.actualArrival = actualArrival;
        this.status = status;
        this.delayMinutes = delayMinutes;
        this.fallbackData = fallbackData;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String flightIata;
        private String departureAirport;
        private String departureCity;
        private String departureAirportName;
        private String arrivalAirport;
        private String arrivalCity;
        private String arrivalAirportName;
        private Instant scheduledDeparture;
        private Instant actualDeparture;
        private Instant scheduledArrival;
        private Instant actualArrival;
        private String status = "SCHEDULED";
        private Integer delayMinutes = 0;
        private boolean fallbackData = false;

        public Builder flightIata(String flightIata) { this.flightIata = flightIata; return this; }
        public Builder departureAirport(String departureAirport) { this.departureAirport = departureAirport; return this; }
        public Builder departureCity(String departureCity) { this.departureCity = departureCity; return this; }
        public Builder departureAirportName(String departureAirportName) { this.departureAirportName = departureAirportName; return this; }
        public Builder arrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; return this; }
        public Builder arrivalCity(String arrivalCity) { this.arrivalCity = arrivalCity; return this; }
        public Builder arrivalAirportName(String arrivalAirportName) { this.arrivalAirportName = arrivalAirportName; return this; }
        public Builder scheduledDeparture(Instant scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; return this; }
        public Builder actualDeparture(Instant actualDeparture) { this.actualDeparture = actualDeparture; return this; }
        public Builder scheduledArrival(Instant scheduledArrival) { this.scheduledArrival = scheduledArrival; return this; }
        public Builder actualArrival(Instant actualArrival) { this.actualArrival = actualArrival; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder delayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; return this; }

        public Builder fallbackData(boolean fallbackData) {
            this.fallbackData = fallbackData;
            return this;
        }

        public Builder isFallbackData(boolean isFallbackData) {
            this.fallbackData = isFallbackData;
            return this;
        }

        public ExternalFlightDto build() {
            return new ExternalFlightDto(flightIata, departureAirport, departureCity, departureAirportName,
                    arrivalAirport, arrivalCity, arrivalAirportName,
                    scheduledDeparture, actualDeparture, scheduledArrival, actualArrival,
                    status, delayMinutes, fallbackData);
        }
    }

    public String getFlightIata() { return flightIata; }
    public void setFlightIata(String flightIata) { this.flightIata = flightIata; }

    public String getDepartureAirport() { return departureAirport; }
    public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }

    public String getDepartureCity() { return departureCity; }
    public void setDepartureCity(String departureCity) { this.departureCity = departureCity; }

    public String getDepartureAirportName() { return departureAirportName; }
    public void setDepartureAirportName(String departureAirportName) { this.departureAirportName = departureAirportName; }

    public String getArrivalAirport() { return arrivalAirport; }
    public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }

    public String getArrivalCity() { return arrivalCity; }
    public void setArrivalCity(String arrivalCity) { this.arrivalCity = arrivalCity; }

    public String getArrivalAirportName() { return arrivalAirportName; }
    public void setArrivalAirportName(String arrivalAirportName) { this.arrivalAirportName = arrivalAirportName; }

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

    public boolean isFallbackData() { return fallbackData; }
    public boolean getFallbackData() { return fallbackData; }
    public void setFallbackData(boolean fallbackData) { this.fallbackData = fallbackData; }
}

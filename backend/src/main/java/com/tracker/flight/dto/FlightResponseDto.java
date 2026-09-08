package com.tracker.flight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Консолидированная информация о рейсе и текущей задержке")
public class FlightResponseDto {

    @Schema(description = "IATA-код рейса", example = "SU-100")
    private String flightIata;

    @Schema(description = "Аэропорт вылета (IATA)", example = "SVO")
    private String departureAirport;

    @Schema(description = "Аэропорт прилета (IATA)", example = "LED")
    private String arrivalAirport;

    @Schema(description = "Плановое время вылета (UTC)")
    private Instant scheduledDeparture;

    @Schema(description = "Фактическое время вылета (UTC)")
    private Instant actualDeparture;

    @Schema(description = "Плановое время прилета (UTC)")
    private Instant scheduledArrival;

    @Schema(description = "Фактическое время прилета (UTC)")
    private Instant actualArrival;

    @Schema(description = "Текущий статус рейса", example = "SCHEDULED")
    private String status;

    @Schema(description = "Расчетная задержка рейса в минутах", example = "25")
    private Integer delayMinutes;

    @Schema(description = "Флаг аварийного режима (Graceful Degradation Circuit Breaker)", example = "false")
    private boolean isDegraded;

    @Schema(description = "Время последней синхронизации записи")
    private Instant lastUpdated;

    public FlightResponseDto() {}

    public FlightResponseDto(String flightIata, String departureAirport, String arrivalAirport,
                             Instant scheduledDeparture, Instant actualDeparture,
                             Instant scheduledArrival, Instant actualArrival,
                             String status, Integer delayMinutes, boolean isDegraded, Instant lastUpdated) {
        this.flightIata = flightIata;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.scheduledDeparture = scheduledDeparture;
        this.actualDeparture = actualDeparture;
        this.scheduledArrival = scheduledArrival;
        this.actualArrival = actualArrival;
        this.status = status;
        this.delayMinutes = delayMinutes;
        this.isDegraded = isDegraded;
        this.lastUpdated = lastUpdated;
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
        private boolean isDegraded = false;
        private Instant lastUpdated;

        public Builder flightIata(String flightIata) { this.flightIata = flightIata; return this; }
        public Builder departureAirport(String departureAirport) { this.departureAirport = departureAirport; return this; }
        public Builder arrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; return this; }
        public Builder scheduledDeparture(Instant scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; return this; }
        public Builder actualDeparture(Instant actualDeparture) { this.actualDeparture = actualDeparture; return this; }
        public Builder scheduledArrival(Instant scheduledArrival) { this.scheduledArrival = scheduledArrival; return this; }
        public Builder actualArrival(Instant actualArrival) { this.actualArrival = actualArrival; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder delayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; return this; }
        public Builder isDegraded(boolean isDegraded) { this.isDegraded = isDegraded; return this; }
        public Builder lastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; return this; }

        public FlightResponseDto build() {
            return new FlightResponseDto(flightIata, departureAirport, arrivalAirport,
                    scheduledDeparture, actualDeparture, scheduledArrival, actualArrival,
                    status, delayMinutes, isDegraded, lastUpdated);
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

    public boolean isDegraded() { return isDegraded; }
    public void setDegraded(boolean degraded) { isDegraded = degraded; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}

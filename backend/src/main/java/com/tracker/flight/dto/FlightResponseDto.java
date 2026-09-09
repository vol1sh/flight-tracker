package com.tracker.flight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Консолидированная информация о рейсе и текущей задержке")
public class FlightResponseDto {

    @Schema(description = "IATA-код рейса", example = "SU-100")
    private String flightIata;

    @Schema(description = "IATA-код аэропорта вылета", example = "SVO")
    private String departureAirport;

    @Schema(description = "Город вылета", example = "Москва")
    private String departureCity;

    @Schema(description = "Наименование аэропорта вылета", example = "Шереметьево")
    private String departureAirportName;

    @Schema(description = "IATA-код аэропорта прилета", example = "DXB")
    private String arrivalAirport;

    @Schema(description = "Город прилета", example = "Дубай")
    private String arrivalCity;

    @Schema(description = "Наименование аэропорта прилета", example = "Международный аэропорт Дубай")
    private String arrivalAirportName;

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

    public FlightResponseDto(String flightIata, String departureAirport, String departureCity, String departureAirportName,
                             String arrivalAirport, String arrivalCity, String arrivalAirportName,
                             Instant scheduledDeparture, Instant actualDeparture,
                             Instant scheduledArrival, Instant actualArrival,
                             String status, Integer delayMinutes, boolean isDegraded, Instant lastUpdated) {
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
        this.isDegraded = isDegraded;
        this.lastUpdated = lastUpdated;
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
        private String status;
        private Integer delayMinutes = 0;
        private boolean isDegraded = false;
        private Instant lastUpdated;

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
        public Builder isDegraded(boolean isDegraded) { this.isDegraded = isDegraded; return this; }
        public Builder lastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; return this; }

        public FlightResponseDto build() {
            return new FlightResponseDto(flightIata, departureAirport, departureCity, departureAirportName,
                    arrivalAirport, arrivalCity, arrivalAirportName,
                    scheduledDeparture, actualDeparture, scheduledArrival, actualArrival,
                    status, delayMinutes, isDegraded, lastUpdated);
        }
    }

    public String getFlightIata() { return flightIata; }
    public String getDepartureAirport() { return departureAirport; }
    public String getDepartureCity() { return departureCity; }
    public String getDepartureAirportName() { return departureAirportName; }
    public String getArrivalAirport() { return arrivalAirport; }
    public String getArrivalCity() { return arrivalCity; }
    public String getArrivalAirportName() { return arrivalAirportName; }
    public Instant getScheduledDeparture() { return scheduledDeparture; }
    public Instant getActualDeparture() { return actualDeparture; }
    public Instant getScheduledArrival() { return scheduledArrival; }
    public Instant getActualArrival() { return actualArrival; }
    public String getStatus() { return status; }
    public Integer getDelayMinutes() { return delayMinutes; }
    public boolean isDegraded() { return isDegraded; }
    public Instant getLastUpdated() { return lastUpdated; }
}

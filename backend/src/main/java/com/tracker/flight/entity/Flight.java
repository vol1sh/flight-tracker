package com.tracker.flight.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "flights", indexes = {
        @Index(name = "idx_flights_iata", columnList = "flight_iata"),
        @Index(name = "idx_flights_departure", columnList = "departure_iata, scheduled_departure_time")
})
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_iata", nullable = false, length = 10)
    private String flightIata;

    @Column(name = "departure_iata", nullable = false, length = 10)
    private String departureIata;

    @Column(name = "departure_city", length = 100)
    private String departureCity;

    @Column(name = "departure_airport_name", length = 150)
    private String departureAirportName;

    @Column(name = "arrival_iata", nullable = false, length = 10)
    private String arrivalIata;

    @Column(name = "arrival_city", length = 100)
    private String arrivalCity;

    @Column(name = "arrival_airport_name", length = 150)
    private String arrivalAirportName;

    @Column(name = "scheduled_departure_time", nullable = false)
    private Instant scheduledDepartureTime;

    @Column(name = "actual_departure_time")
    private Instant actualDepartureTime;

    @Column(name = "scheduled_arrival_time", nullable = false)
    private Instant scheduledArrivalTime;

    @Column(name = "actual_arrival_time")
    private Instant actualArrivalTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Flight() {}

    public Flight(Long id, String flightIata, String departureIata, String departureCity, String departureAirportName,
                  String arrivalIata, String arrivalCity, String arrivalAirportName,
                  Instant scheduledDepartureTime, Instant actualDepartureTime,
                  Instant scheduledArrivalTime, Instant actualArrivalTime,
                  String status, Integer delayMinutes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.flightIata = flightIata;
        this.departureIata = departureIata;
        this.departureCity = departureCity;
        this.departureAirportName = departureAirportName;
        this.arrivalIata = arrivalIata;
        this.arrivalCity = arrivalCity;
        this.arrivalAirportName = arrivalAirportName;
        this.scheduledDepartureTime = scheduledDepartureTime;
        this.actualDepartureTime = actualDepartureTime;
        this.scheduledArrivalTime = scheduledArrivalTime;
        this.actualArrivalTime = actualArrivalTime;
        this.status = status;
        this.delayMinutes = delayMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String flightIata;
        private String departureIata;
        private String departureCity;
        private String departureAirportName;
        private String arrivalIata;
        private String arrivalCity;
        private String arrivalAirportName;
        private Instant scheduledDepartureTime;
        private Instant actualDepartureTime;
        private Instant scheduledArrivalTime;
        private Instant actualArrivalTime;
        private String status = "SCHEDULED";
        private Integer delayMinutes = 0;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder flightIata(String flightIata) { this.flightIata = flightIata; return this; }
        public Builder departureIata(String departureIata) { this.departureIata = departureIata; return this; }
        public Builder departureCity(String departureCity) { this.departureCity = departureCity; return this; }
        public Builder departureAirportName(String departureAirportName) { this.departureAirportName = departureAirportName; return this; }
        public Builder arrivalIata(String arrivalIata) { this.arrivalIata = arrivalIata; return this; }
        public Builder arrivalCity(String arrivalCity) { this.arrivalCity = arrivalCity; return this; }
        public Builder arrivalAirportName(String arrivalAirportName) { this.arrivalAirportName = arrivalAirportName; return this; }
        public Builder scheduledDepartureTime(Instant scheduledDepartureTime) { this.scheduledDepartureTime = scheduledDepartureTime; return this; }
        public Builder actualDepartureTime(Instant actualDepartureTime) { this.actualDepartureTime = actualDepartureTime; return this; }
        public Builder scheduledArrivalTime(Instant scheduledArrivalTime) { this.scheduledArrivalTime = scheduledArrivalTime; return this; }
        public Builder actualArrivalTime(Instant actualArrivalTime) { this.actualArrivalTime = actualArrivalTime; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder delayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Flight build() {
            return new Flight(id, flightIata, departureIata, departureCity, departureAirportName,
                    arrivalIata, arrivalCity, arrivalAirportName,
                    scheduledDepartureTime, actualDepartureTime, scheduledArrivalTime, actualArrivalTime,
                    status, delayMinutes, createdAt, updatedAt);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFlightIata() { return flightIata; }
    public void setFlightIata(String flightIata) { this.flightIata = flightIata; }
    public String getDepartureIata() { return departureIata; }
    public void setDepartureIata(String departureIata) { this.departureIata = departureIata; }
    public String getDepartureCity() { return departureCity; }
    public void setDepartureCity(String departureCity) { this.departureCity = departureCity; }
    public String getDepartureAirportName() { return departureAirportName; }
    public void setDepartureAirportName(String departureAirportName) { this.departureAirportName = departureAirportName; }
    public String getArrivalIata() { return arrivalIata; }
    public void setArrivalIata(String arrivalIata) { this.arrivalIata = arrivalIata; }
    public String getArrivalCity() { return arrivalCity; }
    public void setArrivalCity(String arrivalCity) { this.arrivalCity = arrivalCity; }
    public String getArrivalAirportName() { return arrivalAirportName; }
    public void setArrivalAirportName(String arrivalAirportName) { this.arrivalAirportName = arrivalAirportName; }
    public Instant getScheduledDepartureTime() { return scheduledDepartureTime; }
    public void setScheduledDepartureTime(Instant scheduledDepartureTime) { this.scheduledDepartureTime = scheduledDepartureTime; }
    public Instant getActualDepartureTime() { return actualDepartureTime; }
    public void setActualDepartureTime(Instant actualDepartureTime) { this.actualDepartureTime = actualDepartureTime; }
    public Instant getScheduledArrivalTime() { return scheduledArrivalTime; }
    public void setScheduledArrivalTime(Instant scheduledArrivalTime) { this.scheduledArrivalTime = scheduledArrivalTime; }
    public Instant getActualArrivalTime() { return actualArrivalTime; }
    public void setActualArrivalTime(Instant actualArrivalTime) { this.actualArrivalTime = actualArrivalTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

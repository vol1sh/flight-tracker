package com.tracker.flight.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "flight_status_logs", indexes = {
        @Index(name = "idx_flight_logs_flight_id", columnList = "flight_id")
})
public class FlightStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "previous_status", length = 20)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private Instant recordedAt;

    public FlightStatusLog() {}

    public FlightStatusLog(Long id, Flight flight, String previousStatus, String newStatus, Integer delayMinutes, Instant recordedAt) {
        this.id = id;
        this.flight = flight;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.delayMinutes = delayMinutes;
        this.recordedAt = recordedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Flight flight;
        private String previousStatus;
        private String newStatus;
        private Integer delayMinutes;
        private Instant recordedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder flight(Flight flight) { this.flight = flight; return this; }
        public Builder previousStatus(String previousStatus) { this.previousStatus = previousStatus; return this; }
        public Builder newStatus(String newStatus) { this.newStatus = newStatus; return this; }
        public Builder delayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; return this; }
        public Builder recordedAt(Instant recordedAt) { this.recordedAt = recordedAt; return this; }

        public FlightStatusLog build() {
            return new FlightStatusLog(id, flight, previousStatus, newStatus, delayMinutes, recordedAt);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Flight getFlight() { return flight; }
    public void setFlight(Flight flight) { this.flight = flight; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public Integer getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}

package com.tracker.flight.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "email_subscriptions", indexes = {
        @Index(name = "idx_subscription_flight", columnList = "flight_iata, flight_date")
})
public class EmailSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flight_iata", nullable = false, length = 12)
    private String flightIata;

    @Column(name = "flight_date", nullable = false)
    private LocalDate flightDate;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "confirmation_token_hash", length = 64)
    private String confirmationTokenHash;

    @Column(name = "unsubscribe_token_hash", length = 64)
    private String unsubscribeTokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "last_requested_at")
    private Instant lastRequestedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public EmailSubscription() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFlightIata() { return flightIata; }
    public void setFlightIata(String flightIata) { this.flightIata = flightIata; }
    public LocalDate getFlightDate() { return flightDate; }
    public void setFlightDate(LocalDate flightDate) { this.flightDate = flightDate; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfirmationTokenHash() { return confirmationTokenHash; }
    public void setConfirmationTokenHash(String confirmationTokenHash) { this.confirmationTokenHash = confirmationTokenHash; }
    public String getUnsubscribeTokenHash() { return unsubscribeTokenHash; }
    public void setUnsubscribeTokenHash(String unsubscribeTokenHash) { this.unsubscribeTokenHash = unsubscribeTokenHash; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
    public Instant getLastRequestedAt() { return lastRequestedAt; }
    public void setLastRequestedAt(Instant lastRequestedAt) { this.lastRequestedAt = lastRequestedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
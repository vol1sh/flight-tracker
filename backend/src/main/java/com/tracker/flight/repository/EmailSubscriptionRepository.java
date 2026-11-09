package com.tracker.flight.repository;

import com.tracker.flight.entity.EmailSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, UUID> {

    Optional<EmailSubscription> findByFlightIataAndFlightDateAndEmailIgnoreCase(
            String flightIata, LocalDate flightDate, String email
    );
}
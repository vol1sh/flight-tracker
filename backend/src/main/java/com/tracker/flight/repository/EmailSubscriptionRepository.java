package com.tracker.flight.repository;

import com.tracker.flight.entity.EmailSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, UUID> {
    // Unsubscribed rows are historical; a later subscription must not match them.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailSubscription> findByFlightIataAndFlightDateAndEmailIgnoreCaseAndStatusNot(
            String flightIata, LocalDate flightDate, String email, String status);

    // One hourly confirmation limit per address, including concurrent requests for different flights.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:email, 0))", nativeQuery = true)
    int lockAddress(@Param("email") String normalizedEmail);

    List<EmailSubscription> findByFlightIataAndFlightDateAndStatus(String flightIata, LocalDate date, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmailSubscription s where s.confirmationTokenHash = :hash")
    Optional<EmailSubscription> findByConfirmationHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmailSubscription s where s.unsubscribeTokenHash = :hash")
    Optional<EmailSubscription> findByUnsubscribeHashForUpdate(@Param("hash") String hash);
}

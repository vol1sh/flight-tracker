package com.tracker.flight.repository;

import com.tracker.flight.entity.EmailNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, UUID> {
    // A worker holds both rows until the result of this single delivery is recorded.
    @Query(value = """
        SELECT n.* FROM email_notifications n JOIN email_subscriptions s ON s.id = n.subscription_id
        WHERE n.status = 'PENDING' AND (n.next_attempt_at IS NULL OR n.next_attempt_at <= :now)
        ORDER BY n.created_at, n.id LIMIT 1 FOR UPDATE OF s, n SKIP LOCKED
        """, nativeQuery = true)
    Optional<EmailNotification> lockNextDue(@Param("now") Instant now);

    @Query("select count(n) from EmailNotification n where lower(n.subscription.email) = :email "
            + "and n.eventType = 'CONFIRMATION' and n.createdAt > :since")
    long countRecentConfirmations(@Param("email") String normalizedEmail, @Param("since") Instant since);

    @Modifying(flushAutomatically = true)
    @Query("update EmailNotification n set n.status = 'CANCELLED', n.nextAttemptAt = null "
            + "where n.subscription.id = :id and n.status = 'PENDING'")
    void cancelPending(@Param("id") UUID subscriptionId);
}

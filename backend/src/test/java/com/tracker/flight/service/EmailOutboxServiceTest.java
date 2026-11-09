package com.tracker.flight.service;

import com.tracker.flight.entity.EmailNotification;
import com.tracker.flight.entity.EmailSubscription;
import com.tracker.flight.repository.EmailNotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOutboxServiceTest {
    private final Instant now = Instant.parse("2026-11-09T12:00:00Z");
    private final EmailNotificationRepository repository = mock(EmailNotificationRepository.class);
    private final EmailSenderService sender = mock(EmailSenderService.class);
    private final EmailOutboxService outbox = new EmailOutboxService(repository, sender, "http://localhost:8080", Clock.fixed(now, ZoneOffset.UTC));

    private EmailNotification mail(String subscriptionStatus, String type) {
        var subscription = new EmailSubscription();
        subscription.setStatus(subscriptionStatus);
        subscription.setEmail("user@example.com");
        subscription.setTokenExpiresAt(now.plusSeconds(60));
        var mail = new EmailNotification();
        mail.setSubscription(subscription);
        mail.setEventType(type);
        mail.setSubject("Flight");
        mail.setBody("Message");
        return mail;
    }

    @Test void smtpFailureBacksOffThenStopsAfterFiveAttempts() {
        var mail = mail("ACTIVE", "FLIGHT_CHANGED");
        when(repository.lockNextDue(now)).thenReturn(Optional.of(mail));
        doThrow(new MailSendException("offline")).when(sender).send(anyString(), anyString(), anyString());
        for (int attempt = 1; attempt <= 5; attempt++) {
            outbox.deliverNext();
            assertThat(mail.getAttempts()).isEqualTo(attempt);
            if (attempt < 5) {
                assertThat(mail.getStatus()).isEqualTo("PENDING");
                assertThat(mail.getNextAttemptAt()).isEqualTo(now.plusSeconds(60L << (attempt - 1)));
            }
        }
        assertThat(mail.getNextAttemptAt()).isNull();
        assertThat(mail.getStatus()).isEqualTo("FAILED");
    }

    @Test void confirmationSecretsAreOnlyInDeliveredMessage() {
        var mail = mail("PENDING", "CONFIRMATION");
        when(repository.lockNextDue(now)).thenReturn(Optional.of(mail));
        outbox.deliverNext();
        var delivered = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("user@example.com"), eq("Flight"), delivered.capture());
        assertThat(delivered.getValue()).contains("/confirm?token=", "/unsubscribe?token=");
        assertThat(mail.getBody()).doesNotContain("token=");
        assertThat(mail.getSubscription().getConfirmationTokenHash()).hasSize(64);
        assertThat(mail.getStatus()).isEqualTo("SENT");
        assertThat(mail.getSentAt()).isEqualTo(now);
    }

    @Test void ineligibleMailIsCancelledWithoutSending() {
        var pending = mail("PENDING", "FLIGHT_CHANGED");
        var unsubscribed = mail("UNSUBSCRIBED", "FLIGHT_CHANGED");
        var expired = mail("PENDING", "CONFIRMATION");
        expired.getSubscription().setTokenExpiresAt(now);
        for (var mail : List.of(pending, unsubscribed, expired)) {
            when(repository.lockNextDue(now)).thenReturn(Optional.of(mail));
            outbox.deliverNext();
            assertThat(mail.getStatus()).isEqualTo("CANCELLED");
        }
        verifyNoInteractions(sender);
    }
}

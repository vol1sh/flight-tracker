package com.tracker.flight.service;

import com.tracker.flight.entity.EmailNotification;
import com.tracker.flight.entity.EmailSubscription;
import com.tracker.flight.repository.EmailNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
public class EmailOutboxService {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);
    private static final int MAX_ATTEMPTS = 5;
    private final EmailNotificationRepository notifications;
    private final EmailSenderService sender;
    private final String linksBase;
    private final Clock clock;

    @Autowired
    public EmailOutboxService(EmailNotificationRepository notifications, EmailSenderService sender,
                              @Value("${app.mail.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this(notifications, sender, publicBaseUrl, Clock.systemUTC());
    }

    EmailOutboxService(EmailNotificationRepository notifications, EmailSenderService sender, String publicBaseUrl, Clock clock) {
        this.notifications = notifications;
        this.sender = sender;
        this.linksBase = publicBaseUrl.replaceAll("/+$", "") + "/api/v1/subscriptions/email/";
        this.clock = clock;
    }

    /** One row per transaction so later deliveries cannot roll back an earlier SENT update. */
    @Transactional
    public boolean deliverNext() {
        Instant now = clock.instant();
        var next = notifications.lockNextDue(now);
        if (next.isEmpty()) return false;
        var mail = next.get();
        var subscription = mail.getSubscription();
        if (!deliverable(mail, subscription, now)) {
            mail.setStatus("CANCELLED");
            mail.setNextAttemptAt(null);
            return true;
        }
        int attempt = mail.getAttempts() + 1;
        mail.setAttempts(attempt);
        try {
            if ("CONFIRMATION".equals(mail.getEventType())) sendConfirmation(mail, subscription);
            else sender.send(subscription.getEmail(), mail.getSubject(), mail.getBody());
            mail.setStatus("SENT");
            mail.setSentAt(clock.instant());
            mail.setNextAttemptAt(null);
        } catch (MailException e) {
            if (attempt >= MAX_ATTEMPTS) {
                mail.setStatus("FAILED");
                mail.setNextAttemptAt(null);
            } else {
                mail.setNextAttemptAt(clock.instant().plusSeconds(60L * (1L << (attempt - 1))));
            }
            log.warn("Email delivery failed: id={}, attempt={}, type={}", mail.getId(), attempt, e.getClass().getSimpleName());
        }
        return true;
    }

    private static boolean deliverable(EmailNotification mail, EmailSubscription subscription, Instant now) {
        if ("CONFIRMATION".equals(mail.getEventType())) {
            return "PENDING".equals(subscription.getStatus()) && subscription.getTokenExpiresAt() != null
                    && subscription.getTokenExpiresAt().isAfter(now);
        }
        return "FLIGHT_CHANGED".equals(mail.getEventType()) && "ACTIVE".equals(subscription.getStatus());
    }

    private void sendConfirmation(EmailNotification mail, EmailSubscription subscription) {
        String confirm = SubscriptionTokens.create();
        String unsubscribe = SubscriptionTokens.create();
        String body = mail.getBody() + "\n" + linksBase + "confirm?token=" + confirm
                + "\nПодтверждение доступно до " + subscription.getTokenExpiresAt() + "."
                + " Если вы не запрашивали подписку, проигнорируйте письмо.\n"
                + "Сохраните ссылку для отписки:\n" + linksBase + "unsubscribe?token=" + unsubscribe;
        sender.send(subscription.getEmail(), mail.getSubject(), body);
        // Only the recipient receives the bearer tokens. Neither URL is stored in the outbox.
        subscription.setConfirmationTokenHash(SubscriptionTokens.hash(confirm));
        subscription.setUnsubscribeTokenHash(SubscriptionTokens.hash(unsubscribe));
    }
}

package com.tracker.flight.service;

import com.tracker.flight.dto.CreateEmailSubscriptionRequest;
import com.tracker.flight.dto.EmailSubscriptionResponse;
import com.tracker.flight.entity.EmailNotification;
import com.tracker.flight.entity.EmailSubscription;
import com.tracker.flight.repository.EmailNotificationRepository;
import com.tracker.flight.repository.EmailSubscriptionRepository;
import com.tracker.flight.repository.FlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class EmailSubscriptionService {
    private static final EmailSubscriptionResponse ACCEPTED = new EmailSubscriptionResponse(
            "Если подписку можно оформить, вы получите письмо для подтверждения", "PENDING");
    private final FlightRepository flights;
    private final EmailSubscriptionRepository subscriptions;
    private final EmailNotificationRepository notifications;

    public EmailSubscriptionService(FlightRepository flights, EmailSubscriptionRepository subscriptions,
                                    EmailNotificationRepository notifications) {
        this.flights = flights;
        this.subscriptions = subscriptions;
        this.notifications = notifications;
    }

    public EmailSubscriptionResponse create(CreateEmailSubscriptionRequest request) {
        String iata = FlightFilters.flightNumber(request.flightIata());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate date = request.flightDate();
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(365))) {
            throw FlightFilters.badRequest("Подписка доступна на сегодня и ближайшие 365 дней");
        }
        var flight = flights.findForUpdate(iata, date)
                .orElseThrow(() -> FlightFilters.badRequest("Сначала найдите рейс на выбранную дату"));
        if ("LANDED".equals(flight.getStatus()) || "CANCELLED".equals(flight.getStatus())) {
            throw FlightFilters.badRequest("Рейс уже завершён");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        subscriptions.lockAddress(email);
        var existing = subscriptions.findByFlightIataAndFlightDateAndEmailIgnoreCaseAndStatusNot(
                iata, date, email, "UNSUBSCRIBED");
        Instant now = Instant.now();
        if (existing.isPresent()) {
            EmailSubscription current = existing.get();
            boolean recent = current.getLastRequestedAt() != null && current.getLastRequestedAt().isAfter(now.minusSeconds(60));
            if ("ACTIVE".equals(current.getStatus()) || recent) return ACCEPTED;
        }
        if (notifications.countRecentConfirmations(email, now.minusSeconds(3600)) >= 5) return ACCEPTED;

        EmailSubscription subscription = existing.orElseGet(EmailSubscription::new);
        if (subscription.getId() != null) notifications.cancelPending(subscription.getId());
        subscription.setFlightIata(iata);
        subscription.setFlightDate(date);
        subscription.setEmail(email);
        subscription.setStatus("PENDING");
        subscription.setLastRequestedAt(now);
        subscription.setTokenExpiresAt(now.plus(24, ChronoUnit.HOURS));
        // A resend invalidates old links. The delivery worker creates the new secret links in memory.
        subscription.setConfirmationTokenHash(null);
        subscription.setUnsubscribeTokenHash(null);
        subscriptions.save(subscription);

        EmailNotification mail = new EmailNotification();
        mail.setSubscription(subscription);
        mail.setEventKey("confirm:" + UUID.randomUUID());
        mail.setEventType("CONFIRMATION");
        mail.setSubject("Подтверждение подписки на " + iata);
        mail.setBody("Подтвердите подписку на рейс " + iata + " за " + date + ".");
        notifications.save(mail);
        return ACCEPTED;
    }

    public EmailSubscriptionResponse confirm(String token) {
        var subscription = subscriptions.findByConfirmationHashForUpdate(SubscriptionTokens.hash(token))
                .orElseThrow(() -> FlightFilters.badRequest("Недействительный или использованный токен"));
        Instant now = Instant.now();
        if (!"PENDING".equals(subscription.getStatus()) || subscription.getTokenExpiresAt() == null
                || !subscription.getTokenExpiresAt().isAfter(now)) {
            throw FlightFilters.badRequest("Срок подтверждения истёк; запросите новое письмо");
        }
        subscription.setStatus("ACTIVE");
        subscription.setConfirmedAt(now);
        subscription.setConfirmationTokenHash(null);
        return new EmailSubscriptionResponse("Подписка подтверждена", "ACTIVE");
    }

    public EmailSubscriptionResponse unsubscribe(String token) {
        var subscription = subscriptions.findByUnsubscribeHashForUpdate(SubscriptionTokens.hash(token))
                .orElseThrow(() -> FlightFilters.badRequest("Недействительный токен отписки"));
        subscription.setStatus("UNSUBSCRIBED");
        subscription.setConfirmationTokenHash(null);
        notifications.cancelPending(subscription.getId());
        return new EmailSubscriptionResponse("Подписка отменена", "UNSUBSCRIBED");
    }
}

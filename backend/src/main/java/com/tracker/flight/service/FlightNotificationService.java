package com.tracker.flight.service;

import com.tracker.flight.entity.EmailNotification;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.EmailNotificationRepository;
import com.tracker.flight.repository.EmailSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class FlightNotificationService {
    private final EmailSubscriptionRepository subscriptions;
    private final EmailNotificationRepository notifications;

    public FlightNotificationService(EmailSubscriptionRepository subscriptions, EmailNotificationRepository notifications) {
        this.subscriptions = subscriptions;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(FlightStatusLog change, FlightChangeDetector.Snapshot previous) {
        var flight = change.getFlight();
        var recipients = subscriptions.findByFlightIataAndFlightDateAndStatus(flight.getFlightIata(), flight.getFlightDate(), "ACTIVE");
        if (recipients.isEmpty()) return;
        StringBuilder message = new StringBuilder("Рейс ").append(flight.getFlightIata()).append(" за ").append(flight.getFlightDate());
        appendChange(message, "Статус", previous.status(), change.getNewStatus());
        appendChange(message, "Задержка, мин", previous.delay(), change.getDelayMinutes());
        appendChange(message, "Задержка вылета, мин", previous.departureDelay(), change.getDepartureDelayMinutes());
        appendChange(message, "Задержка прилёта, мин", previous.arrivalDelay(), change.getArrivalDelayMinutes());
        appendChange(message, "Ожидаемое прибытие (UTC)", previous.estimatedArrival(), change.getEstimatedArrival());
        message.append("\nЧтобы отписаться, используйте ссылку из письма подтверждения.");
        String body = message.toString();
        for (var recipient : recipients) {
            var mail = new EmailNotification();
            mail.setSubscription(recipient);
            mail.setEventKey("flight:" + change.getId());
            mail.setEventType("FLIGHT_CHANGED");
            mail.setSubject("Изменение рейса " + flight.getFlightIata() + ": " + change.getNewStatus());
            mail.setBody(body);
            notifications.save(mail);
        }
    }

    private static void appendChange(StringBuilder message, String label, Object before, Object after) {
        if (Objects.equals(before, after)) return;
        message.append('\n').append(label).append(": ").append(before == null ? "нет данных" : before)
                .append(" → ").append(after == null ? "нет данных" : after);
    }
}

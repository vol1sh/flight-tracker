package com.tracker.flight.scheduler;

import com.tracker.flight.service.EmailOutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxScheduler {
    private final EmailOutboxService outbox;

    public EmailOutboxScheduler(EmailOutboxService outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox-delay-ms:10000}")
    public void sendPending() {
        for (int i = 0; i < 20; i++) {
            if (!outbox.deliverNext()) return;
        }
    }
}

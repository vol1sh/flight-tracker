package com.tracker.flight.scheduler;

import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.service.FlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.scheduler.flight-poller",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class FlightPollerScheduler {

    private static final Logger log = LoggerFactory.getLogger(FlightPollerScheduler.class);
    private static final List<String> TERMINAL_STATUSES = List.of("LANDED", "CANCELLED");

    private final FlightRepository flightRepository;
    private final FlightService flightService;

    public FlightPollerScheduler(FlightRepository flightRepository, FlightService flightService) {
        this.flightRepository = flightRepository;
        this.flightService = flightService;
    }

    @Scheduled(
            fixedDelayString = "${app.scheduler.flight-poller.fixed-delay-ms:86400000}",
            initialDelay = 60000
    )
    public void pollActiveFlights() {
        List<Flight> activeFlights = flightRepository.findByStatusNotIn(TERMINAL_STATUSES);

        if (activeFlights.isEmpty()) {
            log.debug("Фоновый опросник: активных рейсов для отслеживания не найдено.");
            return;
        }

        log.info("Фоновый опросник: старт регулярной синхронизации для {} активных рейсов.", activeFlights.size());

        int successCount = 0;
        int errorCount = 0;

        for (Flight flight : activeFlights) {
            try {
                flightService.syncAndGetFlight(flight.getFlightIata());
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Ошибка при фоновом обновлении статуса рейса {}: {}", flight.getFlightIata(), e.getMessage());
            }
        }

        log.info("Фоновый опросник завершил цикл. Успешно обновлено: {}, ошибок: {}.", successCount, errorCount);
    }
}

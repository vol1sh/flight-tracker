package com.tracker.flight.scheduler;

import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.service.FlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * US-2 (Боб): фоновое обновление отслеживаемых рейсов.
 *
 * Правила расхода квоты AviationStack:
 *  - один запрос на уникальный рейс (номер + дата), а не на подписчика;
 *  - только рейсы с датой в окне [сегодня - N; сегодня + N] (UTC), без завершённых (LANDED/CANCELLED);
 *  - не больше maxFlightsPerCycle рейсов за цикл, первыми — давно не синхронизированные;
 *  - при недоступности поставщика (degraded-ответ или 503) цикл прерывается до следующего запуска;
 *    ошибка одного рейса (404, 400 и т.п.) остальные рейсы не останавливает;
 *  - ответы идут через кэш Redis (3 мин), поэтому опрос чаще 3 минут нового не даёт.
 *
 * Источник рейсов сейчас — незавершённые рейсы из PostgreSQL. Когда появится таблица подписок,
 * {@link #findCandidates(LocalDate)} нужно заменить на «рейсы с активными подписками».
 */
@Component
@ConditionalOnProperty(
        prefix = "app.scheduler.flight-poller",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class FlightPollerScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(FlightPollerScheduler.class);

    static final List<String> TERMINAL_STATUSES =
            List.of("LANDED", "CANCELLED");

    private static final long CACHE_TTL_MS = 180_000;

    private final FlightRepository flightRepository;
    private final FlightService flightService;
    private final int maxFlightsPerCycle;
    private final int dateWindowDays;
    private final Clock clock;

    @Autowired
    public FlightPollerScheduler(
            FlightRepository flightRepository,
            FlightService flightService,
            @Value("${app.scheduler.flight-poller.max-flights-per-cycle:5}") int maxFlightsPerCycle,
            @Value("${app.scheduler.flight-poller.date-window-days:1}") int dateWindowDays,
            @Value("${app.scheduler.flight-poller.fixed-delay-ms:86400000}") long fixedDelayMs
    ) {
        this(flightRepository, flightService, maxFlightsPerCycle, dateWindowDays, Clock.systemUTC());

        if (fixedDelayMs < CACHE_TTL_MS) {
            log.warn(
                    "Фоновый опрос каждые {} мс — чаще TTL кэша (3 мин). Допустимо только с тестовым API "
                            + "(docker-compose.mock.yml): с настоящим AviationStack это расходует квоту тарифа.",
                    fixedDelayMs
            );
        }
    }

    FlightPollerScheduler(
            FlightRepository flightRepository,
            FlightService flightService,
            int maxFlightsPerCycle,
            int dateWindowDays,
            Clock clock
    ) {
        this.flightRepository = flightRepository;
        this.flightService = flightService;
        this.maxFlightsPerCycle = Math.max(0, maxFlightsPerCycle);
        this.dateWindowDays = Math.max(0, dateWindowDays);
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.scheduler.flight-poller.fixed-delay-ms:86400000}",
            initialDelay = 60000
    )
    public void pollActiveFlights() {
        LocalDate today = LocalDate.now(clock);

        List<Flight> candidates = findCandidates(today);

        if (candidates.isEmpty()) {
            log.debug("Фоновый опрос: рейсов для обновления нет.");
            return;
        }

        List<Flight> batch = candidates.stream()
                .filter(flight -> flight.getFlightDate() != null)
                .sorted(Comparator.comparing(
                        Flight::getLastSyncedAt,
                        Comparator.nullsFirst(Comparator.<Instant>naturalOrder())
                ))
                .limit(maxFlightsPerCycle)
                .toList();

        log.info(
                "Фоновый опрос: кандидатов {}, в этом цикле {} (лимит {}).",
                candidates.size(),
                batch.size(),
                maxFlightsPerCycle
        );

        int updated = 0;

        for (Flight flight : batch) {
            try {
                FlightResponseDto response = flightService.syncAndGetFlight(
                        flight.getFlightIata(),
                        flight.getFlightDate()
                );

                if (response != null && response.isDegraded()) {
                    // Поставщик недоступен: это не изменение рейса, и дальше в этом цикле квоту не тратим.
                    log.warn(
                            "Фоновый опрос прерван: AviationStack недоступен (рейс {} на {} — сохранённые данные).",
                            flight.getFlightIata(),
                            flight.getFlightDate()
                    );
                    break;
                }

                updated++;
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 503) {
                    // 503 = поставщик недоступен и сохранённых данных нет: сбой общий, не тратим квоту дальше.
                    log.warn(
                            "Фоновый опрос прерван: AviationStack недоступен (рейс {} на {}).",
                            flight.getFlightIata(),
                            flight.getFlightDate()
                    );
                    break;
                }
                // 404 / 400 — проблема конкретного рейса, остальные обновляем дальше.
                log.warn(
                        "Рейс {} на {} пропущен при фоновом обновлении: HTTP {}",
                        flight.getFlightIata(),
                        flight.getFlightDate(),
                        e.getStatusCode().value()
                );
            } catch (Exception e) {
                log.error(
                        "Ошибка при фоновом обновлении рейса {} на {}: {}",
                        flight.getFlightIata(),
                        flight.getFlightDate(),
                        e.getClass().getSimpleName()
                );
            }
        }

        log.info(
                "Фоновый опрос завершён: обновлено {} из {}.",
                updated,
                batch.size()
        );
    }

    /**
     * Какие рейсы опрашивать. Сейчас — незавершённые рейсы в окне дат.
     * После появления подписок (US-2): уникальные (номер, дата) с активными подписками в том же окне.
     */
    List<Flight> findCandidates(LocalDate today) {
        return flightRepository.findByStatusNotInAndFlightDateBetween(
                TERMINAL_STATUSES,
                today.minusDays(dateWindowDays),
                today.plusDays(dateWindowDays)
        );
    }
}
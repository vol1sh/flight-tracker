package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.client.exception.AviationAccessException;
import com.tracker.flight.client.exception.AviationApiUnavailableException;
import com.tracker.flight.client.exception.AviationRateLimitException;
import com.tracker.flight.dto.ActiveFlightDto;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.dto.FlightStatusLogDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class FlightService {

    private static final Logger log =
            LoggerFactory.getLogger(FlightService.class);

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("LANDED", "CANCELLED");

    private final AviationApiClient aviationApiClient;
    private final FlightRepository flightRepository;
    private final FlightStatusLogRepository logRepository;

    public FlightService(
            AviationApiClient aviationApiClient,
            FlightRepository flightRepository,
            FlightStatusLogRepository logRepository
    ) {
        this.aviationApiClient = aviationApiClient;
        this.flightRepository = flightRepository;
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public List<ActiveFlightDto> getActiveFlights() {
        return aviationApiClient.fetchActiveFlights();
    }

    @Transactional
    public FlightResponseDto syncAndGetFlight(
            String flightIata,
            LocalDate flightDate
    ) {
        String cleanFlight = normalizeAndValidateFlightNumber(flightIata);
        validateFlightDate(flightDate);

        log.info(
                "Запрос статуса рейса {} на дату {}",
                cleanFlight,
                flightDate
        );

        Optional<Flight> existingFlightOpt =
                flightRepository.findByFlightIataAndFlightDate(
                        cleanFlight,
                        flightDate
                );

        // Завершённый рейс возвращаем из БД без обращения к поставщику.
        if (existingFlightOpt.isPresent()) {
            Flight existingFlight = existingFlightOpt.get();

            if (existingFlight.getStatus() != null
                    && TERMINAL_STATUSES.contains(
                    existingFlight.getStatus().toUpperCase(Locale.ROOT)
            )) {
                log.info(
                        "Рейс {}: source=DB, terminalStatus={}",
                        cleanFlight,
                        existingFlight.getStatus()
                );

                return mapToFlightResponseDto(existingFlight, false);
            }
        }

        ExternalFlightDto externalDto;

        try {
            externalDto = aviationApiClient.fetchLiveFlight(
                    cleanFlight,
                    flightDate
            );
        } catch (AviationApiUnavailableException
                 | AviationAccessException
                 | AviationRateLimitException
                 | CallNotPermittedException exception) {
            return returnSavedFlightOr503(cleanFlight, existingFlightOpt);
        }

        boolean isDegradedFallback = externalDto == null
                || externalDto.isFallbackData()
                || "UNKNOWN_DEGRADED".equalsIgnoreCase(
                externalDto.getStatus()
        );

        if (isDegradedFallback) {
            return returnSavedFlightOr503(cleanFlight, existingFlightOpt);
        }

        Flight flight;

        if (existingFlightOpt.isPresent()) {
            flight = existingFlightOpt.get();

            boolean statusChanged =
                    !Objects.equals(flight.getStatus(), externalDto.getStatus());

            boolean delayChanged =
                    !Objects.equals(
                            flight.getDelayMinutes(),
                            externalDto.getDelayMinutes()
                    );

            if (statusChanged || delayChanged) {
                log.info(
                        "Аудит рейса {}: статус '{}' -> '{}', задержка {} -> {} мин",
                        cleanFlight,
                        flight.getStatus(),
                        externalDto.getStatus(),
                        flight.getDelayMinutes(),
                        externalDto.getDelayMinutes()
                );

                FlightStatusLog statusLog = FlightStatusLog.builder()
                        .flight(flight)
                        .previousStatus(flight.getStatus())
                        .newStatus(externalDto.getStatus())
                        .delayMinutes(externalDto.getDelayMinutes())
                        .build();

                logRepository.save(statusLog);
            }

            if (externalDto.getDepartureAirport() != null) {
                flight.setDepartureIata(externalDto.getDepartureAirport());
            }

            if (externalDto.getDepartureCity() != null) {
                flight.setDepartureCity(externalDto.getDepartureCity());
            }

            if (externalDto.getDepartureAirportName() != null) {
                flight.setDepartureAirportName(
                        externalDto.getDepartureAirportName()
                );
            }

            if (externalDto.getArrivalAirport() != null) {
                flight.setArrivalIata(externalDto.getArrivalAirport());
            }

            if (externalDto.getArrivalCity() != null) {
                flight.setArrivalCity(externalDto.getArrivalCity());
            }

            if (externalDto.getArrivalAirportName() != null) {
                flight.setArrivalAirportName(
                        externalDto.getArrivalAirportName()
                );
            }

            flight.setStatus(externalDto.getStatus());
            flight.setDelayMinutes(externalDto.getDelayMinutes());

            if (externalDto.getFetchedAt() != null) {
                flight.setLastSyncedAt(externalDto.getFetchedAt());
            }

            if (externalDto.getScheduledDeparture() != null) {
                flight.setScheduledDepartureTime(
                        externalDto.getScheduledDeparture()
                );
            }

            if (externalDto.getScheduledArrival() != null) {
                flight.setScheduledArrivalTime(
                        externalDto.getScheduledArrival()
                );
            }

            if (externalDto.getActualDeparture() != null) {
                flight.setActualDepartureTime(
                        externalDto.getActualDeparture()
                );
            }

            if (externalDto.getActualArrival() != null) {
                flight.setActualArrivalTime(
                        externalDto.getActualArrival()
                );
            }

            flight = flightRepository.save(flight);
        } else {
            flight = Flight.builder()
                    .flightIata(
                            externalDto.getFlightIata() != null
                                    ? externalDto.getFlightIata()
                                    : cleanFlight
                    )
                    .flightDate(flightDate)
                    .departureIata(
                            externalDto.getDepartureAirport() != null
                                    ? externalDto.getDepartureAirport()
                                    : "—"
                    )
                    .departureCity(externalDto.getDepartureCity())
                    .departureAirportName(
                            externalDto.getDepartureAirportName()
                    )
                    .arrivalIata(
                            externalDto.getArrivalAirport() != null
                                    ? externalDto.getArrivalAirport()
                                    : "—"
                    )
                    .arrivalCity(externalDto.getArrivalCity())
                    .arrivalAirportName(
                            externalDto.getArrivalAirportName()
                    )
                    .scheduledDepartureTime(
                            externalDto.getScheduledDeparture() != null
                                    ? externalDto.getScheduledDeparture()
                                    : Instant.now()
                    )
                    .actualDepartureTime(
                            externalDto.getActualDeparture()
                    )
                    .scheduledArrivalTime(
                            externalDto.getScheduledArrival() != null
                                    ? externalDto.getScheduledArrival()
                                    : Instant.now()
                    )
                    .actualArrivalTime(
                            externalDto.getActualArrival()
                    )
                    .status(
                            externalDto.getStatus() != null
                                    ? externalDto.getStatus()
                                    : "UNKNOWN_DEGRADED"
                    )
                    .delayMinutes(
                            externalDto.getDelayMinutes() != null
                                    ? externalDto.getDelayMinutes()
                                    : 0
                    )
                    .lastSyncedAt(externalDto.getFetchedAt())
                    .build();

            flight = flightRepository.save(flight);

            FlightStatusLog initialLog = FlightStatusLog.builder()
                    .flight(flight)
                    .previousStatus(null)
                    .newStatus(flight.getStatus())
                    .delayMinutes(flight.getDelayMinutes())
                    .build();

            logRepository.save(initialLog);
        }

        log.info(
                "Рейс {}: source=AVIATIONSTACK_OR_REDIS, status={}, degraded=false",
                cleanFlight,
                flight.getStatus()
        );

        return mapToFlightResponseDto(flight, false);
    }

    private String normalizeAndValidateFlightNumber(String flightIata) {
        String cleanFlight = flightIata == null
                ? ""
                : flightIata.trim().toUpperCase(Locale.ROOT);

        if (!cleanFlight.matches(
                "(?:[A-Z][A-Z0-9]|[0-9][A-Z]|[A-Z]{3})-?[0-9]{1,4}[A-Z]?"
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Неверный формат номера рейса: например SU100, SU-100 или ANA995"
            );
        }

        return cleanFlight.replace("-", "");
    }

    private void validateFlightDate(LocalDate flightDate) {
        if (flightDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Дата рейса обязательна"
            );
        }
    }

    private FlightResponseDto returnSavedFlightOr503(
            String flightIata,
            Optional<Flight> savedFlight
    ) {
        if (savedFlight.isPresent()) {
            Flight flight = savedFlight.get();

            boolean hasRealData = flight.getStatus() != null
                    && !flight.getStatus().isBlank()
                    && !"UNKNOWN_DEGRADED".equalsIgnoreCase(
                    flight.getStatus()
            )
                    && flight.getDepartureIata() != null
                    && flight.getDepartureIata().matches("[A-Z]{3}")
                    && flight.getArrivalIata() != null
                    && flight.getArrivalIata().matches("[A-Z]{3}")
                    && flight.getScheduledDepartureTime() != null
                    && flight.getScheduledArrivalTime() != null;

            if (hasRealData) {
                log.warn(
                        "Рейс {}: source=DB_FALLBACK, status={}, degraded=true",
                        flightIata,
                        flight.getStatus()
                );

                return mapToFlightResponseDto(flight, true);
            }
        }

        log.warn(
                "Рейс {}: source=NONE, сохранённых данных недостаточно, status=503",
                flightIata
        );

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Не удалось получить данные рейса, сохранённых реальных данных нет"
        );
    }

    @Transactional(readOnly = true)
    public List<FlightStatusLogDto> getFlightHistory(
            String flightIata,
            LocalDate flightDate
    ) {
        String cleanFlight = normalizeAndValidateFlightNumber(flightIata);
        validateFlightDate(flightDate);

        return flightRepository
                .findByFlightIataAndFlightDate(cleanFlight, flightDate)
                .map(flight -> logRepository
                        .findByFlightIdOrderByRecordedAtDesc(flight.getId())
                        .stream()
                        .map(item -> new FlightStatusLogDto(
                                item.getPreviousStatus(),
                                item.getNewStatus(),
                                item.getDelayMinutes(),
                                item.getRecordedAt()
                        ))
                        .toList())
                .orElse(Collections.emptyList());
    }

    private FlightResponseDto mapToFlightResponseDto(
            Flight flight,
            boolean isDegraded
    ) {
        return FlightResponseDto.builder()
                .flightIata(flight.getFlightIata())
                .departureAirport(flight.getDepartureIata())
                .departureCity(flight.getDepartureCity())
                .departureAirportName(flight.getDepartureAirportName())
                .arrivalAirport(flight.getArrivalIata())
                .arrivalCity(flight.getArrivalCity())
                .arrivalAirportName(flight.getArrivalAirportName())
                .scheduledDeparture(flight.getScheduledDepartureTime())
                .actualDeparture(flight.getActualDepartureTime())
                .scheduledArrival(flight.getScheduledArrivalTime())
                .actualArrival(flight.getActualArrivalTime())
                .status(flight.getStatus())
                .delayMinutes(flight.getDelayMinutes())
                .isDegraded(isDegraded)
                .lastUpdated(
                        flight.getLastSyncedAt() != null
                                ? flight.getLastSyncedAt()
                                : flight.getUpdatedAt()
                )
                .build();
    }
}
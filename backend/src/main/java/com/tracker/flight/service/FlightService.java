package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.ActiveFlightDto;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.dto.FlightStatusLogDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);
    private static final Set<String> TERMINAL_STATUSES = Set.of("LANDED", "CANCELLED");

    private final AviationApiClient aviationApiClient;
    private final FlightRepository flightRepository;
    private final FlightStatusLogRepository logRepository;

    public FlightService(AviationApiClient aviationApiClient,
                         FlightRepository flightRepository,
                         FlightStatusLogRepository logRepository) {
        this.aviationApiClient = aviationApiClient;
        this.flightRepository = flightRepository;
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public List<ActiveFlightDto> getActiveFlights() {
        return aviationApiClient.fetchActiveFlights();
    }

    @Transactional
    public FlightResponseDto syncAndGetFlight(String flightIata) {
        String cleanFlight = flightIata.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        log.info("Запрос статуса рейса {}", cleanFlight);

        Optional<Flight> existingFlightOpt = flightRepository.findByFlightIata(cleanFlight);

        // 1. Оптимизация квоты: если рейс уже приземлился (LANDED) или отменен (CANCELLED),
        // данные отдаются напрямую из PostgreSQL без обращения во внешнее API.
        if (existingFlightOpt.isPresent()) {
            Flight existingFlight = existingFlightOpt.get();
            if (existingFlight.getStatus() != null && TERMINAL_STATUSES.contains(existingFlight.getStatus().toUpperCase())) {
                log.info("Рейс {} находится в терминальном статусе '{}'. Данные возвращены из PostgreSQL без обращения к внешнему API.",
                        cleanFlight, existingFlight.getStatus());
                return mapToFlightResponseDto(existingFlight, false);
            }
        }

        // 2. Если рейса нет в базе или он еще активен — запрашиваем внешнее API
        ExternalFlightDto externalDto = aviationApiClient.fetchLiveFlight(cleanFlight);

        Flight flight;
        boolean isDegradedFallback = externalDto.isFallbackData() || "UNKNOWN_DEGRADED".equalsIgnoreCase(externalDto.getStatus());

        if (existingFlightOpt.isPresent()) {
            flight = existingFlightOpt.get();

            if (!isDegradedFallback) {
                boolean statusChanged = !Objects.equals(flight.getStatus(), externalDto.getStatus());
                boolean delayChanged = !Objects.equals(flight.getDelayMinutes(), externalDto.getDelayMinutes());

                if (statusChanged || delayChanged) {
                    log.info("Аудит рейса {}: статус '{}' -> '{}', задержка {} -> {} мин",
                            cleanFlight, flight.getStatus(), externalDto.getStatus(),
                            flight.getDelayMinutes(), externalDto.getDelayMinutes());

                    FlightStatusLog statusLog = FlightStatusLog.builder()
                            .flight(flight)
                            .previousStatus(flight.getStatus())
                            .newStatus(externalDto.getStatus())
                            .delayMinutes(externalDto.getDelayMinutes())
                            .build();

                    logRepository.save(statusLog);
                }

                flight.setDepartureIata(externalDto.getDepartureAirport());
                flight.setDepartureCity(externalDto.getDepartureCity());
                flight.setDepartureAirportName(externalDto.getDepartureAirportName());
                flight.setArrivalIata(externalDto.getArrivalAirport());
                flight.setArrivalCity(externalDto.getArrivalCity());
                flight.setArrivalAirportName(externalDto.getArrivalAirportName());
                flight.setStatus(externalDto.getStatus());
                flight.setDelayMinutes(externalDto.getDelayMinutes());
                flight.setScheduledDepartureTime(externalDto.getScheduledDeparture());
                flight.setScheduledArrivalTime(externalDto.getScheduledArrival());
                flight.setActualDepartureTime(externalDto.getActualDeparture());
                flight.setActualArrivalTime(externalDto.getActualArrival());
                flight = flightRepository.save(flight);
            }
        } else {
            if (isDegradedFallback || externalDto.getDepartureAirport() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс " + cleanFlight + " не найден в реестре телеметрии");
            }

            flight = Flight.builder()
                    .flightIata(externalDto.getFlightIata())
                    .departureIata(externalDto.getDepartureAirport())
                    .departureCity(externalDto.getDepartureCity())
                    .departureAirportName(externalDto.getDepartureAirportName())
                    .arrivalIata(externalDto.getArrivalAirport())
                    .arrivalCity(externalDto.getArrivalCity())
                    .arrivalAirportName(externalDto.getArrivalAirportName())
                    .scheduledDepartureTime(externalDto.getScheduledDeparture() != null ? externalDto.getScheduledDeparture() : Instant.now())
                    .actualDepartureTime(externalDto.getActualDeparture())
                    .scheduledArrivalTime(externalDto.getScheduledArrival() != null ? externalDto.getScheduledArrival() : Instant.now())
                    .actualArrivalTime(externalDto.getActualArrival())
                    .status(externalDto.getStatus())
                    .delayMinutes(externalDto.getDelayMinutes() != null ? externalDto.getDelayMinutes() : 0)
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

        return mapToFlightResponseDto(flight, isDegradedFallback);
    }

    @Transactional(readOnly = true)
    public List<FlightStatusLogDto> getFlightHistory(String flightIata) {
        String cleanFlight = flightIata.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        return flightRepository.findByFlightIata(cleanFlight)
                .map(flight -> logRepository.findByFlightIdOrderByRecordedAtDesc(flight.getId())
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

    private FlightResponseDto mapToFlightResponseDto(Flight flight, boolean isDegraded) {
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
                .lastUpdated(flight.getUpdatedAt() != null ? flight.getUpdatedAt() : Instant.now())
                .build();
    }
}

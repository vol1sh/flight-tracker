package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.dto.FlightStatusLogDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

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

    /**
     * Получение актуальной информации о рейсе:
     * 1. Обращение к клиенту (Redis кэш 3 мин / Resilience4j Circuit Breaker).
     * 2. Сохранение актуального снимка в PostgreSQL.
     * 3. Автоматический аудит изменений в flight_status_logs при смене статуса или задержки.
     */
    @Transactional
    public FlightResponseDto syncAndGetFlight(String flightIata) {
        log.info("Запрос актуальных данных для рейса {}", flightIata);
        ExternalFlightDto externalDto = aviationApiClient.fetchLiveFlight(flightIata);

        Optional<Flight> existingFlightOpt = flightRepository.findByFlightIata(flightIata);
        Flight flight;

        if (existingFlightOpt.isPresent()) {
            flight = existingFlightOpt.get();

            // Если статус или задержка изменились — пишем запись в журнал аудита
            boolean statusChanged = !Objects.equals(flight.getStatus(), externalDto.getStatus());
            boolean delayChanged = !Objects.equals(flight.getDelayMinutes(), externalDto.getDelayMinutes());

            if (statusChanged || delayChanged) {
                log.info("Обнаружено изменение параметров рейса {}: статус '{}' -> '{}', задержка {} -> {} мин",
                        flightIata, flight.getStatus(), externalDto.getStatus(),
                        flight.getDelayMinutes(), externalDto.getDelayMinutes());

                FlightStatusLog statusLog = FlightStatusLog.builder()
                        .flight(flight)
                        .previousStatus(flight.getStatus())
                        .newStatus(externalDto.getStatus())
                        .delayMinutes(externalDto.getDelayMinutes())
                        .build();

                logRepository.save(statusLog);
            }

            // Обновляем сущность актуальными данными
            flight.setStatus(externalDto.getStatus());
            flight.setDelayMinutes(externalDto.getDelayMinutes());
            flight.setActualDepartureTime(externalDto.getActualDeparture());
            flight.setActualArrivalTime(externalDto.getActualArrival());
            flight = flightRepository.save(flight);
        } else {
            // Первичное добавление рейса в базу данных
            flight = Flight.builder()
                    .flightIata(externalDto.getFlightIata())
                    .departureIata(externalDto.getDepartureAirport())
                    .arrivalIata(externalDto.getArrivalAirport())
                    .scheduledDepartureTime(externalDto.getScheduledDeparture())
                    .actualDepartureTime(externalDto.getActualDeparture())
                    .scheduledArrivalTime(externalDto.getScheduledArrival())
                    .actualArrivalTime(externalDto.getActualArrival())
                    .status(externalDto.getStatus())
                    .delayMinutes(externalDto.getDelayMinutes())
                    .build();

            flight = flightRepository.save(flight);

            // Фиксация первой записи в аудите
            FlightStatusLog initialLog = FlightStatusLog.builder()
                    .flight(flight)
                    .previousStatus(null)
                    .newStatus(flight.getStatus())
                    .delayMinutes(flight.getDelayMinutes())
                    .build();

            logRepository.save(initialLog);
        }

        return FlightResponseDto.builder()
                .flightIata(flight.getFlightIata())
                .departureAirport(flight.getDepartureIata())
                .arrivalAirport(flight.getArrivalIata())
                .scheduledDeparture(flight.getScheduledDepartureTime())
                .actualDeparture(flight.getActualDepartureTime())
                .scheduledArrival(flight.getScheduledArrivalTime())
                .actualArrival(flight.getActualArrivalTime())
                .status(flight.getStatus())
                .delayMinutes(flight.getDelayMinutes())
                .isDegraded(externalDto.isFallbackData())
                .lastUpdated(flight.getUpdatedAt() != null ? flight.getUpdatedAt() : Instant.now())
                .build();
    }

    /**
     * Получение истории изменений статусов и задержек для предиктивного анализа.
     */
    @Transactional(readOnly = true)
    public List<FlightStatusLogDto> getFlightHistory(String flightIata) {
        return flightRepository.findByFlightIata(flightIata)
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
}

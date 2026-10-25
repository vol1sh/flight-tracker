package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import com.tracker.flight.client.exception.AviationApiUnavailableException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;


import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тестирование бизнес-логики FlightService")
class FlightServiceTest {

    @Mock
    private AviationApiClient aviationApiClient;

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightStatusLogRepository logRepository;

    @InjectMocks
    private FlightService flightService;

    @Test
    @DisplayName("Первичное сохранение рейса: создает запись в flights и первый лог аудита")
    void syncAndGetFlight_WhenFlightIsNew_ShouldSaveFlightAndCreateInitialLog() {
        // Arrange
        String flightIata = "SU-100";
        ExternalFlightDto externalDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .departureAirport("SVO")
                .arrivalAirport("LED")
                .scheduledDeparture(Instant.now().plusSeconds(3600))
                .scheduledArrival(Instant.now().plusSeconds(7200))
                .status("SCHEDULED")
                .delayMinutes(0)
                .isFallbackData(false)
                .build();

        Flight savedFlight = Flight.builder()
                .id(1L)
                .flightIata(flightIata)
                .departureIata("SVO")
                .arrivalIata("LED")
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        when(aviationApiClient.fetchLiveFlight(flightIata)).thenReturn(externalDto);
        when(flightRepository.findByFlightIata(flightIata)).thenReturn(Optional.empty());
        when(flightRepository.save(any(Flight.class))).thenReturn(savedFlight);

        // Act
        FlightResponseDto response = flightService.syncAndGetFlight(flightIata);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getFlightIata()).isEqualTo(flightIata);
        assertThat(response.getStatus()).isEqualTo("SCHEDULED");
        assertThat(response.isDegraded()).isFalse();

        verify(flightRepository, times(1)).save(any(Flight.class));

        // Проверяем фиксацию первого лога в таблице аудита
        ArgumentCaptor<FlightStatusLog> logCaptor = ArgumentCaptor.forClass(FlightStatusLog.class);
        verify(logRepository, times(1)).save(logCaptor.capture());

        FlightStatusLog capturedLog = logCaptor.getValue();
        assertThat(capturedLog.getPreviousStatus()).isNull();
        assertThat(capturedLog.getNewStatus()).isEqualTo("SCHEDULED");
        assertThat(capturedLog.getDelayMinutes()).isEqualTo(0);
    }

    @Test
    @DisplayName("Обновление рейса: изменение статуса и задержки фиксируется в flight_status_logs")
    void syncAndGetFlight_WhenStatusAndDelayChanged_ShouldCreateAuditLog() {
        // Arrange
        String flightIata = "SU-100";
        Flight existingFlight = Flight.builder()
                .id(1L)
                .flightIata(flightIata)
                .departureIata("SVO")
                .arrivalIata("LED")
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        ExternalFlightDto externalDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("DELAYED")
                .delayMinutes(45)
                .isFallbackData(false)
                .build();

        when(aviationApiClient.fetchLiveFlight(flightIata)).thenReturn(externalDto);
        when(flightRepository.findByFlightIata(flightIata)).thenReturn(Optional.of(existingFlight));
        when(flightRepository.save(any(Flight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        FlightResponseDto response = flightService.syncAndGetFlight(flightIata);

        // Assert
        assertThat(response.getStatus()).isEqualTo("DELAYED");
        assertThat(response.getDelayMinutes()).isEqualTo(45);

        ArgumentCaptor<FlightStatusLog> logCaptor = ArgumentCaptor.forClass(FlightStatusLog.class);
        verify(logRepository, times(1)).save(logCaptor.capture());

        FlightStatusLog log = logCaptor.getValue();
        assertThat(log.getPreviousStatus()).isEqualTo("SCHEDULED");
        assertThat(log.getNewStatus()).isEqualTo("DELAYED");
        assertThat(log.getDelayMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("При сбое без сохранённых данных возвращается 503")
    void syncAndGetFlight_WhenFallbackActivated_ShouldReturn503WithoutSaving() {
        String flightIata = "SU-500";

        ExternalFlightDto fallbackDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .isFallbackData(true)
                .build();

        when(flightRepository.findByFlightIata(flightIata))
                .thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight(flightIata))
                .thenReturn(fallbackDto);

        assertThatThrownBy(() -> flightService.syncAndGetFlight(flightIata))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value())
                                .isEqualTo(503)
                );

        verify(flightRepository, never()).save(any(Flight.class));
        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("При сбое API возвращаются сохранённые данные без изменения")
    void apiFailure_ShouldReturnSavedFlightWithoutSaving() {
        Instant departure = Instant.parse("2026-10-24T10:00:00Z");
        Instant arrival = Instant.parse("2026-10-24T18:00:00Z");
        Instant lastUpdated = Instant.parse("2026-10-24T09:50:00Z");

        Flight savedFlight = Flight.builder()
                .id(1L)
                .flightIata("SU100")
                .departureIata("SVO")
                .arrivalIata("JFK")
                .scheduledDepartureTime(departure)
                .scheduledArrivalTime(arrival)
                .status("DELAYED")
                .delayMinutes(35)
                .updatedAt(lastUpdated)
                .build();

        when(flightRepository.findByFlightIata("SU100"))
                .thenReturn(Optional.of(savedFlight));

        when(aviationApiClient.fetchLiveFlight("SU100"))
                .thenThrow(new AviationApiUnavailableException("Test timeout"));

        FlightResponseDto response = flightService.syncAndGetFlight("SU100");

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getStatus()).isEqualTo("DELAYED");
        assertThat(response.getDelayMinutes()).isEqualTo(35);
        assertThat(response.getScheduledDeparture()).isEqualTo(departure);
        assertThat(response.getScheduledArrival()).isEqualTo(arrival);
        assertThat(response.getLastUpdated()).isEqualTo(lastUpdated);

        verify(flightRepository, never()).save(any(Flight.class));
        verifyNoInteractions(logRepository);
    }


    @Test
    @DisplayName("Номер рейса очищается от пробелов и переводится в верхний регистр")
    void syncAndGetFlight_ShouldNormalizeFlightNumber() {
        String userInput = "  su100  ";
        String expectedIata = "SU100";

        ExternalFlightDto externalDto = ExternalFlightDto.builder()
                .flightIata(expectedIata)
                .status("SCHEDULED")
                .delayMinutes(0)
                .isFallbackData(false)
                .build();

        Flight savedFlight = Flight.builder()
                .id(1L)
                .flightIata(expectedIata)
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        when(flightRepository.findByFlightIata(expectedIata))
                .thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight(expectedIata))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenReturn(savedFlight);

        FlightResponseDto response =
                flightService.syncAndGetFlight(userInput);

        assertThat(response.getFlightIata())
                .isEqualTo(expectedIata);

        verify(flightRepository)
                .findByFlightIata(expectedIata);

        verify(aviationApiClient)
                .fetchLiveFlight(expectedIata);
    }


    @Test
    @DisplayName("Завершённый рейс возвращается из БД без обращения к API")
    void syncAndGetFlight_WhenLanded_ShouldNotCallExternalApi() {
        String flightIata = "SU100";

        Flight existingFlight = Flight.builder()
                .id(10L)
                .flightIata(flightIata)
                .status("LANDED")
                .delayMinutes(0)
                .build();

        when(flightRepository.findByFlightIata(flightIata))
                .thenReturn(Optional.of(existingFlight));
        FlightResponseDto response =
                flightService.syncAndGetFlight(flightIata);
        assertThat(response.getStatus())
                .isEqualTo("LANDED");

        assertThat(response.isDegraded())
                .isFalse();
        verifyNoInteractions(aviationApiClient);

        verify(flightRepository, never())
                .save(any(Flight.class));
        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("Fallback не перезаписывает сохранённый рейс")
    void syncAndGetFlight_WhenApiFails_ShouldPreserveExistingData() {
        String flightIata = "SU100";


        Flight existingFlight = Flight.builder()
                .id(1L)
                .flightIata(flightIata)
                .departureIata("SVO")
                .arrivalIata("LED")
                .scheduledDepartureTime(
                        Instant.parse("2026-10-25T10:00:00Z")
                )
                .scheduledArrivalTime(
                        Instant.parse("2026-10-25T12:00:00Z")
                )
                .status("DELAYED")
                .delayMinutes(45)
                .build();

        ExternalFlightDto fallbackDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .isFallbackData(true)
                .build();

        when(flightRepository.findByFlightIata(flightIata))
                .thenReturn(Optional.of(existingFlight));

        when(aviationApiClient.fetchLiveFlight(flightIata))
                .thenReturn(fallbackDto);

        FlightResponseDto response =
                flightService.syncAndGetFlight(flightIata);

        assertThat(response.isDegraded())
                .isTrue();

        assertThat(response.getStatus())
                .isEqualTo("DELAYED");

        assertThat(response.getDelayMinutes())
                .isEqualTo(45);

        assertThat(existingFlight.getStatus())
                .isEqualTo("DELAYED");

        assertThat(existingFlight.getDelayMinutes())
                .isEqualTo(45);

        verify(flightRepository, never())
                .save(any(Flight.class));

        verifyNoInteractions(logRepository);
    }

}

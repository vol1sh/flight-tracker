package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @DisplayName("Аварийный режим (Circuit Breaker fallback): сервис отдает ответ с флагом degraded=true")
    void syncAndGetFlight_WhenFallbackActivated_ShouldSetDegradedFlag() {
        // Arrange
        String flightIata = "SU-500";
        ExternalFlightDto fallbackDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .isFallbackData(true)
                .build();

        Flight degradedFlight = Flight.builder()
                .id(2L)
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .build();

        when(aviationApiClient.fetchLiveFlight(flightIata)).thenReturn(fallbackDto);
        when(flightRepository.findByFlightIata(flightIata)).thenReturn(Optional.empty());
        when(flightRepository.save(any(Flight.class))).thenReturn(degradedFlight);

        // Act
        FlightResponseDto response = flightService.syncAndGetFlight(flightIata);

        // Assert
        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getStatus()).isEqualTo("UNKNOWN_DEGRADED");
    }
}

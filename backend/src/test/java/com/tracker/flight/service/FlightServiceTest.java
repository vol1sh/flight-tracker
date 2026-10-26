package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.client.exception.AviationApiUnavailableException;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тестирование бизнес-логики FlightService")
class FlightServiceTest {

    private static final LocalDate FLIGHT_DATE =
            LocalDate.of(2026, 10, 25);

    @Mock
    private AviationApiClient aviationApiClient;

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightStatusLogRepository logRepository;

    @InjectMocks
    private FlightService flightService;

    @Test
    @DisplayName("Одинаковый номер за разные дни имеет разные статусы и историю")
    void sameNumberOnDifferentDates_ShouldNotMixStatusOrHistory() {
        LocalDate otherDate = FLIGHT_DATE.plusDays(1);

        Flight first = Flight.builder()
                .id(1L)
                .flightIata("SU100")
                .flightDate(FLIGHT_DATE)
                .status("LANDED")
                .build();

        Flight second = Flight.builder()
                .id(2L)
                .flightIata("SU100")
                .flightDate(otherDate)
                .status("CANCELLED")
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", FLIGHT_DATE
        )).thenReturn(Optional.of(first));

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", otherDate
        )).thenReturn(Optional.of(second));

        when(logRepository.findByFlightIdOrderByRecordedAtDesc(1L))
                .thenReturn(List.of(
                        FlightStatusLog.builder()
                                .flight(first)
                                .newStatus("LANDED")
                                .delayMinutes(0)
                                .build()
                ));

        when(logRepository.findByFlightIdOrderByRecordedAtDesc(2L))
                .thenReturn(List.of(
                        FlightStatusLog.builder()
                                .flight(second)
                                .newStatus("CANCELLED")
                                .delayMinutes(0)
                                .build()
                ));

        assertThat(flightService
                .syncAndGetFlight("SU100", FLIGHT_DATE)
                .getStatus())
                .isEqualTo("LANDED");

        assertThat(flightService
                .syncAndGetFlight("SU100", otherDate)
                .getStatus())
                .isEqualTo("CANCELLED");

        assertThat(flightService
                .getFlightHistory("SU100", FLIGHT_DATE)
                .get(0)
                .getNewStatus())
                .isEqualTo("LANDED");

        assertThat(flightService
                .getFlightHistory("SU100", otherDate)
                .get(0)
                .getNewStatus())
                .isEqualTo("CANCELLED");

        verify(flightRepository, never())
                .findByFlightIata(anyString());

        verifyNoInteractions(aviationApiClient);
    }

    @Test
    @DisplayName("Время синхронизации берётся из fetchedAt клиента")
    void syncAndGetFlight_ShouldStoreProviderFetchTimeAsLastSyncedAt() {
        Instant fetchedAt =
                Instant.parse("2026-10-25T10:00:00Z");

        ExternalFlightDto externalDto = ExternalFlightDto.builder()
                .flightIata("SU100")
                .departureAirport("SVO")
                .arrivalAirport("LED")
                .scheduledDeparture(
                        Instant.parse("2026-10-25T12:00:00Z")
                )
                .scheduledArrival(
                        Instant.parse("2026-10-25T13:30:00Z")
                )
                .status("SCHEDULED")
                .delayMinutes(0)
                .fetchedAt(fetchedAt)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", FLIGHT_DATE
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight("SU100", FLIGHT_DATE))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", FLIGHT_DATE);

        ArgumentCaptor<Flight> captor =
                ArgumentCaptor.forClass(Flight.class);

        verify(flightRepository).save(captor.capture());

        assertThat(captor.getValue().getLastSyncedAt())
                .isEqualTo(fetchedAt);
        assertThat(response.getFlightDate())
                .isEqualTo(FLIGHT_DATE);

        assertThat(response.getLastSyncedAt())
                .isEqualTo(fetchedAt);

        assertThat(response.isDataFresh())
                .isFalse();
    }

    @Test
    @DisplayName("Fallback возвращает настоящее время последней синхронизации")
    void syncAndGetFlight_WhenProviderUnavailable_LastUpdatedIsRealSyncTime() {
        Instant lastSync =
                Instant.parse("2026-10-25T08:00:00Z");

        Flight saved = Flight.builder()
                .id(7L)
                .flightIata("SU100")
                .flightDate(FLIGHT_DATE)
                .departureIata("SVO")
                .arrivalIata("LED")
                .scheduledDepartureTime(
                        Instant.parse("2026-10-25T12:00:00Z")
                )
                .scheduledArrivalTime(
                        Instant.parse("2026-10-25T13:30:00Z")
                )
                .status("SCHEDULED")
                .delayMinutes(0)
                .lastSyncedAt(lastSync)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", FLIGHT_DATE
        )).thenReturn(Optional.of(saved));

        when(aviationApiClient.fetchLiveFlight("SU100", FLIGHT_DATE))
                .thenThrow(new AviationApiUnavailableException("timeout"));

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", FLIGHT_DATE);

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getLastUpdated()).isEqualTo(lastSync);

        assertThat(response.getFlightDate())
                .isEqualTo(FLIGHT_DATE);

        assertThat(response.getLastSyncedAt())
                .isEqualTo(lastSync);

        assertThat(response.isDataFresh())
                .isFalse();

        assertThat(response.isDegraded())
                .isTrue();

    }

    @Test
    @DisplayName("Новый рейс сохраняется с датой и начальным событием истории")
    void syncAndGetFlight_WhenFlightIsNew_ShouldSaveFlightAndCreateInitialLog() {
        String flightIata = "SU100";

        ExternalFlightDto externalDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .departureAirport("SVO")
                .arrivalAirport("LED")
                .scheduledDeparture(
                        Instant.parse("2026-10-25T10:00:00Z")
                )
                .scheduledArrival(
                        Instant.parse("2026-10-25T12:00:00Z")
                )
                .status("SCHEDULED")
                .delayMinutes(0)
                .isFallbackData(false)
                .build();

        Flight savedFlight = Flight.builder()
                .id(1L)
                .flightIata(flightIata)
                .flightDate(FLIGHT_DATE)
                .departureIata("SVO")
                .arrivalIata("LED")
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                flightIata, FLIGHT_DATE
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight(flightIata, FLIGHT_DATE))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenReturn(savedFlight);

        FlightResponseDto response =
                flightService.syncAndGetFlight(flightIata, FLIGHT_DATE);

        assertThat(response.getFlightIata()).isEqualTo(flightIata);
        assertThat(response.getStatus()).isEqualTo("SCHEDULED");
        assertThat(response.isDegraded()).isFalse();

        ArgumentCaptor<Flight> flightCaptor =
                ArgumentCaptor.forClass(Flight.class);

        verify(flightRepository).save(flightCaptor.capture());

        assertThat(flightCaptor.getValue().getFlightDate())
                .isEqualTo(FLIGHT_DATE);

        ArgumentCaptor<FlightStatusLog> logCaptor =
                ArgumentCaptor.forClass(FlightStatusLog.class);

        verify(logRepository).save(logCaptor.capture());

        FlightStatusLog initialLog = logCaptor.getValue();

        assertThat(initialLog.getPreviousStatus()).isNull();
        assertThat(initialLog.getNewStatus()).isEqualTo("SCHEDULED");
        assertThat(initialLog.getDelayMinutes()).isEqualTo(0);
    }

    @Test
    @DisplayName("Изменение статуса и задержки записывается в историю")
    void syncAndGetFlight_WhenStatusAndDelayChanged_ShouldCreateAuditLog() {
        String flightIata = "SU100";

        Flight existingFlight = Flight.builder()
                .id(1L)
                .flightIata(flightIata)
                .flightDate(FLIGHT_DATE)
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

        when(flightRepository.findByFlightIataAndFlightDate(
                flightIata, FLIGHT_DATE
        )).thenReturn(Optional.of(existingFlight));

        when(aviationApiClient.fetchLiveFlight(flightIata, FLIGHT_DATE))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response =
                flightService.syncAndGetFlight(flightIata, FLIGHT_DATE);

        assertThat(response.getStatus()).isEqualTo("DELAYED");
        assertThat(response.getDelayMinutes()).isEqualTo(45);

        ArgumentCaptor<FlightStatusLog> captor =
                ArgumentCaptor.forClass(FlightStatusLog.class);

        verify(logRepository).save(captor.capture());

        FlightStatusLog statusLog = captor.getValue();

        assertThat(statusLog.getPreviousStatus()).isEqualTo("SCHEDULED");
        assertThat(statusLog.getNewStatus()).isEqualTo("DELAYED");
        assertThat(statusLog.getDelayMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("Fallback без сохранённых данных возвращает 503")
    void syncAndGetFlight_WhenFallbackActivated_ShouldReturn503WithoutSaving() {
        String flightIata = "SU500";

        ExternalFlightDto fallbackDto = ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .isFallbackData(true)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                flightIata, FLIGHT_DATE
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight(flightIata, FLIGHT_DATE))
                .thenReturn(fallbackDto);

        assertThatThrownBy(() ->
                flightService.syncAndGetFlight(flightIata, FLIGHT_DATE)
        ).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value())
                        .isEqualTo(503)
        );

        verify(flightRepository, never()).save(any(Flight.class));
        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("При ошибке API сохранённые данные не изменяются")
    void apiFailure_ShouldReturnSavedFlightWithoutSaving() {
        Instant departure =
                Instant.parse("2026-10-25T10:00:00Z");

        Instant arrival =
                Instant.parse("2026-10-25T18:00:00Z");

        Instant lastUpdated =
                Instant.parse("2026-10-25T09:50:00Z");

        Flight savedFlight = Flight.builder()
                .id(1L)
                .flightIata("SU100")
                .flightDate(FLIGHT_DATE)
                .departureIata("SVO")
                .arrivalIata("JFK")
                .scheduledDepartureTime(departure)
                .scheduledArrivalTime(arrival)
                .status("DELAYED")
                .delayMinutes(35)
                .updatedAt(lastUpdated)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", FLIGHT_DATE
        )).thenReturn(Optional.of(savedFlight));

        when(aviationApiClient.fetchLiveFlight("SU100", FLIGHT_DATE))
                .thenThrow(
                        new AviationApiUnavailableException("Test timeout")
                );

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", FLIGHT_DATE);

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
    @DisplayName("Номер очищается от пробелов и переводится в верхний регистр")
    void syncAndGetFlight_ShouldNormalizeFlightNumber() {
        String userInput = "  su100  ";
        String expectedIata = "SU100";

        ExternalFlightDto externalDto = ExternalFlightDto.builder()
                .flightIata(expectedIata)
                .departureAirport("SVO")
                .arrivalAirport("LED")
                .scheduledDeparture(
                        Instant.parse("2026-10-25T10:00:00Z")
                )
                .scheduledArrival(
                        Instant.parse("2026-10-25T12:00:00Z")
                )
                .status("SCHEDULED")
                .delayMinutes(0)
                .isFallbackData(false)
                .build();

        Flight savedFlight = Flight.builder()
                .id(1L)
                .flightIata(expectedIata)
                .flightDate(FLIGHT_DATE)
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                expectedIata, FLIGHT_DATE
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight(expectedIata, FLIGHT_DATE))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenReturn(savedFlight);

        FlightResponseDto response =
                flightService.syncAndGetFlight(userInput, FLIGHT_DATE);

        assertThat(response.getFlightIata()).isEqualTo(expectedIata);

        verify(flightRepository)
                .findByFlightIataAndFlightDate(expectedIata, FLIGHT_DATE);

        verify(aviationApiClient)
                .fetchLiveFlight(expectedIata, FLIGHT_DATE);
    }

    @Test
    @DisplayName("Завершённый рейс возвращается из БД без обращения к API")
    void syncAndGetFlight_WhenLanded_ShouldNotCallExternalApi() {
        String flightIata = "SU100";

        Flight existingFlight = Flight.builder()
                .id(10L)
                .flightIata(flightIata)
                .flightDate(FLIGHT_DATE)
                .status("LANDED")
                .delayMinutes(0)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                flightIata, FLIGHT_DATE
        )).thenReturn(Optional.of(existingFlight));

        FlightResponseDto response =
                flightService.syncAndGetFlight(flightIata, FLIGHT_DATE);

        assertThat(response.getStatus()).isEqualTo("LANDED");
        assertThat(response.isDegraded()).isFalse();

        verifyNoInteractions(aviationApiClient);
        verify(flightRepository, never()).save(any(Flight.class));
        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("Старый fallback DTO не перезаписывает сохранённый рейс")
    void syncAndGetFlight_WhenApiFails_ShouldPreserveExistingData() {
        String flightIata = "SU100";

        Flight existingFlight = Flight.builder()
                .id(1L)
                .flightIata(flightIata)
                .flightDate(FLIGHT_DATE)
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

        when(flightRepository.findByFlightIataAndFlightDate(
                flightIata, FLIGHT_DATE
        )).thenReturn(Optional.of(existingFlight));

        when(aviationApiClient.fetchLiveFlight(flightIata, FLIGHT_DATE))
                .thenReturn(fallbackDto);

        FlightResponseDto response =
                flightService.syncAndGetFlight(flightIata, FLIGHT_DATE);

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getStatus()).isEqualTo("DELAYED");
        assertThat(response.getDelayMinutes()).isEqualTo(45);
        assertThat(existingFlight.getStatus()).isEqualTo("DELAYED");
        assertThat(existingFlight.getDelayMinutes()).isEqualTo(45);

        verify(flightRepository, never()).save(any(Flight.class));
        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("Свежие данные API отображаются с корректной меткой синхронизации")
    void freshProviderResponse_ShouldSetDataFreshTrue() {

        Instant fetchedAt = Instant.now().minusSeconds(10);

        ExternalFlightDto externalDto =
                ExternalFlightDto.builder()
                        .flightIata("SU100")
                        .departureAirport("SVO")
                        .arrivalAirport("LED")
                        .scheduledDeparture(
                                Instant.parse("2026-10-26T10:00:00Z")
                        )
                        .scheduledArrival(
                                Instant.parse("2026-10-26T12:00:00Z")
                        )
                        .status("SCHEDULED")
                        .delayMinutes(0)
                        .fetchedAt(fetchedAt)
                        .isFallbackData(false)
                        .build();

        LocalDate date = LocalDate.of(2026, 10, 26);

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", date
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight("SU100", date))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", date);

        assertThat(response.getFlightDate())
                .isEqualTo(date);

        assertThat(response.getLastSyncedAt())
                .isEqualTo(fetchedAt);

        assertThat(response.isDataFresh())
                .isTrue();

        assertThat(response.isDegraded())
                .isFalse();
    }


    @Test
    @DisplayName("Старый ответ из API/Redis не считается свежим")
    void oldCachedProviderResponse_ShouldSetDataFreshFalse() {

        Instant fetchedAt =
                Instant.now().minusSeconds(600);

        LocalDate date = LocalDate.of(2026, 10, 26);

        ExternalFlightDto externalDto =
                ExternalFlightDto.builder()
                        .flightIata("SU100")
                        .departureAirport("SVO")
                        .arrivalAirport("LED")
                        .scheduledDeparture(
                                Instant.parse("2026-10-26T10:00:00Z")
                        )
                        .scheduledArrival(
                                Instant.parse("2026-10-26T12:00:00Z")
                        )
                        .status("SCHEDULED")
                        .delayMinutes(0)
                        .fetchedAt(fetchedAt)
                        .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", date
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight("SU100", date))
                .thenReturn(externalDto);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", date);

        assertThat(response.getLastSyncedAt())
                .isEqualTo(fetchedAt);

        assertThat(response.isDataFresh())
                .isFalse();

        assertThat(response.isDegraded())
                .isFalse();
    }

    @Test
    @DisplayName("Неполный ответ API не создаёт рейс с выдуманным расписанием")
    void incompleteProviderResponse_ShouldNotCreateFlight() {

        LocalDate date = LocalDate.of(2026, 10, 26);

        ExternalFlightDto incompleteDto =
                ExternalFlightDto.builder()
                        .flightIata("SU100")
                        .departureAirport("SVO")
                        .arrivalAirport("LED")
                        .scheduledDeparture(null)
                        .scheduledArrival(null)
                        .status("SCHEDULED")
                        .delayMinutes(0)
                        .fetchedAt(Instant.now())
                        .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", date
        )).thenReturn(Optional.empty());

        when(aviationApiClient.fetchLiveFlight("SU100", date))
                .thenReturn(incompleteDto);

        assertThatThrownBy(
                () -> flightService.syncAndGetFlight(
                        "SU100", date
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(
                                exception.getStatusCode().value()
                        ).isEqualTo(503)
                );

        verify(flightRepository, never())
                .save(any(Flight.class));

        verifyNoInteractions(logRepository);
    }
}
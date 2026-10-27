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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServicePartialUpdateTest {

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
    @DisplayName("Частичный ответ API сохраняет известные аэропорты и расписание")
    void partialExternalPayloadPreservesValidStoredData() {
        Instant departure = Instant.parse("2026-10-25T10:00:00Z");
        Instant arrival = Instant.parse("2026-10-25T12:00:00Z");

        Flight existing = Flight.builder()
                .id(7L)
                .flightIata("SU100")
                .flightDate(FLIGHT_DATE)
                .departureIata("SVO")
                .arrivalIata("LED")
                .scheduledDepartureTime(departure)
                .scheduledArrivalTime(arrival)
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        ExternalFlightDto apiResponse = ExternalFlightDto.builder()
                .flightIata("SU100")
                .status("DELAYED")
                .delayMinutes(25)
                .isFallbackData(false)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", FLIGHT_DATE
        )).thenReturn(Optional.of(existing));

        when(aviationApiClient.fetchLiveFlight(
                "SU100", FLIGHT_DATE
        )).thenReturn(apiResponse);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", FLIGHT_DATE);

        assertThat(response.getStatus()).isEqualTo("DELAYED");
        assertThat(response.getDelayMinutes()).isEqualTo(25);
        assertThat(response.getDepartureAirport()).isEqualTo("SVO");
        assertThat(response.getArrivalAirport()).isEqualTo("LED");
        assertThat(response.getScheduledDeparture()).isEqualTo(departure);
        assertThat(response.getScheduledArrival()).isEqualTo(arrival);
        assertThat(existing.getFlightDate()).isEqualTo(FLIGHT_DATE);

        verify(aviationApiClient).fetchLiveFlight("SU100", FLIGHT_DATE);
        verify(flightRepository).save(any(Flight.class));
        verify(logRepository).save(any());
    }

    static Stream<Arguments> partialStatusAndDelayCases() {
        return Stream.of(
                Arguments.of(null, null, "DELAYED", 45, false),
                Arguments.of(null, 60, "DELAYED", 60, true),
                Arguments.of("ON_TIME", null, "ON_TIME", 45, true),
                Arguments.of("  ", null, "DELAYED", 45, false),
                Arguments.of("DELAYED", 0, "DELAYED", 0, true)
        );
    }

    @ParameterizedTest
    @MethodSource("partialStatusAndDelayCases")
    @DisplayName("Отсутствующие поля сохраняются, история использует итоговые значения")
    void partialStatusAndDelayPreserveStoredData(
            String apiStatus,
            Integer apiDelay,
            String expectedStatus,
            Integer expectedDelay,
            boolean changeExpected
    ) {
        Flight existing = Flight.builder()
                .id(7L)
                .flightIata("SU100")
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

        ExternalFlightDto apiResponse = ExternalFlightDto.builder()
                .flightIata("SU100")
                .status(apiStatus)
                .delayMinutes(apiDelay)
                .isFallbackData(false)
                .build();

        when(flightRepository.findByFlightIataAndFlightDate(
                "SU100", FLIGHT_DATE
        )).thenReturn(Optional.of(existing));

        when(aviationApiClient.fetchLiveFlight(
                "SU100", FLIGHT_DATE
        )).thenReturn(apiResponse);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response =
                flightService.syncAndGetFlight("SU100", FLIGHT_DATE);

        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.getDelayMinutes()).isEqualTo(expectedDelay);
        assertThat(existing.getStatus()).isEqualTo(expectedStatus);
        assertThat(existing.getDelayMinutes()).isEqualTo(expectedDelay);

        verify(flightRepository).save(existing);

        if (changeExpected) {
            ArgumentCaptor<FlightStatusLog> captor =
                    ArgumentCaptor.forClass(FlightStatusLog.class);

            verify(logRepository).save(captor.capture());

            FlightStatusLog entry = captor.getValue();

            assertThat(entry.getFlight()).isSameAs(existing);
            assertThat(entry.getPreviousStatus()).isEqualTo("DELAYED");
            assertThat(entry.getNewStatus()).isEqualTo(expectedStatus);
            assertThat(entry.getDelayMinutes()).isEqualTo(expectedDelay);
        } else {
            verifyNoInteractions(logRepository);
        }
    }
}
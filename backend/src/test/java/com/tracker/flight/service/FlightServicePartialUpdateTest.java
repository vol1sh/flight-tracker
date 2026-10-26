package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

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
        Instant departure =
                Instant.parse("2026-10-25T10:00:00Z");

        Instant arrival =
                Instant.parse("2026-10-25T12:00:00Z");

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
                "SU100",
                FLIGHT_DATE
        )).thenReturn(Optional.of(existing));

        when(aviationApiClient.fetchLiveFlight(
                "SU100",
                FLIGHT_DATE
        )).thenReturn(apiResponse);

        when(flightRepository.save(any(Flight.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponseDto response = flightService.syncAndGetFlight(
                "SU100",
                FLIGHT_DATE
        );

        assertThat(response.getStatus()).isEqualTo("DELAYED");
        assertThat(response.getDelayMinutes()).isEqualTo(25);

        assertThat(response.getDepartureAirport()).isEqualTo("SVO");
        assertThat(response.getArrivalAirport()).isEqualTo("LED");

        assertThat(response.getScheduledDeparture()).isEqualTo(departure);
        assertThat(response.getScheduledArrival()).isEqualTo(arrival);

        assertThat(existing.getFlightDate()).isEqualTo(FLIGHT_DATE);

        verify(aviationApiClient)
                .fetchLiveFlight("SU100", FLIGHT_DATE);

        verify(flightRepository)
                .save(any(Flight.class));

        verify(logRepository)
                .save(any());
    }
}
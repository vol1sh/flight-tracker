
package com.tracker.flight.controller;

import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.service.FlightService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FlightController.class)
class FlightControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlightService flightService;

    private static final LocalDate DATE =
            LocalDate.of(2026, 10, 26);

    @Test
    void shouldReturnFlightDateAndFreshness()
            throws Exception {

        Instant syncedAt =
                Instant.parse("2026-10-26T09:59:30Z");

        FlightResponseDto response =
                FlightResponseDto.builder()
                        .flightIata("SU100")
                        .flightDate(DATE)
                        .departureAirport("SVO")
                        .arrivalAirport("LED")
                        .status("SCHEDULED")
                        .delayMinutes(0)
                        .lastSyncedAt(syncedAt)
                        .dataFresh(true)
                        .isDegraded(false)
                        .build();

        when(flightService.syncAndGetFlight("SU100", DATE))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/flights/SU100")
                                .param("date", "2026-10-26")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.flightIata").value("SU100")
                )
                .andExpect(
                        jsonPath("$.flightDate").value("2026-10-26")
                )
                .andExpect(
                        jsonPath("$.lastSyncedAt")
                                .value("2026-10-26T09:59:30Z")
                )
                .andExpect(
                        jsonPath("$.dataFresh").value(true)
                )
                .andExpect(
                        jsonPath("$.isDegraded").value(false)
                )
                .andExpect(
                        jsonPath("$.status").value("SCHEDULED")
                );

        verify(flightService)
                .syncAndGetFlight("SU100", DATE);
    }

    @Test
    void shouldReturn400WhenDateIsMissing()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/flights/SU100")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(flightService);
    }

    @Test
    void shouldReturn400WhenDateFormatIsInvalid()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/flights/SU100")
                                .param("date", "26-10-2026")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(flightService);
    }

    @Test
    void shouldReturn404WhenFlightIsConfirmedMissing()
            throws Exception {

        when(flightService.syncAndGetFlight("SU100", DATE))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Flight not found"
                        )
                );

        mockMvc.perform(
                        get("/api/v1/flights/SU100")
                                .param("date", "2026-10-26")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn503WhenDataIsUnavailable()
            throws Exception {

        when(flightService.syncAndGetFlight("SU100", DATE))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Provider unavailable"
                        )
                );

        mockMvc.perform(
                        get("/api/v1/flights/SU100")
                                .param("date", "2026-10-26")
                )
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldExposeDegradedFlagForSavedFallback() throws Exception {
        FlightResponseDto response = FlightResponseDto.builder()
                .flightIata("SU100")
                .flightDate(DATE)
                .status("DELAYED")
                .delayMinutes(45)
                .isDegraded(true)
                .dataFresh(false)
                .build();

        when(flightService.syncAndGetFlight("SU100", DATE))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/flights/SU100")
                        .param("date", "2026-10-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightDate").value("2026-10-26"))
                .andExpect(jsonPath("$.isDegraded").value(true))
                .andExpect(jsonPath("$.dataFresh").value(false));
    }
}
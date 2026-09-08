package com.tracker.flight.scheduler;

import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.service.FlightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тестирование фонового опросника FlightPollerScheduler")
class FlightPollerSchedulerTest {

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightService flightService;

    @InjectMocks
    private FlightPollerScheduler flightPollerScheduler;

    @Test
    @DisplayName("Опрос не вызывается, если список активных рейсов пуст")
    void pollActiveFlights_WhenNoActiveFlights_ShouldNotCallFlightService() {
        when(flightRepository.findByStatusNotIn(anyCollection())).thenReturn(Collections.emptyList());

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, never()).syncAndGetFlight(anyString());
    }

    @Test
    @DisplayName("Опросник вызывает синхронизацию для каждого активного рейса")
    void pollActiveFlights_WhenActiveFlightsExist_ShouldSyncEachFlight() {
        Flight flight1 = Flight.builder().flightIata("SU-100").status("SCHEDULED").build();
        Flight flight2 = Flight.builder().flightIata("BA-202").status("DELAYED").build();

        when(flightRepository.findByStatusNotIn(anyCollection())).thenReturn(List.of(flight1, flight2));

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1)).syncAndGetFlight("SU-100");
        verify(flightService, times(1)).syncAndGetFlight("BA-202");
    }

    @Test
    @DisplayName("Сбой синхронизации одного рейса не прерывает опрос оставшихся")
    void pollActiveFlights_WhenOneFlightFails_ShouldContinueProcessing() {
        Flight flight1 = Flight.builder().flightIata("SU-100").status("SCHEDULED").build();
        Flight flight2 = Flight.builder().flightIata("BA-202").status("SCHEDULED").build();

        when(flightRepository.findByStatusNotIn(anyCollection())).thenReturn(List.of(flight1, flight2));
        when(flightService.syncAndGetFlight("SU-100")).thenThrow(new RuntimeException("External API failure"));

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1)).syncAndGetFlight("SU-100");
        verify(flightService, times(1)).syncAndGetFlight("BA-202");
    }
}

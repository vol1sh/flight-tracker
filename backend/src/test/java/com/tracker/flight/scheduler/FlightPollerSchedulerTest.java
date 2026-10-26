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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тестирование фонового опросника рейсов")
class FlightPollerSchedulerTest {

    private static final LocalDate FLIGHT_DATE =
            LocalDate.of(2026, 10, 25);

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightService flightService;

    @InjectMocks
    private FlightPollerScheduler flightPollerScheduler;

    @Test
    @DisplayName("Пустой список рейсов: сервис не вызывается")
    void pollActiveFlights_WhenNoActiveFlights_ShouldNotCallFlightService() {
        when(flightRepository.findByStatusNotIn(anyList()))
                .thenReturn(Collections.emptyList());

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, never())
                .syncAndGetFlight(anyString(), any(LocalDate.class));
    }

    @Test
    @DisplayName("Одинаковый номер за разные дни обновляется с нужной датой")
    void pollActiveFlights_WhenActiveFlightsExist_ShouldSyncEachFlight() {
        Flight first = Flight.builder()
                .flightIata("SU-100")
                .flightDate(FLIGHT_DATE)
                .status("SCHEDULED")
                .build();

        Flight second = Flight.builder()
                .flightIata("SU-100")
                .flightDate(FLIGHT_DATE.plusDays(1))
                .status("DELAYED")
                .build();

        when(flightRepository.findByStatusNotIn(anyList()))
                .thenReturn(List.of(first, second));

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1))
                .syncAndGetFlight("SU-100", FLIGHT_DATE);

        verify(flightService, times(1))
                .syncAndGetFlight("SU-100", FLIGHT_DATE.plusDays(1));
    }

    @Test
    @DisplayName("Ошибка одного рейса не прерывает обновление остальных")
    void pollActiveFlights_WhenOneFlightFails_ShouldContinueProcessing() {
        Flight first = Flight.builder()
                .flightIata("SU-100")
                .flightDate(FLIGHT_DATE)
                .status("SCHEDULED")
                .build();

        Flight second = Flight.builder()
                .flightIata("BA-202")
                .flightDate(FLIGHT_DATE)
                .status("SCHEDULED")
                .build();

        when(flightRepository.findByStatusNotIn(anyList()))
                .thenReturn(List.of(first, second));

        when(flightService.syncAndGetFlight("SU-100", FLIGHT_DATE))
                .thenThrow(new RuntimeException("External API failure"));

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1))
                .syncAndGetFlight("SU-100", FLIGHT_DATE);

        verify(flightService, times(1))
                .syncAndGetFlight("BA-202", FLIGHT_DATE);
    }

    @Test
    @DisplayName("Старая запись без даты пропускается")
    void pollActiveFlights_WhenDateIsUnknown_ShouldSkipLegacyRecord() {
        Flight legacyFlight = Flight.builder()
                .flightIata("SU100")
                .status("SCHEDULED")
                .build();

        when(flightRepository.findByStatusNotIn(anyList()))
                .thenReturn(List.of(legacyFlight));

        flightPollerScheduler.pollActiveFlights();

        verifyNoInteractions(flightService);
    }
}
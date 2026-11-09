package com.tracker.flight.scheduler;

import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.service.FlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тестирование фонового опросника рейсов")
class FlightPollerSchedulerTest {

    private static final LocalDate FLIGHT_DATE = LocalDate.of(2026, 10, 25);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightService flightService;

    private FlightPollerScheduler flightPollerScheduler;

    @BeforeEach
    void setUp() {
        flightPollerScheduler = poller(5);
    }

    private FlightPollerScheduler poller(int maxPerCycle) {
        return new FlightPollerScheduler(flightRepository, flightService, maxPerCycle, 1, CLOCK);
    }

    private void candidates(List<Flight> flights) {
        when(flightRepository.findByStatusNotInAndFlightDateBetween(
                FlightPollerScheduler.TERMINAL_STATUSES,
                FLIGHT_DATE.minusDays(1),
                FLIGHT_DATE.plusDays(1)
        )).thenReturn(flights);
    }

    private static Flight flight(String iata, LocalDate date, Instant lastSyncedAt) {
        return Flight.builder()
                .flightIata(iata)
                .flightDate(date)
                .status("SCHEDULED")
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    private static FlightResponseDto fresh() {
        return FlightResponseDto.builder().isDegraded(false).build();
    }

    private static FlightResponseDto degraded() {
        return FlightResponseDto.builder().isDegraded(true).build();
    }

    @Test
    @DisplayName("Пустой список рейсов: сервис не вызывается")
    void pollActiveFlights_WhenNoActiveFlights_ShouldNotCallFlightService() {
        candidates(Collections.emptyList());

        flightPollerScheduler.pollActiveFlights();

        verifyNoInteractions(flightService);
    }

    @Test
    @DisplayName("Опрашиваются только даты в окне [сегодня-1; сегодня+1] (UTC)")
    void pollActiveFlights_AsksRepositoryOnlyForDateWindow() {
        candidates(Collections.emptyList());

        flightPollerScheduler.pollActiveFlights();

        verify(flightRepository).findByStatusNotInAndFlightDateBetween(
                FlightPollerScheduler.TERMINAL_STATUSES,
                LocalDate.of(2026, 10, 24),
                LocalDate.of(2026, 10, 26)
        );
    }

    @Test
    @DisplayName("Одинаковый номер за разные дни обновляется с нужной датой")
    void pollActiveFlights_WhenActiveFlightsExist_ShouldSyncEachFlight() {
        candidates(List.of(
                flight("SU-100", FLIGHT_DATE, null),
                flight("SU-100", FLIGHT_DATE.plusDays(1), null)
        ));
        when(flightService.syncAndGetFlight(anyString(), any())).thenReturn(fresh());

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1)).syncAndGetFlight("SU-100", FLIGHT_DATE);
        verify(flightService, times(1)).syncAndGetFlight("SU-100", FLIGHT_DATE.plusDays(1));
    }

    @Test
    @DisplayName("Лимит рейсов за цикл: первыми обновляются давно не синхронизированные")
    void pollActiveFlights_RespectsBudget_OldestSyncedFirst() {
        candidates(List.of(
                flight("AA111", FLIGHT_DATE, Instant.parse("2026-10-25T11:00:00Z")),
                flight("BB222", FLIGHT_DATE, Instant.parse("2026-10-25T09:00:00Z")),
                flight("CC333", FLIGHT_DATE, null)
        ));
        when(flightService.syncAndGetFlight(anyString(), any())).thenReturn(fresh());

        poller(2).pollActiveFlights();

        InOrder order = inOrder(flightService);
        order.verify(flightService).syncAndGetFlight("CC333", FLIGHT_DATE);
        order.verify(flightService).syncAndGetFlight("BB222", FLIGHT_DATE);
        verify(flightService, never()).syncAndGetFlight("AA111", FLIGHT_DATE);
    }

    @Test
    @DisplayName("Ошибка одного рейса (не недоступность поставщика) не прерывает обновление остальных")
    void pollActiveFlights_WhenOneFlightFails_ShouldContinueProcessing() {
        candidates(List.of(
                flight("SU-100", FLIGHT_DATE, null),
                flight("BA-202", FLIGHT_DATE, Instant.parse("2026-10-25T10:00:00Z"))
        ));
        when(flightService.syncAndGetFlight("SU-100", FLIGHT_DATE))
                .thenThrow(new RuntimeException("Unexpected"));
        when(flightService.syncAndGetFlight("BA-202", FLIGHT_DATE)).thenReturn(fresh());

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1)).syncAndGetFlight("SU-100", FLIGHT_DATE);
        verify(flightService, times(1)).syncAndGetFlight("BA-202", FLIGHT_DATE);
    }

    @Test
    @DisplayName("404 по одному рейсу — пропуск, остальные обновляются")
    void pollActiveFlights_When404_ShouldContinue() {
        candidates(List.of(
                flight("SU-100", FLIGHT_DATE, null),
                flight("BA-202", FLIGHT_DATE, Instant.parse("2026-10-25T10:00:00Z"))
        ));
        when(flightService.syncAndGetFlight("SU-100", FLIGHT_DATE))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(flightService.syncAndGetFlight("BA-202", FLIGHT_DATE)).thenReturn(fresh());

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, times(1)).syncAndGetFlight("BA-202", FLIGHT_DATE);
    }

    @Test
    @DisplayName("Degraded-ответ (поставщик недоступен) прерывает цикл — квота не тратится")
    void pollActiveFlights_WhenDegraded_ShouldStopCycle() {
        candidates(List.of(
                flight("SU-100", FLIGHT_DATE, null),
                flight("BA-202", FLIGHT_DATE, Instant.parse("2026-10-25T10:00:00Z"))
        ));
        when(flightService.syncAndGetFlight("SU-100", FLIGHT_DATE)).thenReturn(degraded());

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, never()).syncAndGetFlight("BA-202", FLIGHT_DATE);
    }

    @Test
    @DisplayName("503 (поставщик недоступен, сохранённых данных нет) прерывает цикл")
    void pollActiveFlights_When503_ShouldStopCycle() {
        candidates(List.of(
                flight("SU-100", FLIGHT_DATE, null),
                flight("BA-202", FLIGHT_DATE, Instant.parse("2026-10-25T10:00:00Z"))
        ));
        when(flightService.syncAndGetFlight("SU-100", FLIGHT_DATE))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        flightPollerScheduler.pollActiveFlights();

        verify(flightService, never()).syncAndGetFlight("BA-202", FLIGHT_DATE);
    }

    @Test
    @DisplayName("Старая запись без даты пропускается")
    void pollActiveFlights_WhenDateIsUnknown_ShouldSkipLegacyRecord() {
        candidates(List.of(flight("SU100", null, null)));

        flightPollerScheduler.pollActiveFlights();

        verifyNoInteractions(flightService);
    }
}
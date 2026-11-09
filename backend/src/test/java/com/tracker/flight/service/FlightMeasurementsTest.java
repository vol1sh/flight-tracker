package com.tracker.flight.service;

import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightMeasurementsTest {
    @Mock AviationApiClient api;
    @Mock FlightRepository flights;
    @Mock FlightStatusLogRepository logs;
    @InjectMocks FlightService service;
    private final LocalDate date = LocalDate.of(2026, 11, 9);
    private final Instant now = Instant.parse("2026-11-09T12:00:00Z");

    @BeforeEach void saveIdentity() {
        lenient().when(flights.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ExternalFlightDto.Builder payload() {
        return ExternalFlightDto.builder().flightIata("SU100").status("SCHEDULED")
                .departureAirport("SVO").arrivalAirport("LED").scheduledDeparture(now)
                .scheduledArrival(now.plusSeconds(3600)).fetchedAt(now);
    }

    @Test void newScheduledFlightWithoutDelaysIsStoredWithNullHistory() {
        when(api.fetchLiveFlight("SU100", date)).thenReturn(payload().delayMinutes(null).build());
        var response = service.syncAndGetFlight("SU100", date);
        assertThat(response.getDelayMinutes()).isNull();
        assertThat(response.getArrivalDelayMinutes()).isNull();
        var log = ArgumentCaptor.forClass(FlightStatusLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().getDelayMinutes()).isNull();
    }

    @Test void splitDelaysAndEstimatedArrivalArePersistedAndAudited() {
        var flight = Flight.builder().id(1L).flightIata("SU100").flightDate(date).status("SCHEDULED")
                .delayMinutes(null).estimatedArrival(now.plusSeconds(3600)).build();
        when(flights.findForUpdate("SU100", date)).thenReturn(Optional.of(flight));
        when(api.fetchLiveFlight("SU100", date)).thenReturn(payload().delayMinutes(null)
                .departureDelayMinutes(5).arrivalDelayMinutes(20).estimatedArrival(now.plusSeconds(4800)).build());
        var response = service.syncAndGetFlight("SU100", date);
        assertThat(response.getDepartureDelayMinutes()).isEqualTo(5);
        assertThat(response.getArrivalDelayMinutes()).isEqualTo(20);
        assertThat(response.getEstimatedArrival()).isEqualTo(now.plusSeconds(4800));
        verify(logs).save(any());
    }

    @Test void nullDelayDoesNotPreventRecordingCancellation() {
        var flight = Flight.builder().id(1L).flightIata("SU100").flightDate(date)
                .status("SCHEDULED").delayMinutes(null).build();
        when(flights.findForUpdate("SU100", date)).thenReturn(Optional.of(flight));
        when(api.fetchLiveFlight("SU100", date)).thenReturn(payload().status("CANCELLED").delayMinutes(null).build());
        assertThat(service.syncAndGetFlight("SU100", date).getStatus()).isEqualTo("CANCELLED");
    }

    @Test void fallbackAndOlderCacheCannotChangeFlightOrHistory() {
        var flight = Flight.builder().id(1L).flightIata("SU100").flightDate(date).status("SCHEDULED")
                .departureIata("SVO").arrivalIata("LED").scheduledDepartureTime(now)
                .scheduledArrivalTime(now.plusSeconds(3600)).lastSyncedAt(now).build();
        when(flights.findForUpdate("SU100", date)).thenReturn(Optional.of(flight));
        when(api.fetchLiveFlight("SU100", date))
                .thenReturn(payload().status("DELAYED").isFallbackData(true).build())
                .thenReturn(payload().status("DELAYED").fetchedAt(now.minusSeconds(10)).build());
        assertThat(service.syncAndGetFlight("SU100", date).isDegraded()).isTrue();
        assertThat(service.syncAndGetFlight("SU100", date).getStatus()).isEqualTo("SCHEDULED");
        verify(flights, never()).save(any());
        verifyNoInteractions(logs);
    }

    @Test void landingWithoutFinalDelayOrActualTimeDiscardsEarlierEstimate() {
        var flight = Flight.builder().id(1L).flightIata("SU100").flightDate(date)
                .status("ACTIVE").arrivalDelayMinutes(45).build();
        when(flights.findForUpdate("SU100", date)).thenReturn(Optional.of(flight));
        when(api.fetchLiveFlight("SU100", date)).thenReturn(payload().status("LANDED").build());
        var response = service.syncAndGetFlight("SU100", date);
        assertThat(response.getArrivalDelayMinutes()).isNull();
        assertThat(flight.getArrivalDelayMinutes()).isNull();
        var entry = ArgumentCaptor.forClass(FlightStatusLog.class);
        verify(logs).save(entry.capture());
        assertThat(entry.getValue().getArrivalDelayMinutes()).isNull();
    }
}

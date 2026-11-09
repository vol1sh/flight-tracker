package com.tracker.flight.service;

import com.tracker.flight.dto.FlightAnalyticsSummaryDto;
import com.tracker.flight.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {
    private final FlightRepository repository = mock(FlightRepository.class);
    private final AnalyticsService service = new AnalyticsService(repository);
    private final LocalDate from = LocalDate.of(2026, 10, 1);

    @Test void databaseFailureIsNeverConvertedIntoEmptyOrZeroStatistics() {
        var failure = new DataAccessResourceFailureException("Database unavailable");
        when(repository.getAnalyticsByFlightIata(anyString(), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.getFlightAnalytics("SU100", from, from.plusDays(30))).isSameAs(failure);
    }

    @Test void insufficientCoverageMasksAllMetricsEvenWithManyCompletedFlights() {
        var aggregate = mock(FlightAnalyticsSummaryDto.class);
        when(repository.getAnalyticsByFlightIata(anyString(), any(), any())).thenReturn(aggregate);
        when(aggregate.getSampleSize()).thenReturn(20L);
        when(aggregate.getLandedCount()).thenReturn(10L);
        when(aggregate.getCancelledCount()).thenReturn(10L);
        when(aggregate.getKnownArrivalDelayCount()).thenReturn(7L);
        var summary = service.getFlightAnalytics("SU100", from, from.plusDays(30));
        assertThat(summary.dataSufficient()).isFalse();
        assertThat(summary.knownArrivalDelayCount()).isEqualTo(7);
        assertThat(summary.averageDelayMinutes()).isNull();
        assertThat(summary.onTimeRate()).isNull();
        assertThat(summary.cancellationRate()).isNull();
        assertThat(summary.reliabilityScore()).isNull();
    }

    @Test void reliabilityCannotBecomeNegativeWhenDelaysAreLarge() {
        var aggregate = mock(FlightAnalyticsSummaryDto.class);
        when(repository.getAnalyticsByFlightIata(anyString(), any(), any())).thenReturn(aggregate);
        when(aggregate.getSampleSize()).thenReturn(10L);
        when(aggregate.getLandedCount()).thenReturn(10L);
        when(aggregate.getCancelledCount()).thenReturn(0L);
        when(aggregate.getKnownArrivalDelayCount()).thenReturn(10L);
        when(aggregate.getAverageDelayMinutes()).thenReturn(400.0);
        when(aggregate.getOnTimeRate()).thenReturn(0.0);
        when(aggregate.getCancellationRate()).thenReturn(0.0);
        assertThat(service.getFlightAnalytics("SU100", from, from.plusDays(30)).reliabilityScore()).isZero();
    }
}

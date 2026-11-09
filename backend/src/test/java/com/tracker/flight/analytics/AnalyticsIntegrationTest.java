package com.tracker.flight.analytics;

import com.tracker.flight.AbstractIntegrationTest;
import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.entity.FlightStatusLog;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import com.tracker.flight.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.scheduler.flight-poller.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AnalyticsIntegrationTest extends AbstractIntegrationTest {
    @Autowired FlightRepository flights;
    @Autowired FlightStatusLogRepository logs;
    @Autowired AnalyticsService analytics;
    @Autowired MockMvc mvc;
    @MockBean AviationApiClient api;
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final Instant SYNC = Instant.parse("2026-11-01T12:00:00Z");

    private Flight save(String iata, LocalDate day, String status, Integer delay) {
        return flights.saveAndFlush(Flight.builder().flightIata(iata).flightDate(day)
                .departureIata("SVO").arrivalIata("LED").status(status)
                .scheduledDepartureTime(day.atTime(8, 0).toInstant(ZoneOffset.UTC))
                .scheduledArrivalTime(day.atTime(10, 0).toInstant(ZoneOffset.UTC))
                .arrivalDelayMinutes(delay).lastSyncedAt(SYNC).build());
    }

    private void golden() {
        for (int i = 0; i < 20; i++) {
            Integer delay = null;
            if (i < 14) delay = 0;
            else if (i < 18) delay = List.of(15, 30, 45, 30).get(i - 14);
            Flight f = save("SU100", START.plusDays(i), i < 18 ? "LANDED" : "CANCELLED", delay);
            if (i == 0) for (int n = 0; n < 6; n++) {
                logs.save(FlightStatusLog.builder().flight(f).previousStatus("SCHEDULED")
                        .newStatus("LANDED").delayMinutes(0).build());
            }
        }
    }

    @Test void goldenDatasetCountsExecutionsOnceAndMatchesAllFourMetricsWithoutExternalApi() throws Exception {
        golden();
        var result = analytics.getFlightAnalytics(" su-100 ", START, START.plusDays(30));
        assertThat(result.sampleSize()).isEqualTo(20);
        assertThat(result.landedCount()).isEqualTo(18);
        assertThat(result.cancelledCount()).isEqualTo(2);
        assertThat(result.knownArrivalDelayCount()).isEqualTo(18);
        assertThat(result.averageDelayMinutes()).isEqualTo(6.67);
        assertThat(result.onTimeRate()).isEqualTo(0.7778);
        assertThat(result.cancellationRate()).isEqualTo(0.1);
        assertThat(result.reliabilityScore()).isEqualTo(76.67);
        assertThat(result.dataSufficient()).isTrue();
        assertThat(result.lastUpdatedAt()).isEqualTo(SYNC);
        assertThat(analytics.getRouteAnalytics("svo", "led", START, START.plusDays(30)).averageDelayMinutes()).isEqualTo(6.67);
        mvc.perform(get("/api/v1/analytics/flights/SU100").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("dataSource").value("POSTGRESQL"))
                .andExpect(jsonPath("scope.flightIata").value("SU100"))
                .andExpect(jsonPath("period.from").value("2026-10-01"))
                .andExpect(jsonPath("averageDelayMinutes").value(6.67));
        mvc.perform(get("/api/v1/analytics/executions").param("flightIata", "SU-100")
                        .param("from", "2026-10-01").param("to", "2026-10-31").param("size", "3").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(20))
                .andExpect(jsonPath("content.length()").value(3))
                .andExpect(jsonPath("content[0].flightDate").value("2026-10-17"));
        verifyNoInteractions(api);
    }

    @Test void unknownDelayIsExcludedInsteadOfBecomingZeroInPostgresGreatest() {
        for (int i = 0; i < 10; i++) save("SU100", START.plusDays(i), "LANDED", i < 8 ? 20 : null);
        var summary = analytics.getFlightAnalytics("SU100", START, START.plusDays(30));
        assertThat(summary.knownArrivalDelayCount()).isEqualTo(8);
        assertThat(summary.dataSufficient()).isTrue();
        assertThat(summary.averageDelayMinutes()).isEqualTo(20);
        assertThat(summary.onTimeRate()).isZero();
    }

    @Test void actualArrivalFallbackClampsEarlyArrivalAndDoesNotUseDepartureDelay() {
        for (int i = 0; i < 10; i++) {
            Flight flight = save("SU100", START.plusDays(i), "LANDED", null);
            flight.setDepartureDelayMinutes(99);
            flight.setActualArrivalTime(flight.getScheduledArrivalTime().plusSeconds(i < 5 ? -600 : 900));
        }
        flights.flush();
        var summary = analytics.getFlightAnalytics("SU100", START, START.plusDays(30));
        assertThat(summary.knownArrivalDelayCount()).isEqualTo(10);
        assertThat(summary.averageDelayMinutes()).isEqualTo(7.5);
        assertThat(summary.onTimeRate()).isEqualTo(0.5);
        assertThat(analytics.getExecutions("SU100", null, null, START, START.plusDays(30), 0, 20)
                .getContent()).extracting("arrivalDelayMinutes").containsOnly(0, 15);
    }

    @Test void tenCompletedIncludingCancellationsAreSufficientAndOtherStatusesDoNotChangeDenominators() {
        for (int i = 0; i < 10; i++) save("SU100", START.plusDays(i), i < 8 ? "LANDED" : "CANCELLED", i < 8 ? 0 : null);
        var current = save("SU100", START.plusDays(10), "ACTIVE", 120);
        current.setLastSyncedAt(SYNC.plusSeconds(60));
        flights.flush();
        var summary = analytics.getFlightAnalytics("SU100", START, START.plusDays(30));
        assertThat(summary.sampleSize()).isEqualTo(11);
        assertThat(summary.dataSufficient()).isTrue();
        assertThat(summary.averageDelayMinutes()).isZero();
        assertThat(summary.onTimeRate()).isEqualTo(1);
        assertThat(summary.cancellationRate()).isEqualTo(0.2);
        assertThat(summary.lastUpdatedAt()).isEqualTo(SYNC.plusSeconds(60));
    }

    @Test void emptySmallAllUnknownAndAllCancelledSamplesHaveNullMetricsAndPreserveCounts() throws Exception {
        var empty = analytics.getFlightAnalytics("SU100", START, START.plusDays(30));
        assertThat(empty.sampleSize()).isZero();
        assertThat(empty.lastUpdatedAt()).isNull();
        save("SU100", START, "LANDED", 0);
        assertThat(analytics.getFlightAnalytics("SU100", START, START).dataSufficient()).isFalse();
        for (int i = 0; i < 10; i++) {
            save("BA200", START.plusDays(i), "LANDED", null);
            save("AF300", START.plusDays(i), "CANCELLED", null);
        }
        for (String iata : List.of("SU100", "BA200", "AF300")) {
            var summary = analytics.getFlightAnalytics(iata, START, START.plusDays(30));
            assertThat(summary.dataSufficient()).isFalse();
            assertThat(summary.averageDelayMinutes()).isNull();
            assertThat(summary.onTimeRate()).isNull();
            assertThat(summary.cancellationRate()).isNull();
            assertThat(summary.reliabilityScore()).isNull();
        }
        assertThat(analytics.getFlightAnalytics("AF300", START, START.plusDays(30)).cancelledCount()).isEqualTo(10);
        mvc.perform(get("/api/v1/analytics/flights/BA200").param("from", START.toString()).param("to", START.plusDays(30).toString()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"averageDelayMinutes\":null")));
        verifyNoInteractions(api);
    }

    @Test void routeAndInclusiveDateFiltersExcludeOtherExecutions() {
        golden();
        var outsideRoute = save("BA200", START, "LANDED", 100);
        outsideRoute.setArrivalIata("LHR");
        save("SU100", START.minusDays(1), "LANDED", 500);
        flights.flush();
        assertThat(analytics.getRouteAnalytics("SVO", "LED", START, START.plusDays(19)).sampleSize()).isEqualTo(20);
        assertThat(analytics.getFlightAnalytics("SU100", START, START.plusDays(19)).sampleSize()).isEqualTo(20);
        assertThat(analytics.getExecutions(null, "SVO", "LED", START, START, 0, 20).getTotalElements()).isEqualTo(1);
    }

    @Test void invalidFiltersAndPaginationReturn400() throws Exception {
        for (String path : List.of(
                "/flights/SU100?from=2026-11-01&to=2026-10-01",
                "/flights/SU100?from=2025-01-01&to=2026-10-01",
                "/flights/garbage?from=2026-10-01&to=2026-10-31",
                "/routes?departure=SV&arrival=LED&from=2026-10-01&to=2026-10-31",
                "/routes?departure=SVO&from=2026-10-01&to=2026-10-31",
                "/executions?from=2026-10-01&to=2026-10-31",
                "/executions?flightIata=SU100&arrival=LED&from=2026-10-01&to=2026-10-31",
                "/executions?departure=SVO&from=2026-10-01&to=2026-10-31",
                "/executions?flightIata=SU100&size=101&from=2026-10-01&to=2026-10-31",
                "/executions?flightIata=SU100&page=-1&from=2026-10-01&to=2026-10-31",
                "/flights/SU100?from=not-a-date&to=2026-10-31",
                "/flights/SU100?from=2026-10-01")) {
            mvc.perform(get("/api/v1/analytics" + path)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(api);
    }
}

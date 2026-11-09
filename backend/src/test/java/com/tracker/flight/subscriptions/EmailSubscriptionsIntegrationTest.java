package com.tracker.flight.subscriptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.flight.AbstractIntegrationTest;
import com.tracker.flight.client.AviationApiClient;
import com.tracker.flight.dto.CreateEmailSubscriptionRequest;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.entity.EmailNotification;
import com.tracker.flight.entity.Flight;
import com.tracker.flight.repository.EmailNotificationRepository;
import com.tracker.flight.repository.EmailSubscriptionRepository;
import com.tracker.flight.repository.FlightRepository;
import com.tracker.flight.repository.FlightStatusLogRepository;
import com.tracker.flight.service.EmailOutboxService;
import com.tracker.flight.service.EmailSenderService;
import com.tracker.flight.service.EmailSubscriptionService;
import com.tracker.flight.service.FlightService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.mail.enabled=false", "app.scheduler.flight-poller.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class EmailSubscriptionsIntegrationTest extends AbstractIntegrationTest {
    @Autowired FlightRepository flights;
    @Autowired FlightStatusLogRepository logs;
    @Autowired EmailSubscriptionRepository subscriptions;
    @Autowired EmailNotificationRepository notifications;
    @Autowired EmailSubscriptionService email;
    @Autowired EmailOutboxService outbox;
    @Autowired FlightService flightService;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AviationApiClient api;
    @MockBean EmailSenderService sender;
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private static final String BASE = "/api/v1/subscriptions/email";

    private Flight scheduled(String iata) {
        Instant departure = today.atTime(14, 0).toInstant(ZoneOffset.UTC);
        return flights.saveAndFlush(Flight.builder().flightIata(iata).flightDate(today)
                .departureIata("SVO").arrivalIata("LED").scheduledDepartureTime(departure)
                .scheduledArrivalTime(departure.plusSeconds(3600)).status("SCHEDULED")
                .lastSyncedAt(Instant.now().minusSeconds(100)).build());
    }

    private CreateEmailSubscriptionRequest request(String iata, String address) {
        return new CreateEmailSubscriptionRequest(iata, today, address);
    }

    private record Links(String confirm, String unsubscribe) {}

    private Links deliverConfirmation(String address) {
        assertThat(outbox.deliverNext()).isTrue();
        var body = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).send(eq(address), contains("Подтверждение"), body.capture());
        return new Links(token(body.getValue(), "confirm"), token(body.getValue(), "unsubscribe"));
    }

    private String token(String body, String action) {
        var match = Pattern.compile("/" + action + "\\?token=([A-Za-z0-9_-]{43})").matcher(body);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }

    @Test void createConfirmAndUnsubscribeThroughHttpDoNotExposeOrPersistPlaintextSecrets() throws Exception {
        scheduled("SU100");
        var form = request("su-100", "USER@example.com");
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(json.writeValueAsBytes(form)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("status").value("PENDING"))
                .andExpect(jsonPath("token").doesNotExist());
        email.create(form);
        assertThat(subscriptions.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
        Links links = deliverConfirmation("user@example.com");
        assertThat(links.confirm()).isNotEqualTo(links.unsubscribe());
        em.flush(); em.clear();
        var sub = subscriptions.findAll().getFirst();
        assertThat(sub.getConfirmationTokenHash()).hasSize(64).isNotEqualTo(links.confirm());
        assertThat(sub.getUnsubscribeTokenHash()).hasSize(64).isNotEqualTo(links.unsubscribe());
        assertThat(notifications.findAll().getFirst().getBody()).doesNotContain(links.confirm(), links.unsubscribe());
        mvc.perform(get(BASE + "/confirm").param("token", links.confirm()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sub.getStatus()).isEqualTo("PENDING");
        mvc.perform(post(BASE + "/confirm").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("token", links.confirm()))))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("ACTIVE"));
        assertThat(sub.getConfirmationTokenHash()).isNull();
        assertThat(sub.getConfirmedAt()).isNotNull();
        email.create(form);
        assertThat(notifications.count()).isEqualTo(1);
        mvc.perform(post(BASE + "/unsubscribe").contentType(APPLICATION_FORM_URLENCODED).param("token", links.unsubscribe()))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("UNSUBSCRIBED"));
        assertThat(email.unsubscribe(links.unsubscribe()).status()).isEqualTo("UNSUBSCRIBED");
        email.create(form);
        assertThat(subscriptions.count()).isEqualTo(2);
        verifyNoInteractions(api);
    }

    @Test void malformedExpiredAndWrongPurposeTokensAreRejected() throws Exception {
        scheduled("SU100");
        email.create(request("SU100", "user@example.com"));
        var links = deliverConfirmation("user@example.com");
        subscriptions.findAll().getFirst().setTokenExpiresAt(Instant.now().minusSeconds(1));
        for (String invalid : List.of(links.confirm(), links.unsubscribe(), "bad")) {
            mvc.perform(post(BASE + "/confirm").contentType(APPLICATION_JSON)
                            .content(json.writeValueAsBytes(Map.of("token", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(json.writeValueAsBytes(request("SU100", "not-an-email"))))
                .andExpect(status().isBadRequest());
        assertThat(subscriptions.findAll().getFirst().getStatus()).isEqualTo("PENDING");
    }

    @Test void confirmationResendsObserveTheCooldownAndHourlyLimit() {
        scheduled("SU100");
        var form = request("SU100", "user@example.com");
        email.create(form);
        var sub = subscriptions.findAll().getFirst();
        email.create(form);
        assertThat(notifications.count()).isEqualTo(1);
        for (int i = 0; i < 8; i++) {
            sub.setLastRequestedAt(Instant.now().minusSeconds(120));
            email.create(form);
        }
        assertThat(notifications.count()).isEqualTo(5);
        em.flush(); em.clear();
        assertThat(notifications.findAll()).filteredOn(n -> "PENDING".equals(n.getStatus())).hasSize(1);
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsForOneAddressOnDifferentFlightsShareOneLimit() throws Exception {
        String address = "parallel-requests@example.com";
        var codes = List.of("QZ7101", "QZ7102", "QZ7103", "QZ7104", "QZ7105", "QZ7106");
        try {
            codes.forEach(this::scheduled);
            var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(codes.size())) {
                List<Future<?>> results = new ArrayList<>();
                for (String code : codes) results.add(pool.submit(() -> {
                    start.await();
                    return email.create(request(code, address));
                }));
                start.countDown();
                for (var result : results) result.get(20, TimeUnit.SECONDS);
            }
            assertThat(notifications.countRecentConfirmations(address, Instant.now().minusSeconds(3600))).isEqualTo(5);
        } finally {
            jdbc.update("delete from email_subscriptions where email=?", address);
            for (String code : codes) jdbc.update("delete from flights where flight_iata=? and flight_date=?", code, today);
        }
    }

    @Test void etaOnlyChangeIsQueuedOncePerActiveSubscriberAndUnsubscribeCancelsPendingDelivery() {
        Flight flight = scheduled("SU100");
        email.create(request("SU100", "one@example.com"));
        var one = deliverConfirmation("one@example.com");
        email.confirm(one.confirm());
        email.create(request("SU100", "two@example.com"));
        email.confirm(deliverConfirmation("two@example.com").confirm());
        assertThat(flights.findSubscribedFlights(List.of("LANDED", "CANCELLED"), today, today)).hasSize(1);
        var live = ExternalFlightDto.builder().flightIata("SU100").status("SCHEDULED")
                .estimatedArrival(Instant.now().plusSeconds(1800)).fetchedAt(Instant.now()).build();
        when(api.fetchLiveFlight("SU100", today)).thenReturn(live);
        flightService.syncAndGetFlight("SU100", today);
        flightService.syncAndGetFlight("SU100", today);
        var changes = notifications.findAll().stream().filter(n -> "FLIGHT_CHANGED".equals(n.getEventType())).toList();
        assertThat(changes).hasSize(2);
        assertThat(changes).extracting(EmailNotification::getEventKey).containsOnly(changes.getFirst().getEventKey());
        assertThat(logs.findByFlightIdOrderByRecordedAtDesc(flight.getId())).hasSize(1);
        email.unsubscribe(one.unsubscribe());
        clearInvocations(sender);
        assertThat(outbox.deliverNext()).isTrue();
        assertThat(outbox.deliverNext()).isFalse();
        verify(sender).send(eq("two@example.com"), contains("SU100"), contains("Ожидаемое прибытие"));
        verifyNoMoreInteractions(sender);
    }

    @Test void fallbackAndStaleObservationCannotTriggerEmail() {
        var flight = scheduled("SU100");
        flight.setLastSyncedAt(Instant.now().minusSeconds(7200));
        email.create(request("SU100", "one@example.com"));
        email.confirm(deliverConfirmation("one@example.com").confirm());
        var stale = ExternalFlightDto.builder().flightIata("SU100").status("DELAYED")
                .arrivalDelayMinutes(15).fetchedAt(Instant.now().minusSeconds(3600)).build();
        when(api.fetchLiveFlight("SU100", today)).thenReturn(ExternalFlightDto.builder().isFallbackData(true).build(), stale);
        assertThat(flightService.syncAndGetFlight("SU100", today).isDegraded()).isTrue();
        flightService.syncAndGetFlight("SU100", today);
        assertThat(notifications.findAll()).noneMatch(n -> "FLIGHT_CHANGED".equals(n.getEventType()));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollingBackFlightUpdateAlsoRollsBackItsHistoryAndOutboxEvent() {
        String code = "QZ7200";
        String address = "rollback-check@example.com";
        var flight = scheduled(code);
        try {
            email.create(request(code, address));
            email.confirm(deliverConfirmation(address).confirm());
            when(api.fetchLiveFlight(code, today)).thenReturn(ExternalFlightDto.builder().flightIata(code)
                    .status("CANCELLED").fetchedAt(Instant.now()).build());
            transactions.executeWithoutResult(tx -> {
                flightService.syncAndGetFlight(code, today);
                em.flush();
                assertThat(jdbc.queryForObject("select count(*) from email_notifications n join email_subscriptions s "
                        + "on s.id=n.subscription_id where s.email=? and n.event_type='FLIGHT_CHANGED'", Integer.class, address)).isEqualTo(1);
                tx.setRollbackOnly();
            });
            assertThat(flights.findById(flight.getId()).orElseThrow().getStatus()).isEqualTo("SCHEDULED");
            assertThat(logs.findByFlightIdOrderByRecordedAtDesc(flight.getId())).isEmpty();
            assertThat(notifications.findAll()).noneMatch(n -> "FLIGHT_CHANGED".equals(n.getEventType()));
        } finally {
            jdbc.update("delete from email_subscriptions where email=?", address);
            jdbc.update("delete from flights where id=?", flight.getId());
        }
    }
}

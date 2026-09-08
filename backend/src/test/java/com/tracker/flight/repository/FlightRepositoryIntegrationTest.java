package com.tracker.flight.repository;

import com.tracker.flight.AbstractIntegrationTest;
import com.tracker.flight.entity.Flight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Интеграционное тестирование FlightRepository через базу данных PostgreSQL")
class FlightRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FlightRepository flightRepository;

    @BeforeEach
    void setUp() {
        flightRepository.deleteAll();
    }

    @Test
    @DisplayName("База данных PostgreSQL успешно запущена и отвечает")
    void containerIsRunning() {
        assertThat(isDatabaseReady()).isTrue();
    }

    @Test
    @DisplayName("Сохранение рейса и поиск по IATA коду в реальной БД PostgreSQL")
    void saveAndFindByFlightIata_ShouldPersistAndReturnFlight() {
        Flight flight = Flight.builder()
                .flightIata("SU-100")
                .departureIata("SVO")
                .arrivalIata("LED")
                .scheduledDepartureTime(Instant.now().plusSeconds(3600))
                .scheduledArrivalTime(Instant.now().plusSeconds(7200))
                .status("SCHEDULED")
                .delayMinutes(0)
                .build();

        flightRepository.save(flight);

        Optional<Flight> found = flightRepository.findByFlightIata("SU-100");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getDepartureIata()).isEqualTo("SVO");
        assertThat(found.get().getArrivalIata()).isEqualTo("LED");
        assertThat(found.get().getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("Поиск активных рейсов: исключение терминальных статусов LANDED и CANCELLED")
    void findByStatusNotIn_ShouldFilterTerminalStatuses() {
        Flight activeFlight1 = Flight.builder()
                .flightIata("SU-101")
                .departureIata("SVO")
                .arrivalIata("AER")
                .scheduledDepartureTime(Instant.now())
                .scheduledArrivalTime(Instant.now().plusSeconds(14400))
                .status("SCHEDULED")
                .build();

        Flight activeFlight2 = Flight.builder()
                .flightIata("BA-202")
                .departureIata("LHR")
                .arrivalIata("JFK")
                .scheduledDepartureTime(Instant.now())
                .scheduledArrivalTime(Instant.now().plusSeconds(28800))
                .status("DELAYED")
                .delayMinutes(30)
                .build();

        Flight landedFlight = Flight.builder()
                .flightIata("AF-303")
                .departureIata("CDG")
                .arrivalIata("DXB")
                .scheduledDepartureTime(Instant.now().minusSeconds(30000))
                .scheduledArrivalTime(Instant.now().minusSeconds(1000))
                .status("LANDED")
                .build();

        flightRepository.saveAll(List.of(activeFlight1, activeFlight2, landedFlight));

        List<Flight> activeFlights = flightRepository.findByStatusNotIn(List.of("LANDED", "CANCELLED"));

        List<String> activeIatas = activeFlights.stream().map(Flight::getFlightIata).toList();
        assertThat(activeIatas).contains("SU-101", "BA-202");
        assertThat(activeIatas).doesNotContain("AF-303");
    }
}

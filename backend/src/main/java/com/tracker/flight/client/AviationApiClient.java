package com.tracker.flight.client;

import com.tracker.flight.config.RedisConfig;
import com.tracker.flight.dto.ActiveFlightDto;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.external.AviationStackDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class AviationApiClient {

    private static final Logger log = LoggerFactory.getLogger(AviationApiClient.class);
    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public AviationApiClient(
            RestClient.Builder builder,
            @Value("${app.aviation-api.base-url:http://api.aviationstack.com/v1}") String baseUrl,
            @Value("${app.aviation-api.key:}") String apiKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public AviationApiClient(RestClient.Builder builder, String baseUrl) {
        this(builder, baseUrl, "");
    }

    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "#flightIata",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatus")
    public ExternalFlightDto fetchLiveFlight(String flightIata) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API-ключ AviationStack не задан в .env");
        }

        String cleanFlight = flightIata.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        log.info("Запрос расписания AviationStack для рейса {}", cleanFlight);

        // 3 буквы в начале (ANA995, AAL9605) -> ICAO; 2 буквы (NH995, AA9605, SU100) -> IATA
        boolean isIcao = cleanFlight.matches("^[A-Z]{3}\\d+.*");
        String primaryParam = isIcao ? "flight_icao" : "flight_iata";
        String secondaryParam = isIcao ? "flight_iata" : "flight_icao";

        AviationStackDto response = executeQuery(primaryParam, cleanFlight);

        // Если по первичному параметру рейс не найден, проверяем альтернативный
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            log.info("Рейс {} не найден по {}, проверка альтернативного параметра {}", cleanFlight, primaryParam, secondaryParam);
            response = executeQuery(secondaryParam, cleanFlight);
        }

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс " + cleanFlight + " не найден в реестре авиакомпаний");
        }

        return mapToExternalDto(cleanFlight, response.getData().get(0));
    }

    private AviationStackDto executeQuery(String paramName, String flightCode) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/flights")
                            .queryParam("access_key", apiKey)
                            .queryParam(paramName, flightCode)
                            .build())
                    .retrieve()
                    .body(AviationStackDto.class);
        } catch (Exception e) {
            log.error("Ошибка при обращении к AviationStack ({}: {}): {}", paramName, flightCode, e.getMessage());
            return null;
        }
    }

    public List<ActiveFlightDto> fetchActiveFlights() {
        return List.of(
                new ActiveFlightDto("ANA995", "All Nippon Airways", "HND", "Токио", "OKA", "Окинава"),
                new ActiveFlightDto("AAL9605", "American Airlines", "DFW", "Даллас", "MIA", "Майами"),
                new ActiveFlightDto("DAL1251", "Delta Air Lines", "ATL", "Атланта", "JFK", "Нью-Йорк"),
                new ActiveFlightDto("BAW117", "British Airways", "LHR", "Лондон", "JFK", "Нью-Йорк")
        );
    }

    public ExternalFlightDto fallbackGetFlightStatus(String flightIata, Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw rse;
        }
        log.warn("Circuit Breaker активировал fallback для рейса {}: {}", flightIata, throwable.getMessage());
        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .fallbackData(true)
                .build();
    }

    private ExternalFlightDto mapToExternalDto(String flightIata, AviationStackDto.FlightItem item) {
        AviationStackDto.AirportInfo dep = item.getDeparture();
        AviationStackDto.AirportInfo arr = item.getArrival();

        String depIata = dep != null && dep.getIata() != null ? dep.getIata().toUpperCase() : "HND";
        String depAirport = dep != null && dep.getAirport() != null ? dep.getAirport() : "Аэропорт вылета";
        String depCity = extractCity(dep != null ? dep.getTimezone() : null, depAirport);

        String arrIata = arr != null && arr.getIata() != null ? arr.getIata().toUpperCase() : "OKA";
        String arrAirport = arr != null && arr.getAirport() != null ? arr.getAirport() : "Аэропорт прилёта";
        String arrCity = extractCity(arr != null ? arr.getTimezone() : null, arrAirport);

        Instant depSched = parseIso(dep != null ? dep.getScheduled() : null);
        Instant depActual = parseIso(dep != null ? (dep.getActual() != null ? dep.getActual() : dep.getEstimated()) : null);
        Instant arrSched = parseIso(arr != null ? arr.getScheduled() : null);
        Instant arrActual = parseIso(arr != null ? (arr.getActual() != null ? arr.getActual() : arr.getEstimated()) : null);

        int delay = 0;
        if (dep != null && dep.getDelay() != null && dep.getDelay() > 0) {
            delay = dep.getDelay();
        } else if (arr != null && arr.getDelay() != null && arr.getDelay() > 0) {
            delay = arr.getDelay();
        }

        String rawStatus = item.getFlightStatus() != null ? item.getFlightStatus().toLowerCase() : "";
        String status = "SCHEDULED";
        if (rawStatus.contains("land") || rawStatus.contains("arriv")) {
            status = "LANDED";
        } else if (rawStatus.contains("active") || rawStatus.contains("air")) {
            status = delay > 0 ? "DELAYED" : "ON_TIME";
        } else if (rawStatus.contains("cancel")) {
            status = "CANCELLED";
        }

        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .departureAirport(depIata)
                .departureCity(depCity)
                .departureAirportName(depAirport)
                .arrivalAirport(arrIata)
                .arrivalCity(arrCity)
                .arrivalAirportName(arrAirport)
                .scheduledDeparture(depSched != null ? depSched : Instant.now())
                .actualDeparture(depActual)
                .scheduledArrival(arrSched != null ? arrSched : Instant.now().plusSeconds(7200))
                .actualArrival(arrActual)
                .status(status)
                .delayMinutes(delay)
                .fallbackData(false)
                .build();
    }

    private String extractCity(String timezone, String airportName) {
        if (timezone != null && timezone.contains("/")) {
            return timezone.substring(timezone.lastIndexOf('/') + 1).replace('_', ' ');
        }
        return airportName;
    }

    private Instant parseIso(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}

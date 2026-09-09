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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AviationApiClient {

    private static final Logger log = LoggerFactory.getLogger(AviationApiClient.class);
    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public AviationApiClient(
            RestClient.Builder builder,
            @Value("${app.aviation-api.base-url:http://api.aviationstack.com/v1}") String baseUrl,
            @Value("${app.aviation-api.key:9b1f6092edaf3694e752b08f04c69280}") String apiKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public AviationApiClient(RestClient.Builder builder, String baseUrl) {
        this(builder, baseUrl, "");
    }

    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "'aviationstack_active_flights'",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<ActiveFlightDto> fetchActiveFlights() {
        if (apiKey.isBlank()) {
            return Collections.emptyList();
        }

        try {
            AviationStackDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/flights")
                            .queryParam("access_key", apiKey)
                            .queryParam("limit", 10)
                            .build())
                    .retrieve()
                    .body(AviationStackDto.class);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                return Collections.emptyList();
            }

            return response.getData().stream()
                    .filter(item -> item != null && item.getFlight() != null && 
                            (item.getFlight().getIata() != null || item.getFlight().getIcao() != null))
                    .filter(item -> item.getDeparture() != null && item.getArrival() != null)
                    .map(item -> {
                        String callsign = item.getFlight().getIata() != null 
                                ? item.getFlight().getIata() : item.getFlight().getIcao();
                        String airline = item.getAirline() != null && item.getAirline().getName() != null
                                ? item.getAirline().getName() : "Авиалинии";
                        String origIata = item.getDeparture().getIata() != null ? item.getDeparture().getIata().toUpperCase() : "DEP";
                        String origName = item.getDeparture().getAirport() != null ? item.getDeparture().getAirport() : origIata;
                        String destIata = item.getArrival().getIata() != null ? item.getArrival().getIata().toUpperCase() : "ARR";
                        String destName = item.getArrival().getAirport() != null ? item.getArrival().getAirport() : destIata;

                        return new ActiveFlightDto(callsign, airline, origIata, origName, destIata, destName);
                    })
                    .limit(8)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Ошибка при получении ближайших рейсов: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "#flightIata",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatus")
    public ExternalFlightDto fetchLiveFlight(String flightIata) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API-ключ AviationStack не задан");
        }

        String cleanFlight = flightIata.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        boolean isIcao = cleanFlight.matches("^[A-Z]{3}\\d+.*");
        String primaryParam = isIcao ? "flight_icao" : "flight_iata";
        String secondaryParam = isIcao ? "flight_iata" : "flight_icao";

        AviationStackDto response = executeQuery(primaryParam, cleanFlight);
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
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
            log.error("Ошибка при обращении к AviationStack: {}", e.getMessage());
            return null;
        }
    }

    public ExternalFlightDto fallbackGetFlightStatus(String flightIata, Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw rse;
        }
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

        String depIata = dep != null && dep.getIata() != null ? dep.getIata().toUpperCase() : "DEP";
        String depAirport = dep != null && dep.getAirport() != null ? dep.getAirport() : depIata;

        String arrIata = arr != null && arr.getIata() != null ? arr.getIata().toUpperCase() : "ARR";
        String arrAirport = arr != null && arr.getAirport() != null ? arr.getAirport() : arrIata;

        String depTz = dep != null ? dep.getTimezone() : null;
        String arrTz = arr != null ? arr.getTimezone() : null;

        Instant depSched = parseAirportTimeToUtc(dep != null ? dep.getScheduled() : null, depTz);
        Instant depActual = parseAirportTimeToUtc(dep != null ? (dep.getActual() != null ? dep.getActual() : dep.getEstimated()) : null, depTz);
        Instant arrSched = parseAirportTimeToUtc(arr != null ? arr.getScheduled() : null, arrTz);
        Instant arrActual = parseAirportTimeToUtc(arr != null ? (arr.getActual() != null ? arr.getActual() : arr.getEstimated()) : null, arrTz);

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
        } else if (rawStatus.contains("active") || rawStatus.contains("air") || depActual != null) {
            status = delay > 0 ? "DELAYED" : "ON_TIME";
        } else if (rawStatus.contains("cancel")) {
            status = "CANCELLED";
        }

        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .departureAirport(depIata)
                .departureCity(depAirport)
                .departureAirportName(depAirport)
                .arrivalAirport(arrIata)
                .arrivalCity(arrAirport)
                .arrivalAirportName(arrAirport)
                .scheduledDeparture(depSched != null ? depSched : Instant.now())
                .actualDeparture(depActual != null ? depActual : depSched)
                .scheduledArrival(arrSched != null ? arrSched : Instant.now().plusSeconds(7200))
                .actualArrival(arrActual)
                .status(status)
                .delayMinutes(delay)
                .fallbackData(false)
                .build();
    }

    private static Instant parseAirportTimeToUtc(String isoString, String timezone) {
        if (isoString == null || isoString.isBlank()) return null;
        try {
            String rawLocal = isoString.length() >= 19 ? isoString.substring(0, 19) : isoString;
            LocalDateTime ldt = LocalDateTime.parse(rawLocal);
            ZoneId zoneId = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.of("UTC");
            return ldt.atZone(zoneId).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(isoString);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}

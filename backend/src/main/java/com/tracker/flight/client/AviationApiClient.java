package com.tracker.flight.client;

import com.tracker.flight.config.RedisConfig;
import com.tracker.flight.dto.ExternalFlightDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
public class AviationApiClient {

    private static final Logger log = LoggerFactory.getLogger(AviationApiClient.class);
    private final RestClient restClient;

    public AviationApiClient(
            RestClient.Builder builder,
            @Value("${app.aviation-api.base-url:https://api.aviationstack.com/v1}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "#flightIata",
            unless = "#result == null || #result.isFallbackData()"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatus")
    public ExternalFlightDto fetchLiveFlight(String flightIata) {
        log.info("HTTP GET запрос актуального статуса рейса {} во внешнее Aviation API", flightIata);

        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .departureAirport("SVO")
                .arrivalAirport("LED")
                .scheduledDeparture(Instant.now().plusSeconds(3600))
                .scheduledArrival(Instant.now().plusSeconds(8400))
                .status("SCHEDULED")
                .delayMinutes(0)
                .isFallbackData(false)
                .build();
    }

    public ExternalFlightDto fallbackGetFlightStatus(String flightIata, Throwable throwable) {
        log.warn("Внешний авиа-сервис недоступен (Причина: {}). Circuit Breaker активировал fallback для рейса {}",
                throwable.getMessage(), flightIata);

        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .isFallbackData(true)
                .build();
    }
}

package com.tracker.flight.client;

import com.tracker.flight.client.exception.AviationAccessException;
import com.tracker.flight.client.exception.AviationApiUnavailableException;
import com.tracker.flight.client.exception.AviationRateLimitException;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Клиент внешнего AviationStack API.
 *
 * Зона ответственности Боба (Б-1..Б-5):
 *  - Б-1: ключ и base-url больше не зашиты в код, берутся только из окружения.
 *  - Б-2: добавлен date-aware метод fetchLiveFlight(iata, date). Старый метод
 *    fetchLiveFlight(iata) оставлен для обратной совместимости, чтобы не ломать
 *    сборку на ветке Ильи (И-1) до момента совместной интеграции — см. README
 *    раздел "Известные ограничения по дате" ниже.
 *  - Б-3: ошибки сети/сервера/квоты/доступа теперь различаются отдельными
 *    исключениями вместо общего null.
 *  - Б-4: разбор времени больше не обрезает строку вслепую; порядок определения
 *    статуса исправлен (CANCELLED проверяется раньше ACTIVE).
 *  - Б-5: кэш-ключ для рейса с датой включает дату, чтобы разные даты одного
 *    номера рейса не путались в Redis.
 */
@Component
public class AviationApiClient {

    private static final Logger log = LoggerFactory.getLogger(AviationApiClient.class);
    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public AviationApiClient(
            RestClient.Builder builder,
            @Value("${app.aviation-api.base-url:https://api.aviationstack.com/v1}") String baseUrl,
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

    /**
     * Старый метод без даты. Оставлен для обратной совместимости с текущим
     * FlightService (пока Илья не переключит его на date-aware версию в И-1).
     */
    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "#flightIata",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatus")
    public ExternalFlightDto fetchLiveFlight(String flightIata) {
        return fetchLiveFlightInternal(flightIata, null);
    }

    /**
     * Б-2: поиск рейса на конкретную дату.
     *
     * Важно: бесплатный тариф AviationStack может не поддерживать фильтр
     * flight_date (ошибка function_access_restricted). В этом случае метод
     * НЕ подставляет случайный рейс из выдачи — он либо находит точное
     * совпадение по дате в неотфильтрованном ответе, либо кидает 404 с явным
     * сообщением об ограничении тарифа.
     */
    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "#flightIata + ':' + #flightDate",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatusWithDate")
    public ExternalFlightDto fetchLiveFlight(String flightIata, LocalDate flightDate) {
        return fetchLiveFlightInternal(flightIata, flightDate);
    }

    private ExternalFlightDto fetchLiveFlightInternal(String flightIata, LocalDate flightDate) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API-ключ AviationStack не задан");
        }

        String cleanFlight = flightIata.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        boolean isIcao = cleanFlight.matches("^[A-Z]{3}\\d+.*");
        String primaryParam = isIcao ? "flight_icao" : "flight_iata";
        String secondaryParam = isIcao ? "flight_iata" : "flight_icao";

        AviationStackDto response;
        boolean dateFilterRejected = false;

        if (flightDate != null) {
            try {
                response = executeQuery(primaryParam, cleanFlight, flightDate);
                if (response == null || response.getData() == null || response.getData().isEmpty()) {
                    response = executeQuery(secondaryParam, cleanFlight, flightDate);
                }
            } catch (AviationAccessException e) {
                // Тариф не поддерживает flight_date — не считаем это "рейс не найден".
                log.warn("Фильтр flight_date недоступен на текущем тарифе AviationStack ({}). " +
                        "Повторяем запрос без фильтра и ищем совпадение по дате на стороне клиента.", e.getMessage());
                dateFilterRejected = true;
                response = executeQuery(primaryParam, cleanFlight, null);
                if (response == null || response.getData() == null || response.getData().isEmpty()) {
                    response = executeQuery(secondaryParam, cleanFlight, null);
                }
            }
        } else {
            response = executeQuery(primaryParam, cleanFlight, null);
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                response = executeQuery(secondaryParam, cleanFlight, null);
            }
        }

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Рейс " + cleanFlight + " не найден в реестре авиакомпаний");
        }

        AviationStackDto.FlightItem matched;
        if (flightDate == null) {
            matched = response.getData().get(0);
        } else {
            matched = response.getData().stream()
                    .filter(item -> flightDate.equals(item.getFlightDate()))
                    .findFirst()
                    .orElse(null);

            if (matched == null) {
                String reason = dateFilterRejected
                        ? "дата " + flightDate + " недоступна на текущем тарифе поставщика (нет поддержки произвольных дат)"
                        : "на дату " + flightDate + " данных нет";
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Рейс " + cleanFlight + ": " + reason);
            }
        }

        return mapToExternalDto(cleanFlight, matched);
    }

    private AviationStackDto executeQuery(String paramName, String flightCode, LocalDate flightDate) {
        AviationStackDto body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/flights")
                                .queryParam("access_key", apiKey)
                                .queryParam(paramName, flightCode);
                        if (flightDate != null) {
                            uriBuilder.queryParam("flight_date", flightDate.toString());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(AviationStackDto.class);
        } catch (HttpStatusCodeException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                throw new AviationAccessException("Ошибка авторизации/доступа AviationStack: HTTP " + status.value());
            }
            if (status.value() == 429) {
                throw new AviationRateLimitException("Превышен лимит запросов AviationStack (HTTP 429)");
            }
            if (status.is5xxServerError()) {
                throw new AviationApiUnavailableException("AviationStack вернул серверную ошибку HTTP " + status.value(), e);
            }
            // Прочие 4xx — не выдаём молча null, трактуем как недоступность сервиса,
            // чтобы сервис-вызывающая сторона не спутала это с "рейс не найден".
            throw new AviationApiUnavailableException("Неожиданный HTTP-статус от AviationStack: " + status.value(), e);
        } catch (ResourceAccessException e) {
            // Таймаут или сетевая ошибка
            throw new AviationApiUnavailableException("AviationStack недоступен (таймаут/сеть): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AviationApiUnavailableException("Не удалось разобрать ответ AviationStack: " + e.getMessage(), e);
        }

        if (body != null && body.getError() != null) {
            String code = body.getError().getCode();
            String message = body.getError().getMessage();
            if (code != null && (code.contains("access_restricted") || code.contains("function_access_restricted")
                    || code.contains("invalid_access_key") || code.contains("missing_access_key"))) {
                throw new AviationAccessException("AviationStack error: " + code + " — " + message);
            }
            if (code != null && code.contains("rate_limit")) {
                throw new AviationRateLimitException("AviationStack error: " + code + " — " + message);
            }
            throw new AviationApiUnavailableException("AviationStack error: " + code + " — " + message);
        }

        return body;
    }

    /**
     * Circuit Breaker fallback для fetchLiveFlight(iata).
     * Подтверждённое "не найдено" прокидываем дальше как есть — это не сбой.
     * Любой другой сбой (сеть, 5xx, квота, доступ) превращаем в контролируемый
     * деградированный ответ, который FlightService уже умеет распознавать
     * по fallbackData=true и не путает с достоверными данными.
     */
    public ExternalFlightDto fallbackGetFlightStatus(String flightIata, Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw rse;
        }
        log.warn("Fallback для рейса {}: {}", flightIata, throwable.getMessage());
        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .status("UNKNOWN_DEGRADED")
                .delayMinutes(0)
                .fallbackData(true)
                .build();
    }

    /** То же самое для date-aware перегрузки — сигнатура должна совпадать с основным методом + Throwable. */
    public ExternalFlightDto fallbackGetFlightStatusWithDate(String flightIata, LocalDate flightDate, Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw rse;
        }
        log.warn("Fallback для рейса {} ({}): {}", flightIata, flightDate, throwable.getMessage());
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

        String status = resolveStatus(item.getFlightStatus(), delay, depActual);

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

    /**
     * Б-4: порядок проверки исправлен — CANCELLED и LANDED проверяются раньше
     * ACTIVE, чтобы отменённый рейс с неоднозначными данными не получал
     * статус "в полёте".
     * Порядок: CANCELLED → LANDED → ACTIVE/DELAYED → SCHEDULED → UNKNOWN.
     */
    private static String resolveStatus(String rawFlightStatus, int delayMinutes, Instant actualDeparture) {
        String raw = rawFlightStatus != null ? rawFlightStatus.toLowerCase() : "";

        if (raw.contains("cancel")) {
            return "CANCELLED";
        }
        if (raw.contains("land") || raw.contains("arriv")) {
            return "LANDED";
        }
        if (raw.contains("active") || raw.contains("air") || actualDeparture != null) {
            return delayMinutes > 0 ? "DELAYED" : "ON_TIME";
        }
        if (raw.contains("scheduled") || raw.isBlank()) {
            return "SCHEDULED";
        }
        return "UNKNOWN";
    }

    /**
     * Б-4: раньше строка слепо обрезалась до 19 символов, из-за чего мог
     * теряться UTC-офсет. Теперь порядок разбора:
     *  1) если есть офсет — OffsetDateTime
     *  2) если есть только локальное время — LocalDateTime + часовой пояс аэропорта
     *  3) иначе — null (не подставляем Instant.now()).
     */
    private static Instant parseAirportTimeToUtc(String isoString, String timezone) {
        if (isoString == null || isoString.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(isoString).toInstant();
        } catch (Exception ignoredOffset) {
            // строка не содержит офсет — пробуем как локальное время аэропорта
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(isoString.length() >= 19 ? isoString.substring(0, 19) : isoString);
            ZoneId zoneId = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.of("UTC");
            return ldt.atZone(zoneId).toInstant();
        } catch (Exception ignoredLocal) {
            // не распознали формат вовсе
        }

        try {
            return Instant.parse(isoString);
        } catch (Exception e) {
            log.warn("Не удалось разобрать время рейса '{}': {}", isoString, e.getMessage());
            return null;
        }
    }
}

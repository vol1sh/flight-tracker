package com.tracker.flight.client;

import com.tracker.flight.client.exception.AviationAccessException;
import com.tracker.flight.client.exception.AviationApiUnavailableException;
import com.tracker.flight.client.exception.AviationRateLimitException;
import com.tracker.flight.config.RedisConfig;
import com.tracker.flight.dto.ActiveFlightDto;
import com.tracker.flight.dto.ExternalFlightDto;
import com.tracker.flight.dto.external.AviationStackDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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

// Клиент AviationStack API. Зона ответственности Боба: Б-1..Б-5 (ключ из env, дата, ошибки, время/статус, кэш-ключ).
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

    // Без даты, оставлен для совместимости пока Илья не переключит FlightService (И-1).
    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "#flightIata",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatus")
    public ExternalFlightDto fetchLiveFlight(String flightIata) {
        return fetchLiveFlightInternal(flightIata, null);
    }

    // Б-2: поиск по дате. Если тариф не поддерживает flight_date — не подставляем случайный рейс, а ищем точное совпадение сами.
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
            throw new AviationAccessException("API-ключ AviationStack не задан");
        }

        String cleanFlight = flightIata.replaceAll("[^A-Za-z0-9]", "").trim().toUpperCase();
        boolean isIcao = cleanFlight.matches("^[A-Z]{3}\\d+.*");
        String primaryParam = isIcao ? "flight_icao" : "flight_iata";
        String secondaryParam = isIcao ? "flight_iata" : "flight_icao";

        List<AviationStackDto.FlightItem> items;
        boolean dateFilterRejected = false;

        if (flightDate != null) {
            try {
                items = queryBothCodes(primaryParam, secondaryParam, cleanFlight, flightDate);
            } catch (AviationAccessException e) {
                // Тариф не поддерживает flight_date — повторяем без фильтра и ищем дату сами.
                log.warn("Фильтр flight_date недоступен на текущем тарифе AviationStack ({}).", e.getMessage());
                dateFilterRejected = true;
                items = queryBothCodes(primaryParam, secondaryParam, cleanFlight, null);
            }
        } else {
            items = queryBothCodes(primaryParam, secondaryParam, cleanFlight, null);
        }

        // Провайдер успешно ответил, и данных по рейсу нет — это единственный случай 404.
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Рейс " + cleanFlight + " не найден в реестре авиакомпаний");
        }

        AviationStackDto.FlightItem matched;
        if (flightDate == null) {
            matched = items.get(0);
        } else {
            matched = items.stream()
                    .filter(item -> item != null && flightDate.equals(item.getFlightDate()))
                    .findFirst()
                    .orElse(null);

            if (matched == null) {
                if (dateFilterRejected) {
                    // Дату проверить не удалось из-за ограничений тарифа — это не "рейса нет".
                    throw new AviationAccessException("Не удалось проверить рейс " + cleanFlight + " на дату "
                            + flightDate + ": фильтр по дате недоступен на текущем тарифе AviationStack");
                }
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Рейс " + cleanFlight + ": на дату " + flightDate + " данных нет");
            }
        }

        ExternalFlightDto result = mapToExternalDto(cleanFlight, matched);

        log.info("Рейс {}: source=AVIATIONSTACK, status={}, получен новый ответ поставщика",
                cleanFlight, result.getStatus());

        return result;
    }

    // Пробуем основной параметр, затем запасной. Возвращает ПУСТОЙ список только если провайдер
    // успешно ответил и рейсов нет; отсутствующее/битое тело ответа — AviationApiUnavailableException.
    private List<AviationStackDto.FlightItem> queryBothCodes(String primaryParam, String secondaryParam,
                                                             String flightCode, LocalDate flightDate) {
        List<AviationStackDto.FlightItem> items = executeQuery(primaryParam, flightCode, flightDate);
        if (items.isEmpty()) {
            items = executeQuery(secondaryParam, flightCode, flightDate);
        }
        return items;
    }

    private List<AviationStackDto.FlightItem> executeQuery(String paramName, String flightCode, LocalDate flightDate) {
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
            // Б-3: различаем сетевые/серверные ошибки, квоту и доступ вместо общего null.
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
            throw new AviationApiUnavailableException("Неожиданный HTTP-статус от AviationStack: " + status.value(), e);
        } catch (ResourceAccessException e) {
            throw new AviationApiUnavailableException("AviationStack недоступен (таймаут/сеть): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AviationApiUnavailableException("Не удалось разобрать ответ AviationStack: " + e.getMessage(), e);
        }

        if (body == null) {
            throw new AviationApiUnavailableException("AviationStack вернул пустой ответ (нет тела)");
        }

        // AviationStack иногда шлёт HTTP 200 с телом {"error": {...}} — проверяем это отдельно.
        if (body.getError() != null) {
            String code = body.getError().getCode();
            String message = body.getError().getMessage();
            String c = code != null ? code.toLowerCase() : "";
            if (c.contains("usage_limit") || c.contains("rate_limit")) {
                throw new AviationRateLimitException("AviationStack error: " + code + " — " + message);
            }
            if (c.contains("access_restricted") || c.contains("invalid_access_key")
                    || c.contains("missing_access_key") || c.contains("inactive_user")) {
                throw new AviationAccessException("AviationStack error: " + code + " — " + message);
            }
            throw new AviationApiUnavailableException("AviationStack error: " + code + " — " + message);
        }

        // data отсутствует (null) — ответ непригоден; пустой список — провайдер подтвердил "ничего нет".
        if (body.getData() == null) {
            throw new AviationApiUnavailableException("AviationStack вернул ответ без поля data");
        }
        return body.getData();
    }

    // Фолбэк больше НЕ создаёт заглушку: превращает любой сбой в типизированное исключение по контракту.
    public ExternalFlightDto fallbackGetFlightStatus(String flightIata, Throwable throwable) {
        throw toContractException(flightIata, null, throwable);
    }

    // Та же логика для date-aware метода — сигнатура должна совпадать с основным + Throwable.
    public ExternalFlightDto fallbackGetFlightStatusWithDate(String flightIata, LocalDate flightDate, Throwable throwable) {
        throw toContractException(flightIata, flightDate, throwable);
    }

    private RuntimeException toContractException(String flightIata, LocalDate flightDate, Throwable t) {
        if (t instanceof Error err) {
            throw err;
        }
        // Уже классифицированные исключения и подтверждённый 404 сохраняем как есть.
        if (t instanceof AviationApiUnavailableException
                || t instanceof AviationAccessException
                || t instanceof AviationRateLimitException) {
            return (RuntimeException) t;
        }
        if (t instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.NOT_FOUND) {
            return rse;
        }
        String subject = flightDate != null ? flightIata + " (" + flightDate + ")" : flightIata;
        if (t instanceof CallNotPermittedException) {
            log.warn("Circuit Breaker открыт, запрос по рейсу {} не выполнялся", subject);
            return new AviationApiUnavailableException(
                    "AviationStack временно недоступен: Circuit Breaker открыт", t);
        }
        log.warn("Неклассифицированный сбой при запросе рейса {}: {}", subject, t.toString());
        return new AviationApiUnavailableException(
                "Неожиданный сбой при обращении к AviationStack: " + t.getMessage(), t);
    }

    private ExternalFlightDto mapToExternalDto(String flightIata, AviationStackDto.FlightItem item) {
        AviationStackDto.AirportInfo dep = item.getDeparture();
        AviationStackDto.AirportInfo arr = item.getArrival();

        if (dep == null || arr == null) {
            throw new AviationApiUnavailableException("AviationStack вернул рейс " + flightIata + " без данных об аэропортах");
        }

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

        // Не подставляем выдуманное "сейчас": без расписания данные непригодны.
        if (depSched == null || arrSched == null) {
            throw new AviationApiUnavailableException("AviationStack вернул рейс " + flightIata
                    + " без распознаваемого расписания вылета/прилёта");
        }

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
                .scheduledDeparture(depSched)
                .actualDeparture(depActual != null ? depActual : depSched)
                .scheduledArrival(arrSched)
                .actualArrival(arrActual)
                .status(status)
                .delayMinutes(delay)
                .fallbackData(false)
                .build();
    }

    // Б-4: CANCELLED/LANDED проверяются раньше ACTIVE, чтобы отменённый рейс не читался как "в полёте".
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

    // Б-4: сначала пробуем офсет (не теряем UTC-смещение), потом локальное время + таймзону аэропорта, иначе null.
    private static Instant parseAirportTimeToUtc(String isoString, String timezone) {
        if (isoString == null || isoString.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(isoString).toInstant();
        } catch (Exception ignoredOffset) {
            // нет офсета — пробуем локальное время
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(isoString.length() >= 19 ? isoString.substring(0, 19) : isoString);
            ZoneId zoneId = (timezone != null && !timezone.isBlank()) ? ZoneId.of(timezone) : ZoneId.of("UTC");
            return ldt.atZone(zoneId).toInstant();
        } catch (Exception ignoredLocal) {
            // формат не распознан
        }

        try {
            return Instant.parse(isoString);
        } catch (Exception e) {
            log.warn("Не удалось разобрать время рейса '{}': {}", isoString, e.getMessage());
            return null;
        }
    }
}
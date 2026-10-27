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
import java.util.Locale;

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
        if (baseUrl != null && baseUrl.trim().toLowerCase().startsWith("http://")) {
            // Ключ уходит в query-параметре — по HTTP он передаётся открытым текстом.
            log.warn("AviationStack base-url использует HTTP без шифрования ({}); используйте https://", baseUrl);
        }
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
            log.error("Ошибка при получении ближайших рейсов: {}", redact(e.getMessage()));
            return Collections.emptyList();
        }
    }

    // Без даты — используется FlightService.
    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "T(com.tracker.flight.client.AviationApiClient).normalizeFlightCode(#flightIata)",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatus")
    public ExternalFlightDto fetchLiveFlight(String flightIata) {
        return fetchLiveFlightInternal(flightIata, null);
    }

    // Б-2: поиск по дате. Если тариф не поддерживает flight_date — не подставляем случайный рейс, а ищем точное совпадение сами.
    @Cacheable(
            value = RedisConfig.FLIGHTS_CACHE,
            key = "T(com.tracker.flight.client.AviationApiClient).normalizeFlightCode(#flightIata) + ':' + #flightDate",
            unless = "#result == null || #result.fallbackData"
    )
    @CircuitBreaker(name = "aviationApi", fallbackMethod = "fallbackGetFlightStatusWithDate")
    public ExternalFlightDto fetchLiveFlight(String flightIata, LocalDate flightDate) {
        return fetchLiveFlightInternal(flightIata, flightDate);
    }

    /**
     * Единый вид номера рейса для запроса и для ключа кэша: "su-100", "SU 100" и "SU100" — один и тот же рейс.
     * Публичный и статический, потому что используется в SpEL-выражении ключа @Cacheable.
     */
    public static String normalizeFlightCode(String flightIata) {
        return flightIata == null ? "" : flightIata.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private ExternalFlightDto fetchLiveFlightInternal(String flightIata, LocalDate flightDate) {
        if (apiKey.isBlank()) {
            throw new AviationAccessException("API-ключ AviationStack не задан");
        }

        String cleanFlight = normalizeFlightCode(flightIata);
        boolean isIcao = cleanFlight.matches("^[A-Z]{3}\\d+.*");
        String primaryParam = isIcao ? "flight_icao" : "flight_iata";
        String secondaryParam = isIcao ? "flight_iata" : "flight_icao";

        List<AviationStackDto.FlightItem> items;
        boolean dateFilterRejected = false;

        if (flightDate != null) {
            try {
                items = queryBothCodes(primaryParam, secondaryParam, cleanFlight, flightDate);
            } catch (DateFilterNotAllowedException e) {
                // Тариф не поддерживает flight_date — повторяем без фильтра и ищем дату сами.
                // Любой другой AviationAccessException (неверный ключ, 401/403 без этого кода) сюда не попадает.
                log.warn("Фильтр flight_date недоступен на текущем тарифе AviationStack ({}).", e.getMessage());
                dateFilterRejected = true;
                items = queryBothCodes(primaryParam, secondaryParam, cleanFlight, null);
            }
        } else {
            items = queryBothCodes(primaryParam, secondaryParam, cleanFlight, null);
        }

        // Пустой ответ подтверждает отсутствие рейса только если удалось проверить запрошенную дату.
        if (items.isEmpty() && !dateFilterRejected) {
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
            // Сначала смотрим код ошибки в теле (квота может прийти и с 403), потом HTTP-статус.
            HttpStatusCode status = e.getStatusCode();
            String errorCode = extractErrorCode(e.getResponseBodyAsString());
            RuntimeException byCode = classifyErrorCode(errorCode, "HTTP " + status.value(), flightDate != null);
            if (byCode != null) {
                throw byCode;
            }
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                throw accessException(errorCode, "HTTP " + status.value(), flightDate != null);
            }
            if (status.value() == 429) {
                throw new AviationRateLimitException("Превышен лимит запросов AviationStack (HTTP 429)");
            }
            if (status.is5xxServerError()) {
                throw new AviationApiUnavailableException("AviationStack вернул серверную ошибку HTTP " + status.value());
            }
            throw new AviationApiUnavailableException("Неожиданный HTTP-статус от AviationStack: " + status.value()
                    + (errorCode != null ? " (" + errorCode + ")" : ""));
        } catch (ResourceAccessException e) {
            // Сообщение ResourceAccessException содержит полный URL вместе с access_key — его не сохраняем.
            Throwable root = e.getMostSpecificCause();
            String reason = root != e ? root.getClass().getSimpleName() + ": " + redact(root.getMessage()) : "I/O error";
            throw new AviationApiUnavailableException("AviationStack недоступен (таймаут/сеть): " + reason,
                    root != e ? root : null);
        } catch (Exception e) {
            throw new AviationApiUnavailableException("Не удалось разобрать ответ AviationStack: " + redact(e.getMessage()), e);
        }

        if (body == null) {
            throw new AviationApiUnavailableException("AviationStack вернул пустой ответ (нет тела)");
        }

        // AviationStack иногда шлёт HTTP 200 с телом {"error": {...}} — проверяем это отдельно.
        if (body.getError() != null) {
            String code = body.getError().getCode();
            String message = body.getError().getMessage();
            RuntimeException byCode = classifyErrorCode(code, message, flightDate != null);
            if (byCode != null) {
                throw byCode;
            }
            throw new AviationApiUnavailableException("AviationStack error: " + code + " — " + message);
        }

        // data отсутствует (null) — ответ непригоден; пустой список — провайдер подтвердил "ничего нет".
        if (body.getData() == null) {
            throw new AviationApiUnavailableException("AviationStack вернул ответ без поля data");
        }
        return body.getData();
    }

    /**
     * Ограничение именно фильтра flight_date на тарифе (function_access_restricted при запросе с датой).
     * Только для него клиент повторяет запрос без даты; остальные ошибки доступа — обычный AviationAccessException.
     */
    private static final class DateFilterNotAllowedException extends AviationAccessException {
        DateFilterNotAllowedException(String message) {
            super(message);
        }
    }

    /**
     * Классификация по коду ошибки AviationStack (не зависит от HTTP-статуса).
     * null — код не про квоту и не про доступ, решение принимает вызывающий код.
     */
    private static RuntimeException classifyErrorCode(String errorCode, String details, boolean dateQuery) {
        String c = errorCode != null ? errorCode.toLowerCase() : "";
        if (c.contains("usage_limit") || c.contains("rate_limit")) {
            return new AviationRateLimitException("Превышен лимит AviationStack: " + errorCode + " (" + details + ")");
        }
        if (c.contains("access_restricted") || c.contains("invalid_access_key")
                || c.contains("missing_access_key") || c.contains("inactive_user")) {
            return accessException(errorCode, details, dateQuery);
        }
        return null;
    }

    // Б-1: ключ передаётся в URL (access_key=...), поэтому вырезаем его из любых текстов для логов/исключений.
    static String redact(String text) {
        return text == null ? null : text.replaceAll("access_key=[^&\"'\\s]*", "access_key=***");
    }

    private static AviationAccessException accessException(String errorCode, String details, boolean dateQuery) {
        String text = "Ошибка доступа AviationStack: " + errorCode + " (" + details + ")";
        if (dateQuery && errorCode != null && errorCode.toLowerCase().contains("function_access_restricted")) {
            return new DateFilterNotAllowedException(text);
        }
        return new AviationAccessException(text);
    }

    // Достаёт "code" из тела ошибки {"error":{"code":"...","message":"..."}}; null, если тела/кода нет.
    private static String extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"code\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : null;
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
        log.warn("Неклассифицированный сбой при запросе рейса {}: {}", subject, redact(t.toString()));
        return new AviationApiUnavailableException(
                "Неожиданный сбой при обращении к AviationStack: " + redact(t.getMessage()), t);
    }

    private ExternalFlightDto mapToExternalDto(
            String flightIata,
            AviationStackDto.FlightItem item
    ) {
        AviationStackDto.AirportInfo dep = item.getDeparture();
        AviationStackDto.AirportInfo arr = item.getArrival();

        if (dep == null || arr == null) {
            throw new AviationApiUnavailableException(
                    "AviationStack вернул рейс " + flightIata
                            + " без данных об аэропортах"
            );
        }

        String depIata = dep.getIata() == null
                ? "" : dep.getIata().trim().toUpperCase(Locale.ROOT);

        String arrIata = arr.getIata() == null
                ? "" : arr.getIata().trim().toUpperCase(Locale.ROOT);

        if (!depIata.matches("[A-Z]{3}") || !arrIata.matches("[A-Z]{3}")) {
            throw new AviationApiUnavailableException(
                    "AviationStack вернул рейс " + flightIata
                            + " без корректных кодов аэропортов вылета/прилёта"
            );
        }

        String depAirport = dep.getAirport() == null
                || dep.getAirport().isBlank()
                ? null : dep.getAirport();

        String arrAirport = arr.getAirport() == null
                || arr.getAirport().isBlank()
                ? null : arr.getAirport();

        Instant depSched = parseAirportTimeToUtc(
                dep.getScheduled(), dep.getTimezone()
        );

        Instant arrSched = parseAirportTimeToUtc(
                arr.getScheduled(), arr.getTimezone()
        );

        Instant depActual = parseAirportTimeToUtc(
                dep.getActual(), dep.getTimezone()
        );

        Instant arrActual = parseAirportTimeToUtc(
                arr.getActual(), arr.getTimezone()
        );

        if (depSched == null || arrSched == null) {
            throw new AviationApiUnavailableException(
                    "AviationStack вернул рейс " + flightIata
                            + " без распознаваемого расписания вылета/прилёта"
            );
        }

        Integer delay = null;

        if (dep.getDelay() != null && dep.getDelay() > 0) {
            delay = dep.getDelay();
        } else if (arr.getDelay() != null && arr.getDelay() > 0) {
            delay = arr.getDelay();
        } else if (Integer.valueOf(0).equals(dep.getDelay())
                || Integer.valueOf(0).equals(arr.getDelay())) {
            delay = 0;
        }

        return ExternalFlightDto.builder()
                .flightIata(flightIata)
                .departureAirport(depIata)
                .departureCity(null)
                .departureAirportName(depAirport)
                .arrivalAirport(arrIata)
                .arrivalCity(null)
                .arrivalAirportName(arrAirport)
                .scheduledDeparture(depSched)
                .actualDeparture(depActual)
                .scheduledArrival(arrSched)
                .actualArrival(arrActual)
                .status(resolveStatus(item.getFlightStatus(), delay))
                .delayMinutes(delay)
                .fallbackData(false)
                .fetchedAt(Instant.now())
                .build();
    }

    private static String resolveStatus(
            String rawFlightStatus,
            Integer delayMinutes
    ) {
        if (rawFlightStatus == null || rawFlightStatus.isBlank()) {
            return null;
        }

        String raw = rawFlightStatus.trim().toLowerCase(Locale.ROOT);

        if (raw.contains("cancel")) {
            return "CANCELLED";
        }

        if (raw.contains("land") || raw.contains("arriv")) {
            return "LANDED";
        }

        if (raw.contains("active") || raw.contains("air")) {
            if (delayMinutes == null) {
                return "ACTIVE";
            }

            return delayMinutes > 0 ? "DELAYED" : "ON_TIME";
        }

        if (raw.contains("scheduled")) {
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
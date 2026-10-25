package com.tracker.flight.client;

import com.tracker.flight.client.exception.AviationAccessException;
import com.tracker.flight.client.exception.AviationApiUnavailableException;
import com.tracker.flight.client.exception.AviationRateLimitException;
import com.tracker.flight.config.RedisConfig;
import com.tracker.flight.dto.ExternalFlightDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Проверяет контракт ошибок AviationApiClient на реальном HTTP-уровне (MockRestServiceServer)
 * и то, что неудачные запросы не попадают в кэш как успешные рейсы.
 * Circuit Breaker здесь не подключён (нет AOP-аспекта) — проверяется именно исключение клиента.
 */
@SpringJUnitConfig(AviationApiClientContractTest.Config.class)
@DisplayName("Контракт ошибок AviationApiClient (HTTP-уровень + кэш)")
class AviationApiClientContractTest {

    private static final String BASE = "https://aviation.test/v1";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 25);

    private static final String FLIGHT_JSON = """
            {"data":[{"flight_date":"2026-10-25","flight_status":"scheduled",
              "departure":{"airport":"Pulkovo","timezone":"Europe/Moscow","iata":"LED",
                           "scheduled":"2026-10-25T10:00:00+00:00","delay":0},
              "arrival":{"airport":"Sheremetyevo","timezone":"Europe/Moscow","iata":"SVO",
                         "scheduled":"2026-10-25T11:30:00+00:00"},
              "airline":{"name":"Aeroflot"},
              "flight":{"iata":"SU100"}}]}""";

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RedisConfig.FLIGHTS_CACHE);
        }

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer server(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        }

        @Bean
        AviationApiClient client(RestClient.Builder builder, MockRestServiceServer server) {
            return new AviationApiClient(builder, BASE, "test-key");
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AviationApiClient client;
    @org.springframework.beans.factory.annotation.Autowired
    private MockRestServiceServer server;
    @org.springframework.beans.factory.annotation.Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void reset() {
        server.reset();
        cacheManager.getCache(RedisConfig.FLIGHTS_CACHE).clear();
    }

    private void respond(org.springframework.http.HttpStatusCode status, String body) {
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/flights")))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private void respondOk(String body) {
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.startsWith(BASE + "/flights")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private Cache cache() {
        return cacheManager.getCache(RedisConfig.FLIGHTS_CACHE);
    }

    // ---------- успех ----------

    @Test
    @DisplayName("Успех: возвращает реальные данные рейса и кэширует их")
    void success_ShouldReturnRealDataAndCache() {
        respondOk(FLIGHT_JSON);

        ExternalFlightDto dto = client.fetchLiveFlight("SU100", DATE);

        assertThat(dto.isFallbackData()).isFalse();
        assertThat(dto.getDepartureAirport()).isEqualTo("LED");
        assertThat(dto.getArrivalAirport()).isEqualTo("SVO");
        assertThat(cache().get("SU100:" + DATE)).isNotNull();
    }

    // ---------- 404 только при подтверждённом "не найдено" ----------

    @Test
    @DisplayName("Провайдер ответил пустым data=[] -> 404")
    void emptyData_ShouldBe404() {
        respondOk("{\"data\":[]}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cache().get("SU100")).isNull();
    }

    @Test
    @DisplayName("Фильтр даты работает, но на эту дату рейса нет -> 404")
    void dateWithoutMatch_WhenFilterWorks_ShouldBe404() {
        respondOk(FLIGHT_JSON.replace("2026-10-25\",\"flight_status", "2026-10-24\",\"flight_status"));

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- НЕ 404 ----------

    @Test
    @DisplayName("Пустое тело ответа -> AviationApiUnavailableException, не 404")
    void missingBody_ShouldBeUnavailable() {
        respond(HttpStatus.OK, "");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("Ответ без поля data -> AviationApiUnavailableException, не 404")
    void bodyWithoutData_ShouldBeUnavailable() {
        respondOk("{\"pagination\":{\"count\":0}}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("Битый JSON -> AviationApiUnavailableException, не 404")
    void malformedJson_ShouldBeUnavailable() {
        respondOk("{not json");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("Рейс без расписания/аэропортов — непригодные данные -> AviationApiUnavailableException")
    void flightWithoutSchedule_ShouldBeUnavailable() {
        respondOk("{\"data\":[{\"flight_date\":\"2026-10-25\",\"flight_status\":\"scheduled\","
                + "\"departure\":{\"iata\":\"LED\"},\"arrival\":{\"iata\":\"SVO\"},\"flight\":{\"iata\":\"SU100\"}}]}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("HTTP 5xx -> AviationApiUnavailableException")
    void http5xx_ShouldBeUnavailable() {
        respond(HttpStatus.BAD_GATEWAY, "{}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("HTTP 401/403 -> AviationAccessException")
    void http401_403_ShouldBeAccess() {
        respond(HttpStatus.UNAUTHORIZED, "{}");
        assertThatThrownBy(() -> client.fetchLiveFlight("SU100")).isInstanceOf(AviationAccessException.class);

        server.reset();
        respond(HttpStatus.FORBIDDEN, "{}");
        assertThatThrownBy(() -> client.fetchLiveFlight("SU101")).isInstanceOf(AviationAccessException.class);
    }

    @Test
    @DisplayName("HTTP 429 -> AviationRateLimitException")
    void http429_ShouldBeRateLimit() {
        respond(HttpStatus.TOO_MANY_REQUESTS, "{}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationRateLimitException.class);
    }

    @Test
    @DisplayName("HTTP 200 с телом error: invalid_access_key -> AviationAccessException")
    void errorBodyInvalidKey_ShouldBeAccess() {
        respondOk("{\"error\":{\"code\":\"invalid_access_key\",\"message\":\"bad key\"}}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationAccessException.class);
    }

    @Test
    @DisplayName("HTTP 200 с телом error: usage_limit_reached -> AviationRateLimitException")
    void errorBodyUsageLimit_ShouldBeRateLimit() {
        respondOk("{\"error\":{\"code\":\"usage_limit_reached\",\"message\":\"quota\"}}");

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationRateLimitException.class);
    }

    @Test
    @DisplayName("Дату проверить нельзя из-за тарифа и совпадения нет -> AviationAccessException, не 404")
    void dateCheckBlockedByPlan_ShouldBeAccessNot404() {
        // Запрос с flight_date отклоняется тарифом, запрос без даты возвращает рейс за ДРУГУЮ дату.
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.containsString("flight_date=")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"function_access_restricted\"}}"));
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("flight_date="))))
                .andRespond(withSuccess(
                        FLIGHT_JSON.replace("2026-10-25\",\"flight_status", "2026-10-24\",\"flight_status"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE))
                .isInstanceOf(AviationAccessException.class);
    }

    @Test
    @DisplayName("Дата недоступна по тарифу, но нужная дата есть в полном ответе -> данные возвращаются")
    void dateCheckBlockedByPlan_ButMatchFound_ShouldReturnFlight() {
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.containsString("flight_date=")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"function_access_restricted\"}}"));
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("flight_date="))))
                .andRespond(withSuccess(FLIGHT_JSON, MediaType.APPLICATION_JSON));

        ExternalFlightDto dto = client.fetchLiveFlight("SU100", DATE);

        assertThat(dto.isFallbackData()).isFalse();
        assertThat(dto.getDepartureAirport()).isEqualTo("LED");
    }

    @Test
    @DisplayName("С датой: 401 invalid_access_key — это НЕ запрет фильтра даты, повтора без даты нет")
    void dateQuery_Http401InvalidKey_ShouldNotRetryWithoutDate() {
        server.expect(once(), requestTo(startsWith(BASE + "/flights")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"invalid_access_key\",\"message\":\"bad key\"}}"));

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE))
                .isInstanceOf(AviationAccessException.class);
        server.verify(); // ровно один запрос
    }

    @Test
    @DisplayName("С датой: 403 без кода function_access_restricted — повтора без даты нет")
    void dateQuery_Http403WithoutRestrictionCode_ShouldNotRetryWithoutDate() {
        server.expect(once(), requestTo(startsWith(BASE + "/flights")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(""));

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE))
                .isInstanceOf(AviationAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("С датой: HTTP 200 + error invalid_access_key — повтора без даты нет")
    void dateQuery_ErrorBodyInvalidKey_ShouldNotRetryWithoutDate() {
        server.expect(once(), requestTo(startsWith(BASE + "/flights")))
                .andRespond(withSuccess("{\"error\":{\"code\":\"invalid_access_key\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE))
                .isInstanceOf(AviationAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("Без даты: function_access_restricted — обычный AviationAccessException, без повтора")
    void noDateQuery_FunctionRestricted_ShouldBePlainAccessError() {
        server.expect(once(), requestTo(startsWith(BASE + "/flights")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"function_access_restricted\"}}"));

        assertThatThrownBy(() -> client.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("Ключ не задан -> AviationAccessException")
    void missingApiKey_ShouldBeAccess() {
        AviationApiClient noKey = new AviationApiClient(RestClient.builder(), BASE, "  ");

        assertThatThrownBy(() -> noKey.fetchLiveFlight("SU100"))
                .isInstanceOf(AviationAccessException.class);
        assertThatThrownBy(() -> noKey.fetchLiveFlight("SU100", DATE))
                .isInstanceOf(AviationAccessException.class);
    }

    // ---------- кэш ----------

    @Test
    @DisplayName("Неудачные запросы (5xx / 429 / 404) не кэшируются ни в одной перегрузке")
    void failedLookups_ShouldNotBeCached() {
        respond(HttpStatus.BAD_GATEWAY, "{}");
        assertThatThrownBy(() -> client.fetchLiveFlight("SU100")).isInstanceOf(AviationApiUnavailableException.class);
        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE)).isInstanceOf(AviationApiUnavailableException.class);
        assertThat(cache().get("SU100")).isNull();
        assertThat(cache().get("SU100:" + DATE)).isNull();

        server.reset();
        respond(HttpStatus.TOO_MANY_REQUESTS, "{}");
        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE)).isInstanceOf(AviationRateLimitException.class);
        assertThat(cache().get("SU100:" + DATE)).isNull();

        server.reset();
        respondOk("{\"data\":[]}");
        assertThatThrownBy(() -> client.fetchLiveFlight("SU100")).isInstanceOf(ResponseStatusException.class);
        assertThat(cache().get("SU100")).isNull();
    }

    @Test
    @DisplayName("После неудачи следующий успешный запрос идёт к провайдеру и только тогда кэшируется")
    void afterFailure_NextSuccessIsFetchedAndCached() {
        respond(HttpStatus.BAD_GATEWAY, "{}");
        assertThatThrownBy(() -> client.fetchLiveFlight("SU100", DATE)).isInstanceOf(AviationApiUnavailableException.class);

        server.reset();
        respondOk(FLIGHT_JSON);
        ExternalFlightDto dto = client.fetchLiveFlight("SU100", DATE);

        assertThat(dto.isFallbackData()).isFalse();
        assertThat(cache().get("SU100:" + DATE)).isNotNull();
    }
}

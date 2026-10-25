package com.tracker.flight.client;

import com.tracker.flight.client.exception.AviationAccessException;
import com.tracker.flight.client.exception.AviationApiUnavailableException;
import com.tracker.flight.client.exception.AviationRateLimitException;
import com.tracker.flight.dto.ExternalFlightDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Контракт ошибок fallback-методов и Circuit Breaker (Resilience4j)")
class AviationApiClientResilienceTest {

    private static final String FLIGHT = "SU100";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 25);

    private CircuitBreaker circuitBreaker;
    private AviationApiClient client;

    @BeforeEach
    void setUp() {
        // Конфигурация, аналогичная application.yml: 50% ошибок из 10 вызовов (минимум 5) переводят в OPEN
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50.0f)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("aviationApi");

        client = new AviationApiClient(RestClient.builder(), "https://api.aviationstack.com/v1");
    }

    // ---------- fallbackGetFlightStatus (без даты) ----------

    @Test
    @DisplayName("Неклассифицированный сбой превращается в AviationApiUnavailableException, а не в заглушку")
    void fallback_UnknownFailure_ShouldThrowUnavailable() {
        Throwable cause = new RuntimeException("503 Service Unavailable: Remote server down");

        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, cause))
                .isInstanceOf(AviationApiUnavailableException.class)
                .hasCause(cause);
    }

    @Test
    @DisplayName("Подтверждённый 404 пробрасывается как есть")
    void fallback_NotFound_ShouldRethrowSame404() {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "нет рейса");

        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, notFound))
                .isSameAs(notFound);
    }

    @Test
    @DisplayName("Не-404 ResponseStatusException не маскируется под 404 — это недоступность")
    void fallback_OtherResponseStatus_ShouldBecomeUnavailable() {
        ResponseStatusException other = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "bad gateway");

        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, other))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("Уже классифицированные исключения сохраняются без обёртки")
    void fallback_ClassifiedExceptions_ShouldBePreserved() {
        AviationApiUnavailableException unavailable = new AviationApiUnavailableException("down");
        AviationAccessException access = new AviationAccessException("403");
        AviationRateLimitException rateLimit = new AviationRateLimitException("429");

        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, unavailable)).isSameAs(unavailable);
        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, access)).isSameAs(access);
        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, rateLimit)).isSameAs(rateLimit);
    }

    @Test
    @DisplayName("Блокировка Circuit Breaker сообщается как AviationApiUnavailableException")
    void fallback_CallNotPermitted_ShouldThrowUnavailable() {
        CallNotPermittedException blocked = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        assertThatThrownBy(() -> client.fallbackGetFlightStatus(FLIGHT, blocked))
                .isInstanceOf(AviationApiUnavailableException.class)
                .hasCause(blocked);
    }

    // ---------- fallbackGetFlightStatusWithDate (с датой) ----------

    @Test
    @DisplayName("С датой: неклассифицированный сбой -> AviationApiUnavailableException")
    void fallbackWithDate_UnknownFailure_ShouldThrowUnavailable() {
        assertThatThrownBy(() -> client.fallbackGetFlightStatusWithDate(FLIGHT, DATE, new RuntimeException("boom")))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("С датой: подтверждённый 404 пробрасывается как есть")
    void fallbackWithDate_NotFound_ShouldRethrowSame404() {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "нет рейса");

        assertThatThrownBy(() -> client.fallbackGetFlightStatusWithDate(FLIGHT, DATE, notFound))
                .isSameAs(notFound);
    }

    @Test
    @DisplayName("С датой: классифицированные исключения сохраняются")
    void fallbackWithDate_ClassifiedExceptions_ShouldBePreserved() {
        AviationAccessException access = new AviationAccessException("plan");
        AviationRateLimitException rateLimit = new AviationRateLimitException("429");

        assertThatThrownBy(() -> client.fallbackGetFlightStatusWithDate(FLIGHT, DATE, access)).isSameAs(access);
        assertThatThrownBy(() -> client.fallbackGetFlightStatusWithDate(FLIGHT, DATE, rateLimit)).isSameAs(rateLimit);
    }

    @Test
    @DisplayName("С датой: блокировка Circuit Breaker -> AviationApiUnavailableException")
    void fallbackWithDate_CallNotPermitted_ShouldThrowUnavailable() {
        CallNotPermittedException blocked = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

        assertThatThrownBy(() -> client.fallbackGetFlightStatusWithDate(FLIGHT, DATE, blocked))
                .isInstanceOf(AviationApiUnavailableException.class);
    }

    @Test
    @DisplayName("Ни один fallback не возвращает DTO: результат — только исключение")
    void fallbacks_NeverReturnDto() {
        for (Throwable t : new Throwable[]{
                new RuntimeException("x"),
                new AviationApiUnavailableException("u"),
                new AviationAccessException("a"),
                new AviationRateLimitException("r"),
                new ResponseStatusException(HttpStatus.NOT_FOUND)}) {
            ExternalFlightDto[] result = new ExternalFlightDto[1];
            assertThatThrownBy(() -> result[0] = client.fallbackGetFlightStatus(FLIGHT, t))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> result[0] = client.fallbackGetFlightStatusWithDate(FLIGHT, DATE, t))
                    .isInstanceOf(RuntimeException.class);
            assertThat(result[0]).isNull();
        }
    }

    // ---------- Circuit Breaker ----------

    @Test
    @DisplayName("Circuit Breaker размыкает цепь (переходит в состояние OPEN) при превышении порога ошибок")
    void circuitBreaker_WhenFailureThresholdExceeded_ShouldTransitionToOpen() {
        // Исходное состояние цепи — CLOSED (запросы пропускаются)
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        Supplier<String> failingCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            throw new RuntimeException("External Aviation API 500 Internal Error");
        });

        // Выполняем 5 ошибочных вызовов (порог minimumNumberOfCalls=5, failureRate=100%)
        for (int i = 0; i < 5; i++) {
            try {
                failingCall.get();
            } catch (Exception ignored) {
                // Игнорируем исключение в тестовом цикле
            }
        }

        // Проверяем, что Circuit Breaker перешел в состояние OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 6-й вызов мгновенно блокируется самим Circuit Breaker без обращения к внешнему сервису
        Supplier<String> nextCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> "SUCCESS");

        assertThatThrownBy(nextCall::get)
                .isInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("CircuitBreaker 'aviationApi' is OPEN");
    }
}

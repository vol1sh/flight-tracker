package com.tracker.flight.client;

import com.tracker.flight.dto.ExternalFlightDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Тестирование отказоустойчивости и Circuit Breaker (Resilience4j)")
class AviationApiClientResilienceTest {

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

    @Test
    @DisplayName("Fallback-метод возвращает объект с флагом isFallbackData=true при сбое внешнего API")
    void fallbackGetFlightStatus_ShouldReturnDegradedResponse() {
        // Arrange
        String flightIata = "SU-100";
        Throwable apiException = new RuntimeException("503 Service Unavailable: Remote server down");

        // Act
        ExternalFlightDto fallbackResult = client.fallbackGetFlightStatus(flightIata, apiException);

        // Assert
        assertThat(fallbackResult).isNotNull();
        assertThat(fallbackResult.getFlightIata()).isEqualTo(flightIata);
        assertThat(fallbackResult.isFallbackData()).isTrue();
        assertThat(fallbackResult.getStatus()).isEqualTo("UNKNOWN_DEGRADED");
    }

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

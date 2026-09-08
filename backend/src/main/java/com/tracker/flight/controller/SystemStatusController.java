package com.tracker.flight.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System Operations", description = "Эндпоинты проверки жизнеспособности и метаданных сервиса")
public class SystemStatusController {

    @GetMapping("/ping")
    @Operation(summary = "Проверка доступности сервиса", description = "Возвращает текущий статус сервера и UTC-метку времени")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "flight-delay-tracker",
                "timestamp", Instant.now().toString()
        ));
    }
}

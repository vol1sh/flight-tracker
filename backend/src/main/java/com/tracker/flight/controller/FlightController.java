package com.tracker.flight.controller;

import com.tracker.flight.dto.ActiveFlightDto;
import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.dto.FlightStatusLogDto;
import com.tracker.flight.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/flights")
@Tag(name = "Flight Operations", description = "Управление рейсами, мониторинг задержек и аудит истории изменений")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @GetMapping("/active")
    @Operation(summary = "Получить список активных рейсов в воздухе прямо сейчас")
    public ResponseEntity<List<ActiveFlightDto>> getActiveFlights() {
        return ResponseEntity.ok(flightService.getActiveFlights());
    }

    @GetMapping("/{flightIata}")
    @Operation(summary = "Получить актуальный статус рейса")
    public ResponseEntity<FlightResponseDto> getFlightStatus(@PathVariable String flightIata) {
        return ResponseEntity.ok(flightService.syncAndGetFlight(flightIata.toUpperCase()));
    }

    @GetMapping("/{flightIata}/history")
    @Operation(summary = "Получить историю аудита изменений рейса")
    public ResponseEntity<List<FlightStatusLogDto>> getFlightHistory(@PathVariable String flightIata) {
        return ResponseEntity.ok(flightService.getFlightHistory(flightIata.toUpperCase()));
    }
}

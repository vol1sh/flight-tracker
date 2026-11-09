package com.tracker.flight.controller;

import com.tracker.flight.dto.AnalyticsResponseDto;
import com.tracker.flight.dto.FlightExecutionDto;
import org.springframework.data.domain.Page;
import com.tracker.flight.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/flights/{flightIata}")
    public ResponseEntity<AnalyticsResponseDto> getFlightAnalytics(
            @PathVariable String flightIata,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getFlightAnalytics(flightIata, from, to));
    }

    @GetMapping("/routes")
    public ResponseEntity<AnalyticsResponseDto> getRouteAnalytics(
            @RequestParam String departure,
            @RequestParam String arrival,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.getRouteAnalytics(departure, arrival, from, to));
    }

    @GetMapping("/executions")
    public Page<FlightExecutionDto> getExecutions(
            @RequestParam(required = false) String flightIata,
            @RequestParam(required = false) String departure,
            @RequestParam(required = false) String arrival,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return analyticsService.getExecutions(flightIata, departure, arrival, from, to, page, size);
    }
}

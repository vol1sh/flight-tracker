package com.tracker.flight.controller;

import com.tracker.flight.dto.FlightResponseDto;
import com.tracker.flight.dto.FlightStatusLogDto;
import com.tracker.flight.service.FlightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @GetMapping("/{flightIata}")
    @Operation(
            summary = "Получить актуальный статус рейса",
            description = "Возвращает данные о рейсе с задержкой. Ответ кэшируется в Redis на 3 мин и защищен Circuit Breaker.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Данные о рейсе успешно получены",
                            content = @Content(schema = @Schema(implementation = FlightResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
            }
    )
    public ResponseEntity<FlightResponseDto> getFlightStatus(
            @Parameter(description = "IATA-номер рейса", example = "SU-100")
            @PathVariable String flightIata
    ) {
        return ResponseEntity.ok(flightService.syncAndGetFlight(flightIata.toUpperCase()));
    }

    @GetMapping("/{flightIata}/history")
    @Operation(
            summary = "Получить историю аудита изменений рейса",
            description = "Возвращает временной ряд изменений статусов и задержек для предиктивной аналитики",
            responses = {
                    @ApiResponse(responseCode = "200", description = "История аудита получена",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FlightStatusLogDto.class))))
            }
    )
    public ResponseEntity<List<FlightStatusLogDto>> getFlightHistory(
            @Parameter(description = "IATA-номер рейса", example = "SU-100")
            @PathVariable String flightIata
    ) {
        return ResponseEntity.ok(flightService.getFlightHistory(flightIata.toUpperCase()));
    }
}

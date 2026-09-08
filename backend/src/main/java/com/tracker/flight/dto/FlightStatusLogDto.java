package com.tracker.flight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Запись аудита изменения статуса рейса или задержки")
public class FlightStatusLogDto {

    @Schema(description = "Предыдущий статус рейса", example = "SCHEDULED")
    private String previousStatus;

    @Schema(description = "Новый зафиксированный статус рейса", example = "DELAYED")
    private String newStatus;

    @Schema(description = "Зафиксированная величина задержки (в минутах)", example = "45")
    private Integer delayMinutes;

    @Schema(description = "Метка времени фиксации изменения (UTC)")
    private Instant recordedAt;

    public FlightStatusLogDto() {}

    public FlightStatusLogDto(String previousStatus, String newStatus, Integer delayMinutes, Instant recordedAt) {
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.delayMinutes = delayMinutes;
        this.recordedAt = recordedAt;
    }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public Integer getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}

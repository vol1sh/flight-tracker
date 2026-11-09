package com.tracker.flight.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateEmailSubscriptionRequest(
        @NotBlank @Size(max = 12) String flightIata,
        @NotNull LocalDate flightDate,
        @NotBlank @Email @Size(max = 320) String email) {}

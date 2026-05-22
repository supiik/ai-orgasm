package com.pi2.anchor.backend.sample;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record CreateSampleRequest(
        @NotBlank String name,
        String description,
        @Email String email,
        @Min(0) @Max(10_000) int quantity,
        long largeNumber,
        @Min(0) @Max(10) double rating,
        BigDecimal price,
        boolean active,
        LocalDate birthDate,
        LocalDateTime scheduledAt,
        @NotNull SampleStatus status,
        String notes
) {}

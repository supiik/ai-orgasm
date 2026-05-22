package com.pi2.anchor.backend.sample;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record UpdateSampleRequest(
        @NotBlank String name,
        String description,
        @Email String email,
        @Min(0) @Max(10_000) Integer quantity,
        Long largeNumber,
        @Min(0) @Max(10) Double rating,
        BigDecimal price,
        Boolean active,
        LocalDate birthDate,
        LocalDateTime scheduledAt,
        SampleStatus status,
        String notes
) {}

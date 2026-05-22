package com.pi2.anchor.backend.sample;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record SampleResponse(
        String id,
        String name,
        String description,
        String email,
        int quantity,
        long largeNumber,
        double rating,
        BigDecimal price,
        boolean active,
        LocalDate birthDate,
        LocalDateTime scheduledAt,
        SampleStatus status,
        String notes,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

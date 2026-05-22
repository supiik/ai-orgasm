package com.pi2.anchor.backend.sample;

import com.pi2.anchor.backend.domain.AuditableEntity;
import com.pi2.anchor.backend.domain.IdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "samples")
@SQLRestriction("deleted_at IS NULL")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sample extends AuditableEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @PrePersist
    void assignId() {
        if (id == null) id = IdGenerator.generate();
    }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "email")
    private String email;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "large_number", nullable = false)
    private long largeNumber;

    @Column(name = "rating", nullable = false)
    private double rating;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SampleStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}

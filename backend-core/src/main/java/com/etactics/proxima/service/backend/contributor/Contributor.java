package com.etactics.proxima.service.backend.contributor;

import com.etactics.proxima.service.backend.domain.AuditableEntity;
import com.etactics.proxima.service.backend.domain.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "contributors")
@SQLRestriction("deleted_at IS NULL")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class Contributor extends AuditableEntity {

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

    @Column(name = "email")
    private String email;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;
}

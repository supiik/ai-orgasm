package com.orgasm.backend.contributor;

import com.orgasm.backend.domain.AuditableEntity;
import com.orgasm.backend.domain.TsidGenerator;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "contributors")
@SQLRestriction("deleted_at IS NULL")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class Contributor extends AuditableEntity {

    @Id
    @Column(name = "id", length = 25)
    private String id;

    @PrePersist
    void assignId() {
        if (id == null) id = TsidGenerator.generate("cont");
    }

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;
}

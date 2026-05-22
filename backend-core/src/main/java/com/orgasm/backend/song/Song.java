package com.orgasm.backend.song;

import com.orgasm.backend.domain.AuditableEntity;
import com.orgasm.backend.domain.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "songs")
@SQLRestriction("deleted_at IS NULL")
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class Song extends AuditableEntity {

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

    @Column(name = "artist", nullable = false)
    private String artist;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "album")
    private String album;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "url", length = 2048)
    private String url;
}

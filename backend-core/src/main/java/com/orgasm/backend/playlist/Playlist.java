package com.orgasm.backend.playlist;

import com.orgasm.backend.domain.AuditableEntity;
import com.orgasm.backend.domain.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "playlists")
@SQLRestriction("deleted_at IS NULL")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class Playlist extends AuditableEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @PrePersist
    void assignId() {
        if (id == null) id = IdGenerator.generate();
    }

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlaylistStatus status;

    @Column(name = "lead_contributor_id")
    private Long leadContributorId;

    @Column(name = "deadline")
    private Instant deadline;
}

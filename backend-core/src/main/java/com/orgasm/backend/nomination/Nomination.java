package com.orgasm.backend.nomination;

import com.orgasm.backend.domain.AuditableEntity;
import com.orgasm.backend.domain.IdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "nominations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"playlist_id", "song_id"}))
@SQLRestriction("deleted_at IS NULL")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Nomination extends AuditableEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @PrePersist
    void assignId() {
        if (id == null) id = IdGenerator.generate();
        if (status == null) status = NominationStatus.PENDING;
    }

    @Column(name = "playlist_id", nullable = false, updatable = false)
    private Long playlistId;

    @Column(name = "song_id", nullable = false, updatable = false)
    private Long songId;

    @Column(name = "nominated_by_id", nullable = false, updatable = false)
    private Long nominatedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NominationStatus status;
}

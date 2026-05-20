package com.etactics.proxima.service.backend.nomination;

import com.etactics.proxima.service.backend.contributor.Contributor;
import com.etactics.proxima.service.backend.domain.AuditableEntity;
import com.etactics.proxima.service.backend.domain.IdGenerator;
import com.etactics.proxima.service.backend.playlist.Playlist;
import com.etactics.proxima.service.backend.song.Song;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

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

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false, updatable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false, updatable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nominated_by_id", nullable = false, updatable = false)
    private Contributor nominatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NominationStatus status;
}

package com.orgasm.backend.ranking;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.AuditableEntity;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.playlist.Playlist;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "playlist_ranking",
        uniqueConstraints = @UniqueConstraint(columnNames = {"playlist_id", "contributor_id"}))
@SQLRestriction("deleted_at IS NULL")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaylistRanking extends AuditableEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false, updatable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contributor_id", nullable = false, updatable = false)
    private Contributor contributor;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "correct_guesses", nullable = false)
    private int correctGuesses;

    @Column(name = "total_guesses", nullable = false)
    private int totalGuesses;
}

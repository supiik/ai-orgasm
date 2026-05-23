package com.orgasm.backend.guessing;

import com.orgasm.backend.contributor.Contributor;
import com.orgasm.backend.domain.AuditableEntity;
import com.orgasm.backend.domain.IdGenerator;
import com.orgasm.backend.nomination.Nomination;
import com.orgasm.backend.playlist.Playlist;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "guesses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nomination_id", "guesser_contributor_id"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Guess extends AuditableEntity {

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
    @JoinColumn(name = "nomination_id", nullable = false, updatable = false)
    private Nomination nomination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guesser_contributor_id", nullable = false, updatable = false)
    private Contributor guesser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guessed_contributor_id", nullable = false, updatable = false)
    private Contributor guessedContributor;
}

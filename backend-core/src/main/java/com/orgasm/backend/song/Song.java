package com.orgasm.backend.song;

import com.orgasm.backend.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.SQLRestriction;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist", nullable = false)
    private String artist;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "album")
    private String album;

    @Column(name = "release_year")
    private Integer releaseYear;
}

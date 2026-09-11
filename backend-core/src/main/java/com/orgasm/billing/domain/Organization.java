package com.orgasm.billing.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organization")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Organization {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * When set, registration/linking requires the contributor's email to end in
     * {@code @<allowedDomain>}. Null means unrestricted (any email may join). Never exposed via
     * the public {@code GET /api/v1/organizations} listing (which serializes this entity
     * directly) — it's a server-side registration gate, not part of the public contract.
     */
    @JsonIgnore
    @Column(name = "allowed_domain", length = 255)
    private String allowedDomain;
}

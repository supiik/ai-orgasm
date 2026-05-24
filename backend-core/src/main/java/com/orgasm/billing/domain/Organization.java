package com.orgasm.billing.domain;

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
}

package com.orgasm.backend.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditableEntityTest {

    static class TestEntity extends AuditableEntity {}

    @Test
    void markDeleted_setsDeletedAtTimestamp() {
        TestEntity entity = new TestEntity();
        Instant before = Instant.now();

        entity.markDeleted();

        assertThat(entity.getDeletedAt())
                .isNotNull()
                .isAfterOrEqualTo(before);
    }
}

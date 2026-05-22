package com.pi2.anchor.backend.controller;

import com.pi2.anchor.sdk.model.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private HealthController controller = new HealthController();

    @Test
    void healthReturnsUp() {
        ApiResponse<Map<String, String>> response = controller.health();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("status", "UP");
    }
}

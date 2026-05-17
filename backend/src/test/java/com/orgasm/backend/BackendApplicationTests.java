package com.orgasm.backend;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void main_delegatesToSpringApplicationRun() {
        String[] args = {"--server.port=0"};

        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            BackendApplication.main(args);
            mocked.verify(() -> SpringApplication.run(BackendApplication.class, args));
        }
    }
}

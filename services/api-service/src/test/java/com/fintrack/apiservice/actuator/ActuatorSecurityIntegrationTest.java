package com.fintrack.apiservice.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void livenessIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessIsPublicWhenDatabaseIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void metricsRejectsRegularUser() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void metricsAllowsAdministrator() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void unexposedActuatorEndpointRemainsUnavailable() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
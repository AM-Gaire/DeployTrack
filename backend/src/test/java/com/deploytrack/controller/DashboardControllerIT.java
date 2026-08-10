package com.deploytrack.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deploytrack.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "deploytrack.simulator.enabled=false")
class DashboardControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // Dashboard stats are global by design -- an ops view covering every
        // project. That makes this suite the one place where data left behind
        // by other test classes changes the answer, since all of them share a
        // single container. Starting from a known-empty state is what lets
        // these assertions name exact numbers instead of vague ranges.
        //
        // Child tables first: the foreign keys would reject any other order.
        jdbcTemplate.execute("TRUNCATE logs, deployments, projects RESTART IDENTITY CASCADE");

        token = registerAndLogin("dash-" + unique(), "dash-" + unique() + "@test.local");
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsEveryStatusBucketEvenWhenEmpty() throws Exception {
        // A missing key would force the frontend to guess whether it means
        // "none" or "the backend forgot".
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentsByStatus.IN_PROGRESS").exists())
            .andExpect(jsonPath("$.deploymentsByStatus.SUCCESS").exists())
            .andExpect(jsonPath("$.deploymentsByStatus.FAILED").exists());
    }

    @Test
    void countsDeploymentsByStatus() throws Exception {
        long projectId = createProject("dash-counts-" + unique());
        long succeeded = trigger(projectId, "1.0.0", "production");
        complete(succeeded, "SUCCESS");
        long failed = trigger(projectId, "1.0.1", "staging");
        complete(failed, "FAILED");
        trigger(projectId, "1.0.2", "dev");

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDeployments").value(3))
            .andExpect(jsonPath("$.deploymentsByStatus.SUCCESS").value(1))
            .andExpect(jsonPath("$.deploymentsByStatus.FAILED").value(1))
            .andExpect(jsonPath("$.deploymentsByStatus.IN_PROGRESS").value(1));
    }

    @Test
    void successRateExcludesInProgressDeployments() throws Exception {
        long projectId = createProject("dash-rate-" + unique());
        complete(trigger(projectId, "1.0.0", "production"), "SUCCESS");
        complete(trigger(projectId, "1.0.1", "staging"), "FAILED");
        // Still running: must not drag the rate down to 33%.
        trigger(projectId, "1.0.2", "dev");

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successRatePercent").value(50.0));
    }

    @Test
    void successRateIsNullWhenNothingHasSettled() throws Exception {
        // Zero would read as "everything is failing", which is a different
        // and much more alarming claim than "nothing has finished yet".
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successRatePercent").doesNotExist());
    }

    @Test
    void includesRecentDeploymentsNewestFirst() throws Exception {
        long projectId = createProject("dash-recent-" + unique());
        complete(trigger(projectId, "1.0.0", "dev"), "SUCCESS");
        trigger(projectId, "2.0.0", "dev");

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentDeployments[0].version").value("2.0.0"));
    }

    @Test
    void countsRecentActivityWindows() throws Exception {
        long projectId = createProject("dash-window-" + unique());
        trigger(projectId, "1.0.0", "production");

        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentsLast24Hours").isNumber())
            .andExpect(jsonPath("$.deploymentsLast7Days").isNumber());
    }

    private long createProject(String name) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"dashboard fixture\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private long trigger(long projectId, String version, String environment) throws Exception {
        String response = mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"" + version + "\",\"environment\":\"" + environment + "\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void complete(long deploymentId, String status) throws Exception {
        mockMvc.perform(patch("/api/deployments/" + deploymentId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"))
            .andExpect(status().isOk());
    }

    private String registerAndLogin(String username, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"password\":\"test-password-123\"}"));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"test-password-123\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}

package com.deploytrack.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// The simulator is switched off here. Left on, a background thread would race
// every assertion -- a deployment asserted to be IN_PROGRESS could complete
// between the trigger and the check, making the suite flaky.
@AutoConfigureMockMvc
@TestPropertySource(properties = "deploytrack.simulator.enabled=false")
class DeploymentControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String devToken;
    private long projectId;

    @BeforeEach
    void setUp() throws Exception {
        devToken = registerAndLogin("deployer-" + unique(), "deployer-" + unique() + "@test.local");
        projectId = createProject(devToken, "deploy-target-" + unique());
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void triggeringADeploymentReturns202AndStartsInProgress() throws Exception {
        // 202 Accepted, not 201 Created: the record exists but the work does
        // not. This distinction is the whole point of the async design.
        mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":"2.4.1","environment":"production"}"""))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.completedAt").doesNotExist())
            .andExpect(jsonPath("$.version").value("2.4.1"));
    }

    @Test
    void rejectsUnknownEnvironment() throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":"2.4.1","environment":"not-an-environment"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingVersion() throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":"","environment":"production"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
    }

    @Test
    void rejectsConcurrentDeploymentToTheSameEnvironment() throws Exception {
        trigger(devToken, projectId, "1.0.0", "production");

        mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":"1.0.1","environment":"production"}"""))
            .andExpect(status().isConflict());
    }

    @Test
    void allowsConcurrentDeploymentsToDifferentEnvironments() throws Exception {
        trigger(devToken, projectId, "1.0.0", "production");

        mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":"1.0.0","environment":"staging"}"""))
            .andExpect(status().isAccepted());
    }

    @Test
    void completesADeploymentViaTheStatusCallback() throws Exception {
        long deploymentId = trigger(devToken, projectId, "3.0.0", "staging");

        mockMvc.perform(patch("/api/deployments/" + deploymentId + "/status")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"SUCCESS"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void rejectsChangingAlreadySettledDeployment() throws Exception {
        long deploymentId = trigger(devToken, projectId, "3.0.0", "dev");
        completeDeployment(deploymentId, "FAILED");

        // A retried webhook must not be able to rewrite a settled outcome.
        mockMvc.perform(patch("/api/deployments/" + deploymentId + "/status")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"SUCCESS"}"""))
            .andExpect(status().isConflict());
    }

    @Test
    void filtersDeploymentsByEnvironmentAndStatus() throws Exception {
        trigger(devToken, projectId, "1.0.0", "production");
        long stagingId = trigger(devToken, projectId, "1.0.0", "staging");
        completeDeployment(stagingId, "SUCCESS");

        mockMvc.perform(get("/api/projects/" + projectId + "/deployments")
                .param("environment", "staging")
                .header("Authorization", "Bearer " + devToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].environment").value("staging"));

        mockMvc.perform(get("/api/projects/" + projectId + "/deployments")
                .param("status", "IN_PROGRESS")
                .header("Authorization", "Bearer " + devToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));
    }

    @Test
    void listsDeploymentsNewestFirst() throws Exception {
        long first = trigger(devToken, projectId, "1.0.0", "dev");
        completeDeployment(first, "SUCCESS");
        long second = trigger(devToken, projectId, "2.0.0", "dev");

        mockMvc.perform(get("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + devToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value((int) second));
    }

    @Test
    void returnsNotFoundListingDeploymentsForUnknownProject() throws Exception {
        // Not an empty page -- that would imply the project exists.
        mockMvc.perform(get("/api/projects/999999/deployments")
                .header("Authorization", "Bearer " + devToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void writesLogEntriesAsTheDeploymentProgresses() throws Exception {
        long deploymentId = trigger(devToken, projectId, "1.0.0", "dev");
        completeDeployment(deploymentId, "SUCCESS");

        // A start entry and a completion entry at minimum.
        mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs")
                .header("Authorization", "Bearer " + devToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void projectStatusIsDerivedFromItsLatestDeployment() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + devToken))
            .andExpect(jsonPath("$.status").value("IDLE"))
            .andExpect(jsonPath("$.latestDeployment").doesNotExist());

        long deploymentId = trigger(devToken, projectId, "1.0.0", "production");

        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + devToken))
            .andExpect(jsonPath("$.status").value("DEPLOYING"))
            .andExpect(jsonPath("$.latestDeployment.version").value("1.0.0"));

        completeDeployment(deploymentId, "SUCCESS");

        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + devToken))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        long failing = trigger(devToken, projectId, "1.0.1", "production");
        completeDeployment(failing, "FAILED");

        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + devToken))
            .andExpect(jsonPath("$.status").value("FAILING"));
    }

    private long trigger(String token, long project, String version, String environment) throws Exception {
        String response = mockMvc.perform(post("/api/projects/" + project + "/deployments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"" + version + "\",\"environment\":\"" + environment + "\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void completeDeployment(long deploymentId, String status) throws Exception {
        mockMvc.perform(patch("/api/deployments/" + deploymentId + "/status")
                .header("Authorization", "Bearer " + devToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"))
            .andExpect(status().isOk());
    }

    private long createProject(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"deployment test fixture\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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
